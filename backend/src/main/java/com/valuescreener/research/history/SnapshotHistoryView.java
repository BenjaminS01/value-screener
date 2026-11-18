package com.valuescreener.research.history;

import com.valuescreener.research.snapshot.ResearchCriterion;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record SnapshotHistoryView(List<FindingHistoryEntry> findings) {

    public record FindingHistoryEntry(ResearchCriterion criterionKey, BigDecimal numericValue,
                                      Boolean booleanValue, String claim, String sourceUrl, LocalDate asOfDate,
                                      Instant replacedAt, HistoryOrigin origin) {
    }
}
