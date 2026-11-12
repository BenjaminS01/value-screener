package com.valuescreener.research;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/** The owner's thesis for one stock, keyed by ISIN. Independent of research data (no FK to a snapshot). */
@Entity
@Table(name = "investment_thesis")
public class InvestmentThesis {

    static final int MAX_TEXT = 4000;
    static final int MAX_STATUS_REASON = 500;

    @Id
    @Column(length = 12, updatable = false)
    private String isin;

    @Column(name = "why_interesting", columnDefinition = "TEXT")
    private String whyInteresting;

    @Column(name = "expected_development", columnDefinition = "TEXT")
    private String expectedDevelopment;

    @Column(name = "exit_criteria", columnDefinition = "TEXT")
    private String exitCriteria;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private ThesisStatus status;

    @Column(name = "status_reason", length = MAX_STATUS_REASON)
    private String statusReason;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected InvestmentThesis() {
        // JPA
    }

    public InvestmentThesis(String isin, Instant now) {
        this.isin = Objects.requireNonNull(isin, "isin must not be null");
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    /** Replaces all fields. Blank text becomes null. Validates everything before changing anything. */
    public void replace(String why, String expected, String exit, ThesisStatus newStatus, String reason, Instant now) {
        String newWhy = textOrNull(why);
        String newExpected = textOrNull(expected);
        String newExit = textOrNull(exit);
        String newReason = textOrNull(reason);
        requireMaxLength(newWhy, newExpected, newExit);
        if (newReason != null && newStatus == null) {
            throw new IllegalArgumentException("statusReason requires status");
        }
        if (newReason != null && newReason.length() > MAX_STATUS_REASON) {
            throw new IllegalArgumentException("statusReason must not exceed " + MAX_STATUS_REASON + " characters");
        }
        Objects.requireNonNull(now, "now must not be null");
        this.whyInteresting = newWhy;
        this.expectedDevelopment = newExpected;
        this.exitCriteria = newExit;
        this.status = newStatus;
        this.statusReason = newReason;
        this.updatedAt = now;
    }

    /** Appends a dated paragraph to {@code whyInteresting}; existing text is never changed. */
    public void appendPurchaseReason(String reason, LocalDate date, Instant now) {
        String paragraph = "[" + date + "] Kauf: " + reason.trim();
        String combined = whyInteresting == null ? paragraph : whyInteresting + "\n\n" + paragraph;
        requireMaxLength(combined);
        Objects.requireNonNull(now, "now must not be null");
        this.whyInteresting = combined;
        this.updatedAt = now;
    }

    private static String textOrNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static void requireMaxLength(String... texts) {
        for (String text : texts) {
            if (text != null && text.length() > MAX_TEXT) {
                throw new ThesisTooLongException();
            }
        }
    }

    public String getIsin() { return isin; }
    public String getWhyInteresting() { return whyInteresting; }
    public String getExpectedDevelopment() { return expectedDevelopment; }
    public String getExitCriteria() { return exitCriteria; }
    public ThesisStatus getStatus() { return status; }
    public String getStatusReason() { return statusReason; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }
}
