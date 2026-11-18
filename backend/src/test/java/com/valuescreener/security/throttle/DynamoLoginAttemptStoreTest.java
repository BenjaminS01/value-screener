package com.valuescreener.security.throttle;

import com.valuescreener.persistence.ConcurrentUpdateException;
import com.valuescreener.persistence.DynamoProperties;
import com.valuescreener.persistence.DynamoTableInitializer;
import com.valuescreener.persistence.DynamoTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.valuescreener.MutableClock;
import com.valuescreener.security.session.SerializationSupport;

class DynamoLoginAttemptStoreTest extends DynamoTestSupport {

    record Counter(int n, Instant until) implements LoginAttemptStore.State {
        @Override
        public Instant expiresAt() {
            return until;
        }
    }

    private final DynamoDbClient client = newClient();
    private final MutableClock clock = new MutableClock();
    private DynamoProperties dynamo;
    private DynamoLoginAttemptStore store;

    @BeforeEach
    void setUp() {
        // DynamoDB Local deletes items whose ttl is really in the past: run the app clock at real time.
        clock.advance(Duration.between(clock.instant(), Instant.now()));
        dynamo = new DynamoProperties(freshTableName(), endpoint(), "eu-central-1", true);
        new DynamoTableInitializer(client, dynamo).run(null);
        store = new DynamoLoginAttemptStore(client, dynamo, clock);
    }

    private Map<String, AttributeValue> item(String key) {
        return client.getItem(GetItemRequest.builder().tableName(dynamo.tableName()).consistentRead(true)
                .key(Map.of("pk", AttributeValue.fromS("LOGIN#" + key), "sk", AttributeValue.fromS("STATE")))
                .build()).item();
    }

    @Test
    void updateIsAtomicUnderConcurrency() throws Exception {
        int threads = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger succeeded = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                try {
                    store.update("IP#4.4.4.4", Counter.class, old -> new Counter(old == null ? 1 : old.n() + 1, null));
                    succeeded.incrementAndGet();
                } catch (ConcurrentUpdateException exhausted) {
                    // allowed: the caller gets a 503, nothing was written for this call
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : futures) {
            f.get();
        }
        pool.shutdown();
        // Never lost, never double counted: the stored count equals the number of calls that returned normally.
        assertThat(store.get("IP#4.4.4.4", Counter.class)).get().extracting(Counter::n).isEqualTo(succeeded.get());
        assertThat(succeeded.get()).isGreaterThanOrEqualTo(15);
    }

