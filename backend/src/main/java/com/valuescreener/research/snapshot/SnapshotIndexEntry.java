package com.valuescreener.research.snapshot;

import java.time.LocalDate;

/**
 * One row of the snapshot index: what the research list shows. Absent values are null (ungrouped, no country, no
 * findings yet, or a v1 index item that has not been upgraded by the backfill).
 */
public record SnapshotIndexEntry(String isin, String ticker, String companyName, String country, String groupKey,
                                 GroupSource groupSource, LocalDate lastResearchedAt) {
}
