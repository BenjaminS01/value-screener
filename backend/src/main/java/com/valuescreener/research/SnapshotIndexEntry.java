package com.valuescreener.research;

/** One row of the snapshot index: the ISIN and its group (null when ungrouped). */
public record SnapshotIndexEntry(String isin, String groupKey) {
}
