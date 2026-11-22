package com.valuescreener.security.session;

import com.valuescreener.persistence.DynamoProperties;
import com.valuescreener.persistence.DynamoTableInitializer;
import com.valuescreener.persistence.DynamoTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.session.MapSession;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.CancellationReason;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;
import software.amazon.awssdk.services.dynamodb.model.TransactionConflictException;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.valuescreener.MutableClock;

class DynamoSessionRepositoryTest extends DynamoTestSupport {

    private static final Duration MAX_INACTIVE = Duration.ofHours(2);
    private static final Duration REFRESH = Duration.ofMinutes(10);

    private final DynamoDbClient client = newClient();
    private final MutableClock clock = new MutableClock();
    private DynamoProperties dynamo;
    private DynamoSessionRepository repoA;
    private DynamoSessionRepository repoB;

    @BeforeEach
    void setUp() {
        // DynamoDB Local deletes items whose ttl is really in the past: run the app clock at real time.
        clock.advance(Duration.between(clock.instant(), Instant.now()));
        dynamo = new DynamoProperties(freshTableName(), endpoint(), "eu-central-1", true);
        new DynamoTableInitializer(client, dynamo).run(null);
        repoA = new DynamoSessionRepository(client, dynamo, clock, MAX_INACTIVE, REFRESH);
        repoB = new DynamoSessionRepository(client, dynamo, clock, MAX_INACTIVE, REFRESH);
    }

    private Map<String, AttributeValue> item(String id) {
        return client.getItem(GetItemRequest.builder().tableName(dynamo.tableName()).consistentRead(true)
                .key(Map.of("pk", AttributeValue.fromS("SESSION#" + id), "sk", AttributeValue.fromS("SESSION")))
                .build()).item();
    }

    private DynamoSession savedSession() {
        DynamoSession session = repoA.createSession();
        session.setAttribute("k", "v");
        repoA.save(session);
        return session;
    }

    /** What a request does: load, touch, save. */
    private DynamoSession touch(DynamoSessionRepository repo, String id) {
        DynamoSession session = repo.findById(id);
        session.setLastAccessedTime(clock.instant());
        repo.save(session);
        return session;
    }

    @Test
    void sessionIsVisibleToAnotherRepositoryInstance() {
        DynamoSession session = savedSession();
        DynamoSession found = repoB.findById(session.getId());
        assertThat(found).isNotNull();
        assertThat(found.getId()).isEqualTo(session.getId());
        assertThat(found.<String>getAttribute("k")).isEqualTo("v");
        assertThat(found.getMaxInactiveInterval()).isEqualTo(MAX_INACTIVE);
        assertThat(found.getCreationTime().toEpochMilli()).isEqualTo(session.getCreationTime().toEpochMilli());
    }

    @Test
    void itemLayout() {
        DynamoSession session = savedSession();
        Map<String, AttributeValue> item = item(session.getId());
        assertThat(item).containsKeys("created", "lastAccessed", "maxInactiveSeconds", "attrs", "ttl");
        assertThat(item.get("maxInactiveSeconds").n()).isEqualTo("7200");
        assertThat(item.get("attrs").b()).isNotNull();
        assertThat(Long.parseLong(item.get("ttl").n()))
                .isEqualTo(session.getLastAccessedTime().plus(MAX_INACTIVE).getEpochSecond());
    }

    @Test
    void expiredSessionIsNotFound() {
        DynamoSession session = savedSession();
        assertThat(repoB.findById(session.getId())).isNotNull();
        clock.advance(MAX_INACTIVE.plusSeconds(1));
        assertThat(repoB.findById(session.getId())).isNull();
        // the item is still stored (its real ttl is in the future); the read itself must filter
        assertThat(item(session.getId())).isNotEmpty();
    }

    @Test
    void logoutInvalidatesSessionEverywhere() {
        DynamoSession session = savedSession();
        assertThat(repoB.findById(session.getId())).isNotNull();
        repoA.deleteById(session.getId());
        assertThat(repoB.findById(session.getId())).isNull();
        assertThat(item(session.getId())).isEmpty();
    }

    @Test
    void changedIdMovesSession() {
        DynamoSession session = savedSession();
        String oldId = session.getId();
        DynamoSession loaded = repoB.findById(oldId);
        String newId = loaded.changeSessionId();
        repoB.save(loaded);
        assertThat(newId).isNotEqualTo(oldId);
        assertThat(repoA.findById(oldId)).isNull();
        assertThat(item(oldId)).isEmpty();
        DynamoSession moved = repoA.findById(newId);
        assertThat(moved).isNotNull();
        assertThat(moved.<String>getAttribute("k")).isEqualTo("v");
    }

    @Test
    void newSessionWithChangedIdBeforeFirstSaveIsStoredOnlyUnderNewId() {
        DynamoSession session = repoA.createSession();
        String first = session.getId();
        String second = session.changeSessionId();
        repoA.save(session);
        assertThat(repoB.findById(first)).isNull();
        assertThat(repoB.findById(second)).isNotNull();
    }