    @Test
    void accountFailuresAreCountedExactlyUnderConcurrency() throws Exception {
        int threads = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                store.recordAccountFailure("ACCOUNT#admin", clock.instant(), 10, Duration.ofMinutes(15));
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : futures) {
            f.get(); // no ConcurrentUpdateException possible on this path
        }
        pool.shutdown();
        assertThat(item("ACCOUNT#admin").get("failures").n()).isEqualTo("20");
        assertThat(store.accountLockedUntil("ACCOUNT#admin")).isPresent();
    }

    @Test
    void guardOnDynamoCountsParallelFailuresExactlyAndKeepsLockSemantics() throws Exception {
        LoginAttemptGuard guard = new LoginAttemptGuard(clock, store);
        ExecutorService pool = Executors.newFixedThreadPool(9);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                guard.recordFailure("alice");
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : futures) {
            f.get();
        }
        pool.shutdown();
        assertThat(guard.isAccountLocked("alice")).isFalse();
        guard.recordFailure("alice");
        assertThat(guard.isAccountLocked("alice")).isTrue();
        clock.advance(Duration.ofMinutes(14));
        assertThat(guard.isAccountLocked("alice")).isTrue();
        clock.advance(Duration.ofMinutes(1));
        assertThat(guard.isAccountLocked("alice")).isFalse();
        // after the lock the count starts fresh: nine more failures do not lock again
        for (int i = 0; i < 9; i++) {
            guard.recordFailure("alice");
        }
        assertThat(guard.isAccountLocked("alice")).isFalse();
        guard.recordFailure("alice");
        assertThat(guard.isAccountLocked("alice")).isTrue();
        // success resets everything
        guard.recordSuccess("alice");
        assertThat(guard.isAccountLocked("alice")).isFalse();
        assertThat(item("ACCOUNT#alice")).isEmpty();
    }

    @Test
    void accountFailuresWithoutLockHaveNoTtlAndLockSetsTtlToLockEnd() {
        for (int i = 0; i < 9; i++) {
            store.recordAccountFailure("ACCOUNT#bob", clock.instant(), 10, Duration.ofMinutes(15));
        }
        assertThat(item("ACCOUNT#bob")).doesNotContainKey("ttl");
        store.recordAccountFailure("ACCOUNT#bob", clock.instant(), 10, Duration.ofMinutes(15));
        assertThat(item("ACCOUNT#bob").get("ttl").n())
                .isEqualTo(Long.toString(clock.instant().plus(Duration.ofMinutes(15)).getEpochSecond()));
    }

    @Test
    void storesStateWithKeyVersionAndTtl() {
        Instant until = clock.instant().plusSeconds(600);
        store.update("ACCOUNT#alice", Counter.class, old -> new Counter(1, until));
        store.update("ACCOUNT#alice", Counter.class, old -> new Counter(old.n() + 1, until));
        Map<String, AttributeValue> item = item("ACCOUNT#alice");
        assertThat(item.get("version").n()).isEqualTo("2");
        assertThat(item.get("ttl").n()).isEqualTo(Long.toString(until.getEpochSecond()));
        assertThat(item.get("state").b()).isNotNull();
    }

    @Test
    void stateWithoutExpiryHasNoTtlAttribute() {
        store.update("ACCOUNT#alice", Counter.class, old -> new Counter(1, null));
        assertThat(item("ACCOUNT#alice")).doesNotContainKey("ttl");
        assertThat(store.get("ACCOUNT#alice", Counter.class)).isPresent();
    }

    @Test
    void expiredBlockIsIgnoredEvenIfItemStillExists() {
        store.update("IP#2.2.2.2", Counter.class, old -> new Counter(5, clock.instant().plusSeconds(60)));
        clock.advance(Duration.ofSeconds(61));
        // the stored ttl is still in the real future, so DynamoDB Local keeps the item
        assertThat(item("IP#2.2.2.2")).isNotEmpty();
        assertThat(store.get("IP#2.2.2.2", Counter.class)).isEmpty();
        // update sees "no state" and starts over
        Counter next = store.update("IP#2.2.2.2", Counter.class, old -> new Counter(old == null ? 1 : 99, null));
        assertThat(next.n()).isEqualTo(1);
    }

    @Test
    void deleteRemovesState() {
        store.update("ACCOUNT#alice", Counter.class, old -> new Counter(1, null));
        store.delete("ACCOUNT#alice");
        assertThat(store.get("ACCOUNT#alice", Counter.class)).isEmpty();
        store.delete("ACCOUNT#alice"); // deleting nothing is fine
    }

    @Test
    void retriesAtMostFiveTimesThenThrowsConcurrentUpdate() {
        AtomicInteger calls = new AtomicInteger();
        store.update("IP#3.3.3.3", Counter.class, old -> new Counter(1, null));
        assertThatThrownBy(() -> store.update("IP#3.3.3.3", Counter.class, old -> {
            calls.incrementAndGet();
            // a competing writer wins every round
            client.putItem(b -> b.tableName(dynamo.tableName()).item(Map.of(
                    "pk", AttributeValue.fromS("LOGIN#IP#3.3.3.3"), "sk", AttributeValue.fromS("STATE"),
                    "version", AttributeValue.fromN(Integer.toString(100 + calls.get())),
                    "state", AttributeValue.fromB(software.amazon.awssdk.core.SdkBytes.fromByteArray(
                            SerializationSupport.serialize(new Counter(7, null)))))));
            return new Counter(old.n() + 1, null);
        })).isInstanceOf(ConcurrentUpdateException.class);
        assertThat(calls.get()).isEqualTo(5);
    }
}
