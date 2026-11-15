package com.valuescreener.persistence;

import java.time.Instant;

/**
 * DynamoDB TTL helpers. Real DynamoDB deletes expired items lazily (up to ~48 h after expiry) and
 * DynamoDB Local's timing differs, so reads must filter expired items themselves.
 */
public final class Ttl {

    private Ttl() {
    }

    public static long epochSeconds(Instant instant) {
        return instant.getEpochSecond();
    }

    public static boolean isExpired(Long ttl, Instant now) {
        return ttl != null && ttl <= now.getEpochSecond();
    }
}
