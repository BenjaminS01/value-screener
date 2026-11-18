package com.valuescreener.research.history;

import com.valuescreener.research.snapshot.GroupSource;
import java.time.Instant;
import java.util.Objects;

/**
 * Append-only: one actual change of a snapshot's group (previous values are null for the first assignment).
 * References the snapshot by ISIN and the groups by key (see {@link GroupHistoryItem}).
 */
public class StockGroupHistory {

    private final String snapshotIsin;
    private final String previousGroupKey;
    private final GroupSource previousSource;
    private final String newGroupKey;
    private final GroupSource newSource;
    private final Instant changedAt;
    private final HistoryOrigin origin;

    /** Pair rule (formerly a CHECK constraint): previous key and previous source are both set or both null. */
    public StockGroupHistory(String snapshotIsin, String previousGroupKey, GroupSource previousSource,
                             String newGroupKey, GroupSource newSource, Instant changedAt, HistoryOrigin origin) {
        if ((previousGroupKey == null) != (previousSource == null)) {
            throw new IllegalArgumentException(
                    "previousGroupKey and previousSource must both be set or both be empty");
        }
        this.snapshotIsin = Objects.requireNonNull(snapshotIsin, "snapshotIsin must not be null");
        this.previousGroupKey = previousGroupKey;
        this.previousSource = previousSource;
        this.newGroupKey = Objects.requireNonNull(newGroupKey, "newGroupKey must not be null");
        this.newSource = Objects.requireNonNull(newSource, "newSource must not be null");
        this.changedAt = Objects.requireNonNull(changedAt, "changedAt must not be null");
        this.origin = Objects.requireNonNull(origin, "origin must not be null");
    }

    public String getSnapshotIsin() { return snapshotIsin; }
    public String getPreviousGroupKey() { return previousGroupKey; }
    public GroupSource getPreviousSource() { return previousSource; }
    public String getNewGroupKey() { return newGroupKey; }
    public GroupSource getNewSource() { return newSource; }
    public Instant getChangedAt() { return changedAt; }
    public HistoryOrigin getOrigin() { return origin; }
}
