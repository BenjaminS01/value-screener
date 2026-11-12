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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "previous_group_key", updatable = false)
    private StockGroup previousGroup;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_group_source", length = 10, updatable = false)
    private GroupSource previousSource;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "new_group_key", nullable = false, updatable = false)
    private StockGroup newGroup;

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

    public StockGroupHistory(CompanySnapshot companySnapshot, StockGroup previousGroup, GroupSource previousSource,
                             StockGroup newGroup, GroupSource newSource, Instant changedAt, HistoryOrigin origin) {
        this.companySnapshot = Objects.requireNonNull(companySnapshot, "companySnapshot must not be null");
        this.previousGroup = previousGroup;
        this.previousSource = previousSource;
        this.newGroup = Objects.requireNonNull(newGroup, "newGroup must not be null");
        this.newSource = Objects.requireNonNull(newSource, "newSource must not be null");
        this.changedAt = Objects.requireNonNull(changedAt, "changedAt must not be null");
        this.origin = Objects.requireNonNull(origin, "origin must not be null");
    }

    public Long getId() { return id; }
    public StockGroup getPreviousGroup() { return previousGroup; }
    public GroupSource getPreviousSource() { return previousSource; }
    public StockGroup getNewGroup() { return newGroup; }
    public GroupSource getNewSource() { return newSource; }
    public Instant getChangedAt() { return changedAt; }
    public HistoryOrigin getOrigin() { return origin; }
}
