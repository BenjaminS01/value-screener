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

import java.time.Instant;
import java.util.Objects;

/** Insert-only: one actual change of a snapshot's group (previous values are null for the first assignment). */
@Entity
@Table(name = "stock_group_history")
public class StockGroupHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "company_snapshot_id", nullable = false, updatable = false)
    private CompanySnapshot companySnapshot;

    @Column(name = "previous_group_key", length = 40, updatable = false)
    private String previousGroupKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_group_source", length = 10, updatable = false)
    private GroupSource previousSource;

    @Column(name = "new_group_key", nullable = false, length = 40, updatable = false)
    private String newGroupKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_group_source", nullable = false, length = 10, updatable = false)
    private GroupSource newSource;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, updatable = false)
    private HistoryOrigin origin;

    protected StockGroupHistory() {
        // JPA
    }

    public StockGroupHistory(CompanySnapshot companySnapshot, String previousGroupKey, GroupSource previousSource,
                             String newGroupKey, GroupSource newSource, Instant changedAt, HistoryOrigin origin) {
        this.companySnapshot = Objects.requireNonNull(companySnapshot, "companySnapshot must not be null");
        this.previousGroupKey = previousGroupKey;
        this.previousSource = previousSource;
        this.newGroupKey = Objects.requireNonNull(newGroupKey, "newGroupKey must not be null");
        this.newSource = Objects.requireNonNull(newSource, "newSource must not be null");
        this.changedAt = Objects.requireNonNull(changedAt, "changedAt must not be null");
        this.origin = Objects.requireNonNull(origin, "origin must not be null");
    }

    public Long getId() { return id; }
    public String getPreviousGroupKey() { return previousGroupKey; }
    public GroupSource getPreviousSource() { return previousSource; }
    public String getNewGroupKey() { return newGroupKey; }
    public GroupSource getNewSource() { return newSource; }
    public Instant getChangedAt() { return changedAt; }
    public HistoryOrigin getOrigin() { return origin; }
}
