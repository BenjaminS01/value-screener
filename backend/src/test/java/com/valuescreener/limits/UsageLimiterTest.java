package com.valuescreener.limits;

import com.valuescreener.limits.LimitReachedException.Scope;
import com.valuescreener.persistence.DynamoProperties;
import com.valuescreener.persistence.DynamoTableInitializer;
import com.valuescreener.persistence.DynamoTestSupport;
import com.valuescreener.MutableClock;
import com.valuescreener.security.auth.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UsageLimiterTest extends DynamoTestSupport {

    private static final JobType JOB = JobType.DEEP_ANALYSIS_DEMO;

    private final DynamoDbClient client = newClient();
    private final MutableClock clock = new MutableClock();
    private DynamoProperties dynamo;
    private UsageLimiter limiter;

    @BeforeEach
    void setUp() {
        // DynamoDB Local really deletes items whose ttl is in the past, so the test clock must run at
        // real time (start of today UTC), not at MutableClock's fixed 2026-01-01.
        Instant startOfToday = LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant();
        clock.advance(Duration.between(clock.instant(), startOfToday));
        // fresh table per test: no shared data
        dynamo = new DynamoProperties(freshTableName(), endpoint(), "eu-central-1", true);
        new DynamoTableInitializer(client, dynamo).run(null);
        limiter = new UsageLimiter(client, dynamo, new LimitsProperties(5, 20, 100), clock);
    }

    private String today() {
        return LocalDate.now(clock).toString();
    }

    private Map<String, AttributeValue> item(String day, String sk) {
        return client.getItem(GetItemRequest.builder().tableName(dynamo.tableName()).consistentRead(true)
                .key(Map.of("pk", AttributeValue.fromS("USAGE#" + day), "sk", AttributeValue.fromS(sk)))
                .build()).item();
    }

    private int count(String day, String sk) {
        var it = item(day, sk);
        return it.isEmpty() ? 0 : Integer.parseInt(it.get("count").n());
    }

    @Test
    void userGetsFivePerDay() {
        for (int i = 0; i < 5; i++) {
            assertThat(limiter.consume("alice", Role.USER, JOB)).isEqualTo(new Quota(4 - i, 5));
        }
        assertThatThrownBy(() -> limiter.consume("alice", Role.USER, JOB))
                .isInstanceOfSatisfying(LimitReachedException.class, e -> assertThat(e.scope()).isEqualTo(Scope.ACCOUNT));
        assertThat(count(today(), "alice#DEEP_ANALYSIS_DEMO")).isEqualTo(5);
    }

    @Test
    void adminGetsTwentyPerDay() {
        for (int i = 0; i < 20; i++) {
            assertThat(limiter.consume("root", Role.ADMIN, JOB)).isEqualTo(new Quota(19 - i, 20));
        }
        assertThatThrownBy(() -> limiter.consume("root", Role.ADMIN, JOB))
                .isInstanceOfSatisfying(LimitReachedException.class, e -> assertThat(e.scope()).isEqualTo(Scope.ACCOUNT));
    }

    @Test
    void globalCapStopsEveryoneAndDoesNotCountTheAccount() {
        UsageLimiter capped = new UsageLimiter(client, dynamo, new LimitsProperties(5, 20, 2), clock);
        capped.consume("alice", Role.USER, JOB);
        capped.consume("bob", Role.USER, JOB);

        assertThatThrownBy(() -> capped.consume("carol", Role.USER, JOB))
                .isInstanceOfSatisfying(LimitReachedException.class, e -> assertThat(e.scope()).isEqualTo(Scope.GLOBAL));

        assertThat(capped.quota("carol", Role.USER, JOB)).isEqualTo(new Quota(5, 5));
        assertThat(count(today(), "carol#DEEP_ANALYSIS_DEMO")).isZero();
        assertThat(count(today(), "*#ALL")).isEqualTo(2);
    }

    @Test
    void limitZeroMeansNoInvocationAndNoWrite() {
        UsageLimiter zero = new UsageLimiter(client, dynamo, new LimitsProperties(0, 0, 100), clock);
        assertThatThrownBy(() -> zero.consume("alice", Role.USER, JOB))
                .isInstanceOfSatisfying(LimitReachedException.class, e -> assertThat(e.scope()).isEqualTo(Scope.ACCOUNT));
        UsageLimiter zeroGlobal = new UsageLimiter(client, dynamo, new LimitsProperties(5, 20, 0), clock);
        assertThatThrownBy(() -> zeroGlobal.consume("alice", Role.USER, JOB))
                .isInstanceOfSatisfying(LimitReachedException.class, e -> assertThat(e.scope()).isEqualTo(Scope.GLOBAL));
        assertThat(client.scan(b -> b.tableName(dynamo.tableName())).count()).isZero();
    }

    @Test
    void accountNameIsCaseInsensitive() {
        limiter.consume("Alice", Role.USER, JOB);
        assertThat(limiter.consume("ALICE", Role.USER, JOB)).isEqualTo(new Quota(3, 5));
        assertThat(count(today(), "alice#DEEP_ANALYSIS_DEMO")).isEqualTo(2);
    }

    @Test
    void quotaWithoutItemIsFullAndReflectsUsage() {
        assertThat(limiter.quota("alice", Role.USER, JOB)).isEqualTo(new Quota(5, 5));
        limiter.consume("alice", Role.USER, JOB);
        assertThat(limiter.quota("alice", Role.USER, JOB)).isEqualTo(new Quota(4, 5));
    }

    @Test
    void newDayStartsAtZeroEvenIfOldItemExists() {
        for (int i = 0; i < 5; i++) {
            limiter.consume("alice", Role.USER, JOB);
        }
        String firstDay = today();
        clock.advance(Duration.ofDays(1));
        assertThat(limiter.consume("alice", Role.USER, JOB)).isEqualTo(new Quota(4, 5));
        assertThat(count(firstDay, "alice#DEEP_ANALYSIS_DEMO")).isEqualTo(5);
    }

    @Test
    void concurrentConsumesNeverExceedLimit() throws Exception {
        for (int i = 0; i < 4; i++) {
            limiter.consume("alice", Role.USER, JOB);
        }
        int threads = 10;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        Callable<Object> task = () -> {
            ready.countDown();
            go.await();
            try {
                return limiter.consume("alice", Role.USER, JOB);
            } catch (LimitReachedException e) {
                return e;
            }
        };
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(task));
            }
            ready.await();
            go.countDown();
            List<Object> results = new ArrayList<>();
            for (Future<Object> f : futures) {
                results.add(f.get());
            }
            assertThat(results.stream().filter(Quota.class::isInstance)).hasSize(1);
            assertThat(results.stream().filter(LimitReachedException.class::isInstance)).hasSize(9)
                    .allSatisfy(e -> assertThat(((LimitReachedException) e).scope()).isEqualTo(Scope.ACCOUNT));
            assertThat(count(today(), "alice#DEEP_ANALYSIS_DEMO")).isEqualTo(5);
            assertThat(count(today(), "*#ALL")).isEqualTo(5);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void itemsCarryTtlTwoDaysAfterDayStart() {
        limiter.consume("alice", Role.USER, JOB);
        long expected = LocalDate.now(clock).plusDays(2).atStartOfDay(ZoneOffset.UTC).toInstant().getEpochSecond();
        assertThat(Long.parseLong(item(today(), "alice#DEEP_ANALYSIS_DEMO").get("ttl").n())).isEqualTo(expected);
        assertThat(Long.parseLong(item(today(), "*#ALL").get("ttl").n())).isEqualTo(expected);
    }
}
