package com.valuescreener.security.throttle;

import java.io.Serializable;
import java.time.Instant;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * Shared state of the login throttle. With several Lambda instances the counters cannot live in
 * memory, so every state change is a read, a pure change function and a conditional write.
 */
public interface LoginAttemptStore {

    /** A stored state; it tells the store how long it must be kept. */
    interface State extends Serializable {
        /** Moment after which the state has no effect any more, or null when it never expires. */
        Instant expiresAt();
    }

    /**
     * Applies {@code change} to the current state (null when there is none or it expired) and stores
     * the result. Concurrent writers are handled by retrying; {@code change} may therefore run several
     * times and must have no side effects.
     *
     * @throws com.valuescreener.persistence.ConcurrentUpdateException when the retries are exhausted
     */
    <S extends State> S update(String key, Class<S> type, UnaryOperator<S> change);

    <S extends State> Optional<S> get(String key, Class<S> type);

    /** Removes the state under the key, including account failure counters. */
    void delete(String key);

    /**
     * Counts one failed login for an account, atomically (no read-modify-write, so it cannot fail under
     * contention). A lock that has ended starts a fresh count; the failure that reaches {@code lockAfter}
     * (and any later one) sets the lock to {@code now + lockFor}. Failures without a lock never expire.
     */
    void recordAccountFailure(String key, Instant now, int lockAfter, java.time.Duration lockFor);

    /** End of the account's lock, empty when it has none (or it expired in the store). */
    Optional<Instant> accountLockedUntil(String key);
}
