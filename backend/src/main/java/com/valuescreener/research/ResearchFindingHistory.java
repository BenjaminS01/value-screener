package com.valuescreener.research;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/** Append-only: the previous values of a finding at the moment it was replaced (see {@link FindingHistoryItem}). */
public class ResearchFindingHistory {

    private final String snapshotIsin;
    private final ResearchCriterion criterionKey;
    private final BigDecimal numericValue;
    private final Boolean booleanValue;
    private final String claim;
    private final String sourceUrl;
    private final LocalDate asOfDate;
    private final Instant replacedAt;
    private final HistoryOrigin origin;

    /** origin must be set (formerly a NOT NULL + CHECK column); history references its snapshot by ISIN. */
    public ResearchFindingHistory(String snapshotIsin, ReplacedFinding replaced, Instant replacedAt,
                                  HistoryOrigin origin) {
        this.snapshotIsin = Objects.requireNonNull(snapshotIsin, "snapshotIsin must not be null");
        Objects.requireNonNull(replaced, "replaced must not be null");
        this.criterionKey = replaced.criterionKey();
        this.numericValue = replaced.numericValue();
        this.booleanValue = replaced.booleanValue();
        this.claim = replaced.claim();
        this.sourceUrl = replaced.sourceUrl();
        this.asOfDate = replaced.asOfDate();
        this.replacedAt = Objects.requireNonNull(replacedAt, "replacedAt must not be null");
        this.origin = Objects.requireNonNull(origin, "origin must not be null");
    }

    public String getSnapshotIsin() { return snapshotIsin; }
    public ResearchCriterion getCriterionKey() { return criterionKey; }
    public BigDecimal getNumericValue() { return numericValue; }
    public Boolean getBooleanValue() { return booleanValue; }
    public String getClaim() { return claim; }
    public String getSourceUrl() { return sourceUrl; }
    public LocalDate getAsOfDate() { return asOfDate; }
    public Instant getReplacedAt() { return replacedAt; }
    public HistoryOrigin getOrigin() { return origin; }
}
