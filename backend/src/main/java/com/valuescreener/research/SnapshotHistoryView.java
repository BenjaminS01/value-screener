package com.valuescreener.research;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record SnapshotHistoryView(List<FindingHistoryEntry> findings, List<GroupHistoryEntry> groups) {

    public record FindingHistoryEntry(ResearchCriterion criterionKey, BigDecimal numericValue,
                                      Boolean booleanValue, String claim, String sourceUrl, LocalDate asOfDate,
                                      Instant replacedAt, HistoryOrigin origin) {
    }

    /** previousGroup and previousSource are null when the snapshot had no group before. */
    public record GroupHistoryEntry(StockGroupRef previousGroup, GroupSource previousSource,
                                    StockGroupRef newGroup, GroupSource newSource, Instant changedAt,
                                    HistoryOrigin origin) {
    }
}
