package com.valuescreener.security;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;

/** In-memory store for tests; compute() makes each update atomic. No TTL: the guard's own logic ignores stale state. */
public class InMemoryLoginAttemptStore implements LoginAttemptStore {

    private record Account(int failures, Instant lockedUntil) {
        boolean lockEnded(Instant now) {
            return lockedUntil != null && !lockedUntil.isAfter(now);
        }
    }

    private final Map<String, State> states = new ConcurrentHashMap<>();
    private final Map<String, Account> accounts = new ConcurrentHashMap<>();

    @Override
    public <S extends State> S update(String key, Class<S> type, UnaryOperator<S> change) {
        return type.cast(states.compute(key, (k, old) -> change.apply(type.cast(old))));
    }

    @Override
    public <S extends State> Optional<S> get(String key, Class<S> type) {
        return Optional.ofNullable(states.get(key)).map(type::cast);
    }

    @Override
    public void delete(String key) {
        states.remove(key);
        accounts.remove(key);
    }

    @Override
    public void recordAccountFailure(String key, Instant now, int lockAfter, Duration lockFor) {
        accounts.compute(key, (k, old) -> {
            boolean fresh = old == null || old.lockEnded(now);
            int failures = (fresh ? 0 : old.failures()) + 1;
            Instant lockedUntil = fresh ? null : old.lockedUntil();
            if (failures >= lockAfter) {
                lockedUntil = now.plus(lockFor);
            }
            return new Account(failures, lockedUntil);
        });
    }

    @Override
    public Optional<Instant> accountLockedUntil(String key) {
        return Optional.ofNullable(accounts.get(key)).map(Account::lockedUntil);
    }

    /** Number of stored keys that start with the prefix, e.g. "ACCOUNT#". */
    public int count(String keyPrefix) {
        return (int) java.util.stream.Stream.concat(states.keySet().stream(), accounts.keySet().stream())
                .filter(k -> k.startsWith(keyPrefix)).count();
    }
}
