package com.valuescreener.research;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Objects;

/** One finding of a snapshot; embedded in the snapshot item, keyed there by criterion. */
public class ResearchFinding {

    private final ResearchCriterion criterionKey;
    private BigDecimal numericValue;
    private Boolean booleanValue;
    private String claim;
    private String sourceUrl;
    private LocalDate asOfDate;

    public ResearchFinding(ResearchCriterion criterionKey, BigDecimal numericValue, Boolean booleanValue,
                            String claim, String sourceUrl, LocalDate asOfDate) {
        this.criterionKey = Objects.requireNonNull(criterionKey, "criterionKey must not be null");
        this.numericValue = normalize(numericValue);
        this.booleanValue = booleanValue;
        this.claim = requireNonBlank(claim, "claim");
        this.sourceUrl = sourceUrl;
        this.asOfDate = Objects.requireNonNull(asOfDate, "asOfDate must not be null");
    }

    void applyUpdate(BigDecimal numericValue, Boolean booleanValue, String claim, String sourceUrl,
                      LocalDate asOfDate) {
        this.numericValue = normalize(numericValue);
        this.booleanValue = booleanValue;
        this.claim = requireNonBlank(claim, "claim");
        this.sourceUrl = sourceUrl;
        this.asOfDate = Objects.requireNonNull(asOfDate, "asOfDate must not be null");
    }

    /** Content comparison as stored: BigDecimal by value (scale-insensitive), claim as it would be trimmed on write. */
    boolean differsFrom(BigDecimal numericValue, Boolean booleanValue, String claim, String sourceUrl,
                        LocalDate asOfDate) {
        boolean sameNumber = this.numericValue == null || numericValue == null
                ? this.numericValue == numericValue
                : this.numericValue.compareTo(normalize(numericValue)) == 0;
        String incomingClaim = claim == null ? null : claim.trim();
        return !(sameNumber
                && Objects.equals(this.booleanValue, booleanValue)
                && Objects.equals(this.claim, incomingClaim)
                && Objects.equals(this.sourceUrl, sourceUrl)
                && Objects.equals(this.asOfDate, asOfDate));
    }

    /** Rounds to 4 decimals: what is stored and compared, so 1.5 and 1.5000 are the same value. */
    private static BigDecimal normalize(BigDecimal value) {
        if (value == null) {
            return null;
        }
        BigDecimal rounded = value.setScale(4, RoundingMode.HALF_UP);
        if (rounded.precision() - rounded.scale() > 8) { // formerly NUMERIC(12,4): at most 8 integer digits
            throw new IllegalArgumentException("numericValue must have at most 8 integer digits");
        }
        return rounded;
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    public ResearchCriterion getCriterionKey() { return criterionKey; }
    public BigDecimal getNumericValue() { return numericValue; }
    public Boolean getBooleanValue() { return booleanValue; }
    public String getClaim() { return claim; }
    public String getSourceUrl() { return sourceUrl; }
    public LocalDate getAsOfDate() { return asOfDate; }
}
