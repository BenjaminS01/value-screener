package com.valuescreener.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class LoginAttemptGuardTest {

    private MutableClock clock;
    private LoginAttemptGuard guard;

    @BeforeEach
    void setUp() {
        clock = new MutableClock();
        guard = new LoginAttemptGuard(clock);
    }

    private void attempts(String ip, int n) {
        for (int i = 0; i < n; i++) {
            guard.recordAttempt(ip);
        }
    }

    @Test
    void allowsFiveAttemptsPerMinuteThenBlocksOneMinute() {
        attempts("1.1.1.1", 5);
        assertThat(guard.ipBlockRemaining("1.1.1.1")).isEmpty();
        guard.recordAttempt("1.1.1.1");
        assertThat(guard.ipBlockRemaining("1.1.1.1")).contains(Duration.ofMinutes(1));
        assertThat(guard.ipBlockRemaining("2.2.2.2")).isEmpty();
        clock.advance(Duration.ofSeconds(59));
        assertThat(guard.ipBlockRemaining("1.1.1.1")).contains(Duration.ofSeconds(1));
        clock.advance(Duration.ofSeconds(1));
        assertThat(guard.ipBlockRemaining("1.1.1.1")).isEmpty();
    }

    @Test
    void attemptsSpreadOverMoreThanAMinuteAreNotBlocked() {
        for (int i = 0; i < 20; i++) {
            attempts("1.1.1.1", 3);
            clock.advance(Duration.ofSeconds(61));
            assertThat(guard.ipBlockRemaining("1.1.1.1")).isEmpty();
        }
    }

    @Test
    void blockGrowsOneTwoFourEightThenCapsAtFifteenMinutes() {
        long[] expectedMinutes = {1, 2, 4, 8, 15, 15};
        for (long expected : expectedMinutes) {
            attempts("1.1.1.1", 6);
            assertThat(guard.ipBlockRemaining("1.1.1.1")).contains(Duration.ofMinutes(expected));
            clock.advance(Duration.ofMinutes(expected));
            assertThat(guard.ipBlockRemaining("1.1.1.1")).isEmpty();
        }
    }

    @Test
    void violationCounterResetsAfterOneQuietHour() {
        attempts("1.1.1.1", 6);
        clock.advance(Duration.ofMinutes(1));
        attempts("1.1.1.1", 6);
        assertThat(guard.ipBlockRemaining("1.1.1.1")).contains(Duration.ofMinutes(2));
        clock.advance(Duration.ofMinutes(2));
        clock.advance(Duration.ofHours(1));
        attempts("1.1.1.1", 6);
        assertThat(guard.ipBlockRemaining("1.1.1.1")).contains(Duration.ofMinutes(1));
    }

    @Test
    void locksAccountAfterTenConsecutiveFailuresForFifteenMinutes() {
        for (int i = 0; i < 9; i++) {
            guard.recordFailure("admin");
        }
        assertThat(guard.isAccountLocked("admin")).isFalse();
        guard.recordFailure("admin");
        assertThat(guard.isAccountLocked("admin")).isTrue();
        assertThat(guard.isAccountLocked("alice")).isFalse();
        clock.advance(Duration.ofMinutes(14));
        assertThat(guard.isAccountLocked("admin")).isTrue();
    }

    @Test
    void successResetsFailureCount() {
        for (int i = 0; i < 9; i++) {
            guard.recordFailure("admin");
        }
        guard.recordSuccess("admin");
        for (int i = 0; i < 9; i++) {
            guard.recordFailure("admin");
        }
        assertThat(guard.isAccountLocked("admin")).isFalse();
    }

    @Test
    void lockExpiresAfterFifteenMinutes() {
        for (int i = 0; i < 10; i++) {
            guard.recordFailure("admin");
        }
        clock.advance(Duration.ofMinutes(15));
        assertThat(guard.isAccountLocked("admin")).isFalse();
        // The counter starts fresh after the lock: nine more failures do not lock again.
        for (int i = 0; i < 9; i++) {
            guard.recordFailure("admin");
        }
        assertThat(guard.isAccountLocked("admin")).isFalse();
    }

    @Test
    void parallelFailuresAreCountedExactly() throws Exception {
        int threads = 20;
        // 20 parallel failures must all be counted (10 are enough to lock); the exact count is checked below.
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                start.await();
                guard.recordFailure("admin");
                return null;
            });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        assertThat(guard.isAccountLocked("admin")).isTrue();

        // Exact count: 9 parallel failures must not lock, the 10th must.
        LoginAttemptGuard g2 = new LoginAttemptGuard(clock);
        ExecutorService pool2 = Executors.newFixedThreadPool(9);
        CountDownLatch start2 = new CountDownLatch(1);
        for (int i = 0; i < 9; i++) {
            pool2.submit(() -> {
                start2.await();
                g2.recordFailure("alice");
                return null;
            });
        }
        start2.countDown();
        pool2.shutdown();
        assertThat(pool2.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        assertThat(g2.isAccountLocked("alice")).isFalse();
        g2.recordFailure("alice");
        assertThat(g2.isAccountLocked("alice")).isTrue();
    }

    @Test
    void purgesExpiredEntriesAboveTenThousand() {
        for (int i = 0; i < 10_001; i++) {
            guard.recordAttempt("ip-" + i);
        }
        assertThat(guard.trackedIpCount()).isEqualTo(10_001);
        clock.advance(Duration.ofHours(2));
        guard.recordAttempt("fresh");
        assertThat(guard.trackedIpCount()).isEqualTo(1);
    }

    @Test
    void doesNotPurgeUnexpiredEntriesAboveTenThousand() {
        for (int i = 0; i < 10_002; i++) {
            guard.recordAttempt("ip-" + i);
        }
        assertThat(guard.trackedIpCount()).isEqualTo(10_002);
    }
}