    @Test
    void accessWithinTenMinutesDoesNotWrite() {
        DynamoSession session = savedSession();
        Map<String, AttributeValue> before = item(session.getId());
        clock.advance(REFRESH.minusSeconds(1));
        touch(repoB, session.getId());
        // identical item, including lastAccessed and ttl: nothing was written
        assertThat(item(session.getId())).isEqualTo(before);
    }

    @Test
    void accessWithinTenMinutesWithUnchangedSecurityContextDoesNotWrite() {
        DynamoSession session = repoA.createSession();
        session.setAttribute("SPRING_SECURITY_CONTEXT", context());
        repoA.save(session);
        Map<String, AttributeValue> before = item(session.getId());
        clock.advance(Duration.ofMinutes(5));
        DynamoSession loaded = repoB.findById(session.getId());
        loaded.setLastAccessedTime(clock.instant());
        // what Spring Security does on every request: set the same context again
        loaded.setAttribute("SPRING_SECURITY_CONTEXT", loaded.getAttribute("SPRING_SECURITY_CONTEXT"));
        repoB.save(loaded);
        assertThat(item(session.getId())).isEqualTo(before);
    }

    @Test
    void changedAttributeIsWrittenImmediately() {
        DynamoSession session = savedSession();
        clock.advance(Duration.ofMinutes(1));
        DynamoSession loaded = repoB.findById(session.getId());
        loaded.setLastAccessedTime(clock.instant());
        loaded.setAttribute("k", "changed");
        repoB.save(loaded);
        DynamoSession again = repoA.findById(session.getId());
        assertThat(again.<String>getAttribute("k")).isEqualTo("changed");
        assertThat(Long.parseLong(item(session.getId()).get("lastAccessed").n()))
                .isEqualTo(clock.instant().toEpochMilli());
    }

    @Test
    void removedAttributeIsWrittenImmediately() {
        DynamoSession session = savedSession();
        DynamoSession loaded = repoB.findById(session.getId());
        loaded.removeAttribute("k");
        repoB.save(loaded);
        assertThat(repoA.findById(session.getId()).getAttributeNames()).isEmpty();
    }

    @Test
    void accessAfterTenMinutesRefreshesExpiry() {
        DynamoSession session = savedSession();
        clock.advance(REFRESH);
        touch(repoB, session.getId());
        Map<String, AttributeValue> after = item(session.getId());
        assertThat(Long.parseLong(after.get("lastAccessed").n())).isEqualTo(clock.instant().toEpochMilli());
        assertThat(Long.parseLong(after.get("ttl").n()))
                .isEqualTo(clock.instant().plus(MAX_INACTIVE).getEpochSecond());
        // the session survives past the original expiry
        clock.advance(MAX_INACTIVE.minusMinutes(1));
        assertThat(repoA.findById(session.getId())).isNotNull();
    }

    @Test
    void securityContextSurvivesRoundTrip() {
        DynamoSession session = repoA.createSession();
        session.setAttribute("SPRING_SECURITY_CONTEXT", context());
        repoA.save(session);
        SecurityContext read = repoB.findById(session.getId()).getAttribute("SPRING_SECURITY_CONTEXT");
        assertThat(read.getAuthentication().getName()).isEqualTo("alice");
        assertThat(read.getAuthentication().getAuthorities())
                .extracting(Object::toString).containsExactly("ROLE_USER");
        assertThat(read.getAuthentication().isAuthenticated()).isTrue();
    }

    @Test
    void itemWithClassesOutsideTheAllowListIsRejected() throws Exception {
        DynamoSession session = savedSession();
        HashMap<String, Object> evil = new HashMap<>();
        evil.put("x", new javax.naming.Reference("not.allowed"));
        client.putItem(b -> b.tableName(dynamo.tableName()).item(Map.of(
                "pk", AttributeValue.fromS("SESSION#evil"), "sk", AttributeValue.fromS("SESSION"),
                "created", AttributeValue.fromN(Long.toString(clock.millis())),
                "lastAccessed", AttributeValue.fromN(Long.toString(clock.millis())),
                "maxInactiveSeconds", AttributeValue.fromN("7200"),
                "attrs", AttributeValue.fromB(SdkBytes.fromByteArray(SerializationSupport.serialize(evil))))));
        assertThat(repoA.findById("evil")).isNull();
        assertThat(repoA.findById(session.getId())).isNotNull();
    }

    @Test
    void saveAfterConcurrentLogoutDoesNotReviveSessionWhenRefreshIsDue() {
        DynamoSession session = savedSession();
        DynamoSession loaded = repoB.findById(session.getId());
        repoA.deleteById(session.getId()); // logout on another instance
        clock.advance(REFRESH);
        loaded.setLastAccessedTime(clock.instant());
        repoB.save(loaded);
        assertThat(item(session.getId())).isEmpty();
        assertThat(repoA.findById(session.getId())).isNull();
    }

    @Test
    void saveAfterConcurrentLogoutDoesNotReviveSessionWhenAttributeChanged() {
        DynamoSession session = savedSession();
        DynamoSession loaded = repoB.findById(session.getId());
        repoA.deleteById(session.getId());
        loaded.setAttribute("k", "changed");
        repoB.save(loaded);
        assertThat(item(session.getId())).isEmpty();
    }

