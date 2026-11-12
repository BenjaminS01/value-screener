package com.valuescreener.research;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/** Insert-only: the previous values of a finding at the moment it was replaced. */
@Entity
@Table(name = "research_finding_history")
public class ResearchFindingHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "company_snapshot_id", nullable = false, updatable = false)
    private CompanySnapshot companySnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "criterion_key", nullable = false, length = 40, updatable = false)
    private ResearchCriterion criterionKey;

    @Column(name = "numeric_value", precision = 12, scale = 4, updatable = false)
    private BigDecimal numericValue;

    @Column(name = "boolean_value", updatable = false)
    private Boolean booleanValue;

    @Column(nullable = false, columnDefinition = "TEXT", updatable = false)
    private String claim;

    @Column(name = "source_url", length = 1000, updatable = false)
    private String sourceUrl;

    @Column(name = "as_of_date", nullable = false, updatable = false)
    private LocalDate asOfDate;

    @Column(name = "replaced_at", nullable = false, updatable = false)
    private Instant replacedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, updatable = false)
    private HistoryOrigin origin;

    protected ResearchFindingHistory() {
        // JPA
    }

    public ResearchFindingHistory(CompanySnapshot companySnapshot, ReplacedFinding replaced, Instant replacedAt,
                                  HistoryOrigin origin) {
        this.companySnapshot = Objects.requireNonNull(companySnapshot, "companySnapshot must not be null");
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

    public Long getId() { return id; }
    public ResearchCriterion getCriterionKey() { return criterionKey; }
    public BigDecimal getNumericValue() { return numericValue; }
    public Boolean getBooleanValue() { return booleanValue; }
    public String getClaim() { return claim; }
    public String getSourceUrl() { return sourceUrl; }
    public LocalDate getAsOfDate() { return asOfDate; }
    public Instant getReplacedAt() { return replacedAt; }
    public HistoryOrigin getOrigin() { return origin; }
}
