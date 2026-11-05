package com.valuescreener.security;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Brute-force protection state: a per-IP attempt limit with growing block durations and a per-account
 * lock after consecutive failures. Requests are rejected, never delayed. Every update goes through
 * {@link ConcurrentHashMap#compute}, so concurrent requests cannot lose counts.
 */
@Component
public class LoginAttemptGuard {

    static final int MAX_ATTEMPTS_PER_WINDOW = 5;
    static final Duration WINDOW = Duration.ofSeconds(60);
    static final Duration MAX_BLOCK = Duration.ofMinutes(15);
    static final Duration VIOLATION_MEMORY = Duration.ofHours(1);
    static final int MAX_FAILURES = 10;
    static final Duration ACCOUNT_LOCK = Duration.ofMinutes(15);
    static final int PURGE_THRESHOLD = 10_000;

    private record IpState(Instant windowStart, int count, int violations, Instant lastViolation,
                           Instant blockedUntil) {
        boolean expired(Instant now) {
            return !blockedUntil.isAfter(now)
                    && !windowStart.plus(WINDOW).isAfter(now)
                    && (violations == 0 || !lastViolation.plus(VIOLATION_MEMORY).isAfter(now));
        }
    }

    private record AccountState(int failures, Instant lockedUntil) {
        boolean expired(Instant now) {
            return lockedUntil != null && !lockedUntil.isAfter(now);
        }
    }

    private final Clock clock;
    private final Map<String, IpState> ips = new ConcurrentHashMap<>();
    private final Map<String, AccountState> accounts = new ConcurrentHashMap<>();

    public LoginAttemptGuard(Clock clock) {
        this.clock = clock;
    }

    public Optional<Duration> ipBlockRemaining(String ip) {
        Instant now = clock.instant();
        IpState state = ips.get(ip);
        if (state == null || !state.blockedUntil().isAfter(now)) {
            return Optional.empty();
        }
        return Optional.of(Duration.between(now, state.blockedUntil()));
    }

    public void recordAttempt(String ip) {
        Instant now = clock.instant();
        ips.compute(ip, (k, old) -> {
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
        purgeIfLarge(ips, now);
    }

    private static Duration blockFor(int violations) {
        long minutes = 1L << Math.min(violations - 1, 10);
        Duration block = Duration.ofMinutes(minutes);
        return block.compareTo(MAX_BLOCK) > 0 ? MAX_BLOCK : block;
    }

    public boolean isAccountLocked(String normalizedUsername) {
        AccountState state = accounts.get(normalizedUsername);
        return state != null && state.lockedUntil() != null && state.lockedUntil().isAfter(clock.instant());
    }

    public void recordFailure(String normalizedUsername) {
        Instant now = clock.instant();
        accounts.compute(normalizedUsername, (k, old) -> {
            int failures = (old == null || old.expired(now)) ? 0 : old.failures();
            Instant lockedUntil = (old == null || old.expired(now)) ? null : old.lockedUntil();
            failures++;
            if (failures >= MAX_FAILURES) {
                lockedUntil = now.plus(ACCOUNT_LOCK);
            }
            return new AccountState(failures, lockedUntil);
        });
        purgeIfLarge(accounts, now);
    }

    public void recordSuccess(String normalizedUsername) {
        accounts.remove(normalizedUsername);
    }

    int trackedAccountCount() {
        return accounts.size();
    }

    int trackedIpCount() {
        return ips.size();
    }

    private static <V> void purgeIfLarge(Map<String, V> map, Instant now) {
        if (map.size() > PURGE_THRESHOLD) {
            map.entrySet().removeIf(e -> isExpired(e.getValue(), now));
        }
    }

    private static boolean isExpired(Object state, Instant now) {
        if (state instanceof IpState ip) {
            return ip.expired(now);
        }
        return state instanceof AccountState account && account.expired(now);
    }
}