    @Test
    void laterSavesOfTheSameObjectAfterADroppedWriteStillDoNotReviveIt() {
        DynamoSession session = savedSession();
        DynamoSession loaded = repoB.findById(session.getId());
        repoA.deleteById(session.getId());
        loaded.setAttribute("k", "first");
        repoB.save(loaded); // dropped
        loaded.setAttribute("k", "second");
        repoB.save(loaded);
        loaded.changeSessionId();
        repoB.save(loaded);
        assertThat(item(session.getId())).isEmpty();
        assertThat(item(loaded.getId())).isEmpty();
    }

    @Test
    void changeSessionIdAfterConcurrentLogoutRecreatesNothing() {
        DynamoSession session = savedSession();
        DynamoSession loaded = repoB.findById(session.getId());
        repoA.deleteById(session.getId());
        String newId = loaded.changeSessionId();
        repoB.save(loaded);
        assertThat(item(session.getId())).isEmpty();
        assertThat(item(newId)).isEmpty();
    }

    @Test
    void unknownIdIsNull() {
        assertThat(repoA.findById("nope")).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"created", "lastAccessed", "maxInactiveSeconds"})
    void itemWithoutATimestampAttributeIsTreatedAsMissing(String missing) {
        DynamoSession session = savedSession();
        Map<String, AttributeValue> damaged = new HashMap<>(item(session.getId()));
        damaged.remove(missing);
        client.putItem(b -> b.tableName(dynamo.tableName()).item(damaged));
        assertThat(repoA.findById(session.getId())).isNull();
    }

    /** The same session as {@code original}, but as a fresh object that was never stored. */
    private DynamoSession unstoredCopy(DynamoSession original, Instant created) {
        MapSession map = new MapSession(original.getId());
        map.setCreationTime(created);
        map.setLastAccessedTime(original.getLastAccessedTime());
        map.setMaxInactiveInterval(MAX_INACTIVE);
        DynamoSession copy = new DynamoSession(map);
        copy.setAttribute("k", "v");
        return copy;
    }

    @Test
    void newSessionWhoseFirstPutAlreadyArrivedCountsAsSaved() {
        // An SDK retry after a lost response finds its own item: same id and same creation time.
        DynamoSession session = savedSession();
        DynamoSession retry = unstoredCopy(session, session.getCreationTime());
        repoB.save(retry);
        assertThat(retry.isStored()).isTrue();
        assertThat(repoA.findById(session.getId())).isNotNull();
    }

    @Test
    void newSessionNeverOverwritesAForeignItemWithTheSameId() {
        DynamoSession session = savedSession();
        Map<String, AttributeValue> before = item(session.getId());
        DynamoSession intruder = unstoredCopy(session, session.getCreationTime().minusSeconds(60));
        intruder.setAttribute("k", "intruder");
        assertThatThrownBy(() -> repoB.save(intruder)).isInstanceOf(IllegalStateException.class);
        assertThat(item(session.getId())).isEqualTo(before);
    }

    /** A stored session for a repository on a mocked client (no DynamoDB call needed to set it up). */
    private static DynamoSession storedSession(String id, Instant at) {
        MapSession map = new MapSession(id);
        map.setCreationTime(at);
        map.setLastAccessedTime(at);
        map.setMaxInactiveInterval(MAX_INACTIVE);
        DynamoSession session = new DynamoSession(map);
        session.storedId = id;
        session.storedLastAccessed = at;
        session.storedAttributes = new byte[0];
        return session;
    }

    @Test
    void idChangeThatCollidesWithAnInFlightTransactionIsSkipped() {
        DynamoDbClient conflicting = mock(DynamoDbClient.class);
        when(conflicting.transactWriteItems(any(TransactWriteItemsRequest.class)))
                .thenThrow(TransactionCanceledException.builder().cancellationReasons(
                        CancellationReason.builder().code("TransactionConflict").build(),
                        CancellationReason.builder().code("None").build()).build());
        DynamoSessionRepository repo = new DynamoSessionRepository(conflicting, dynamo, clock, MAX_INACTIVE, REFRESH);
        DynamoSession session = storedSession("old-id", clock.instant());
        session.changeSessionId();

        repo.save(session);

        assertThat(session.storedId).isEqualTo("old-id");
    }

    @Test
    void refreshThatCollidesWithAnInFlightTransactionIsSkipped() {
        DynamoDbClient conflicting = mock(DynamoDbClient.class);
        when(conflicting.putItem(any(PutItemRequest.class)))
                .thenThrow(TransactionConflictException.builder().message("conflict").build());
        DynamoSessionRepository repo = new DynamoSessionRepository(conflicting, dynamo, clock, MAX_INACTIVE, REFRESH);
        Instant loaded = clock.instant();
        DynamoSession session = storedSession("some-id", loaded);
        clock.advance(REFRESH);
        session.setLastAccessedTime(clock.instant());

        repo.save(session);

        assertThat(session.storedLastAccessed).isEqualTo(loaded);
    }

    private static SecurityContext context() {
        SecurityContext context = new SecurityContextImpl();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "alice", null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        return context;
    }
}
