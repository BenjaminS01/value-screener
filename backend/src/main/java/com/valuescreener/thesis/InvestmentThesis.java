package com.valuescreener.thesis;


import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/** The owner's thesis for one stock, keyed by ISIN. Independent of research data (no FK to a snapshot). */
public class InvestmentThesis {

    static final int MAX_TEXT = 4000;
    static final int MAX_STATUS_REASON = 500;

    private String isin;

    private String whyInteresting;

    private String expectedDevelopment;

    private String exitCriteria;

    private ThesisStatus status;

    private String statusReason;

    private Instant updatedAt;

    private Long version;

    private InvestmentThesis() {
        // for restore()
    }

    /** Rebuilds a stored thesis without re-validating; only the persistence layer uses this. */
    static InvestmentThesis restore(String isin, String why, String expected, String exit, ThesisStatus status,
                                    String statusReason, Instant updatedAt, Long version) {
        InvestmentThesis thesis = new InvestmentThesis();
        thesis.isin = isin;
        thesis.whyInteresting = why;
        thesis.expectedDevelopment = expected;
        thesis.exitCriteria = exit;
        thesis.status = status;
        thesis.statusReason = statusReason;
        thesis.updatedAt = updatedAt;
        thesis.version = version;
        return thesis;
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
    void setVersion(Long version) { this.version = version; }
}
