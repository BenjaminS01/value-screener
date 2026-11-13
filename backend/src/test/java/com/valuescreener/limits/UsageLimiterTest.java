package com.valuescreener.limits;

import com.valuescreener.limits.LimitReachedException.Scope;
import com.valuescreener.security.MutableClock;
import com.valuescreener.security.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({UsageLimiter.class, UsageLimiterTest.ClockTestConfig.class})
@TestPropertySource(properties = {
        "app.limits.deep-analysis-per-user=5",
        "app.limits.deep-analysis-per-admin=20",
        "app.limits.global-per-day=100"})
class UsageLimiterTest {

    @TestConfiguration
    static class ClockTestConfig {
        @Bean
        MutableClock clock() {
            return new MutableClock();
        }
    }

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private UsageLimiter limiter;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private MutableClock clock;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM usage_counter");
    }

    private int count(String account, String jobType) {
        var rows = jdbc.queryForList(
                "SELECT count FROM usage_counter WHERE account = ? AND job_type = ?",
                Integer.class, account, jobType);
        return rows.isEmpty() ? 0 : rows.get(0);
    }

    @Test
    void firstCallReturnsQuotaAfterIncrement() {
        assertThat(limiter.consume("alice", Role.USER, JobType.DEEP_ANALYSIS_DEMO)).isEqualTo(new Quota(4, 5));
    }

    @Test
    void sixthCallHitsAccountLimit() {
        for (int i = 0; i < 5; i++) {
            limiter.consume("alice", Role.USER, JobType.DEEP_ANALYSIS_DEMO);
        }
        assertThatThrownBy(() -> limiter.consume("alice", Role.USER, JobType.DEEP_ANALYSIS_DEMO))
                .isInstanceOfSatisfying(LimitReachedException.class, e -> assertThat(e.scope()).isEqualTo(Scope.ACCOUNT));
        assertThat(count("alice", "DEEP_ANALYSIS_DEMO")).isEqualTo(5);
    }

    @Test
    void adminHasHigherLimit() {
        assertThat(limiter.consume("root", Role.ADMIN, JobType.DEEP_ANALYSIS_DEMO)).isEqualTo(new Quota(19, 20));
    }

    @Test
    void accountNamesAreCaseInsensitive() {
        limiter.consume("Alice", Role.USER, JobType.DEEP_ANALYSIS_DEMO);
        assertThat(limiter.consume("ALICE", Role.USER, JobType.DEEP_ANALYSIS_DEMO)).isEqualTo(new Quota(3, 5));
        assertThat(count("alice", "DEEP_ANALYSIS_DEMO")).isEqualTo(2);
    }

    @Test
    void nextDayStartsAtZero() {
        for (int i = 0; i < 5; i++) {
            limiter.consume("alice", Role.USER, JobType.DEEP_ANALYSIS_DEMO);
        }
        clock.advance(Duration.ofDays(1));
        assertThat(limiter.consume("alice", Role.USER, JobType.DEEP_ANALYSIS_DEMO)).isEqualTo(new Quota(4, 5));
    }

    @Test
    void quotaWithoutRowIsFull() {
        assertThat(limiter.quota("alice", Role.USER, JobType.DEEP_ANALYSIS_DEMO)).isEqualTo(new Quota(5, 5));
    }

    @Test
    void quotaReflectsUsage() {
        limiter.consume("alice", Role.USER, JobType.DEEP_ANALYSIS_DEMO);
        assertThat(limiter.quota("alice", Role.USER, JobType.DEEP_ANALYSIS_DEMO)).isEqualTo(new Quota(4, 5));
    }

    @Test
    void globalCapBlocksAndRollsBackAccountIncrement() {
        jdbc.update("INSERT INTO usage_counter(account, job_type, day, count) VALUES ('*', 'ALL', ?, 100)",
                java.time.LocalDate.now(clock));

        assertThatThrownBy(() -> limiter.consume("alice", Role.USER, JobType.DEEP_ANALYSIS_DEMO))
                .isInstanceOfSatisfying(LimitReachedException.class, e -> assertThat(e.scope()).isEqualTo(Scope.GLOBAL));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM usage_counter WHERE account = 'alice'", Integer.class))
                .isZero();
        assertThat(count("*", "ALL")).isEqualTo(100);
    }

    @Test
    void successfulCallIncrementsGlobalRow() {
        limiter.consume("alice", Role.USER, JobType.DEEP_ANALYSIS_DEMO);
        limiter.consume("bob", Role.USER, JobType.DEEP_ANALYSIS_DEMO);
        assertThat(count("*", "ALL")).isEqualTo(2);
    }

    @Test
    void twoParallelCallsWithOneLeftExactlyOnePasses() throws Exception {
        for (int i = 0; i < 4; i++) {
            limiter.consume("alice", Role.USER, JobType.DEEP_ANALYSIS_DEMO);
        }
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        Callable<Object> task = () -> {
            ready.countDown();
            go.await();
            try {
                return limiter.consume("alice", Role.USER, JobType.DEEP_ANALYSIS_DEMO);
            } catch (LimitReachedException e) {
                return e;
            }
        };
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Object> a = pool.submit(task);
            Future<Object> b = pool.submit(task);
            ready.await();
            go.countDown();
            Object ra = get(a);
            Object rb = get(b);

            long successes = java.util.stream.Stream.of(ra, rb).filter(Quota.class::isInstance).count();
            var failures = java.util.stream.Stream.of(ra, rb).filter(LimitReachedException.class::isInstance)
                    .map(LimitReachedException.class::cast).toList();
            assertThat(successes).isEqualTo(1);
            assertThat(failures).hasSize(1);
            assertThat(failures.get(0).scope()).isEqualTo(Scope.ACCOUNT);
            assertThat(count("alice", "DEEP_ANALYSIS_DEMO")).isEqualTo(5);
            assertThat(count("*", "ALL")).isEqualTo(5);
        } finally {
            pool.shutdownNow();
        }
    }

    private static Object get(Future<Object> f) throws InterruptedException, ExecutionException {
        return f.get();
    }

    @Test
    void zeroLimitBlocksWithoutTouchingRows() {
        UsageLimiter zero = new UsageLimiter(jdbc, new LimitsProperties(0, 0, 100), clock);
        assertThatThrownBy(() -> zero.consume("alice", Role.USER, JobType.DEEP_ANALYSIS_DEMO))
                .isInstanceOfSatisfying(LimitReachedException.class, e -> assertThat(e.scope()).isEqualTo(Scope.ACCOUNT));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM usage_counter", Integer.class)).isZero();
    }
}
