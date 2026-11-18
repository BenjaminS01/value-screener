package com.valuescreener.security.throttle;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

/**
 * Brute-force protection state: a per-IP attempt limit with growing block durations and a per-account
 * lock after consecutive failures. Requests are rejected, never delayed. The state lives in a
 * {@link LoginAttemptStore} shared by all instances; every change is an optimistic read-modify-write there,
 * so concurrent requests cannot lose counts. Expired state is dropped by the store's TTL.
 */
@Component
public class LoginAttemptGuard {

    static final int MAX_ATTEMPTS_PER_WINDOW = 5;
    static final Duration WINDOW = Duration.ofSeconds(60);
    static final Duration MAX_BLOCK = Duration.ofMinutes(15);
    static final Duration VIOLATION_MEMORY = Duration.ofHours(1);
    static final int MAX_FAILURES = 10;
    static final Duration ACCOUNT_LOCK = Duration.ofMinutes(15);

    record IpState(Instant windowStart, int count, int violations, Instant lastViolation,
                   Instant blockedUntil) implements LoginAttemptStore.State {
        boolean expired(Instant now) {
            return !blockedUntil.isAfter(now)
                    && !windowStart.plus(WINDOW).isAfter(now)
                    && (violations == 0 || !lastViolation.plus(VIOLATION_MEMORY).isAfter(now));
        }

        /** Later of block end and window end + violation memory; after that nothing of this state matters. */
        @Override
        public Instant expiresAt() {
            Instant memoryEnd = windowStart.plus(WINDOW).plus(VIOLATION_MEMORY);
            return blockedUntil.isAfter(memoryEnd) ? blockedUntil : memoryEnd;
        }
    }

    private static final String IP_PREFIX = "IP#";
    private static final String ACCOUNT_PREFIX = "ACCOUNT#";

    private final Clock clock;
    private final LoginAttemptStore store;

    public LoginAttemptGuard(Clock clock, LoginAttemptStore store) {
        this.clock = clock;
        this.store = store;
    }

    public Optional<Duration> ipBlockRemaining(String ip) {
        Instant now = clock.instant();
        Optional<IpState> state = store.get(IP_PREFIX + ip, IpState.class);
        if (state.isEmpty() || !state.get().blockedUntil().isAfter(now)) {
            return Optional.empty();
        }
        return Optional.of(Duration.between(now, state.get().blockedUntil()));
    }

    public void recordAttempt(String ip) {
        Instant now = clock.instant();
        store.update(IP_PREFIX + ip, IpState.class, old -> {
            if (old == null) {
                return new IpState(now, 1, 0, Instant.EPOCH, Instant.EPOCH);
            }
            int violations = old.violations();
            if (violations > 0 && !old.lastViolation().plus(VIOLATION_MEMORY).isAfter(now)) {
                violations = 0;
            }
            Instant windowStart = old.windowStart();
            int count = old.count();
            if (!windowStart.plus(WINDOW).isAfter(now)) {
                windowStart = now;
                count = 0;
            }
            count++;
            if (count > MAX_ATTEMPTS_PER_WINDOW) {
                violations++;
                Duration block = blockFor(violations);
                return new IpState(now, 0, violations, now, now.plus(block));
            }
            return new IpState(windowStart, count, violations, old.lastViolation(), old.blockedUntil());
        });
    }

    private static Duration blockFor(int violations) {
        long minutes = 1L << Math.min(violations - 1, 10);
        Duration block = Duration.ofMinutes(minutes);
        return block.compareTo(MAX_BLOCK) > 0 ? MAX_BLOCK : block;
    }

    public boolean isAccountLocked(String normalizedUsername) {
        return store.accountLockedUntil(accountKey(normalizedUsername))
                .filter(lockedUntil -> lockedUntil.isAfter(clock.instant()))
                .isPresent();
    }

    /** Atomic in the store: a wrong password is always counted, however many requests race. */
    public void recordFailure(String normalizedUsername) {
        store.recordAccountFailure(accountKey(normalizedUsername), clock.instant(), MAX_FAILURES, ACCOUNT_LOCK);
    }

    public void recordSuccess(String normalizedUsername) {
        store.delete(accountKey(normalizedUsername));
    }

    private static String accountKey(String normalizedUsername) {
        return ACCOUNT_PREFIX + normalizedUsername.toLowerCase(Locale.ROOT);
    }
}
