package com.valuescreener.research;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FindingView(
        ResearchCriterion criterionKey,
        BigDecimal numericValue,
        Boolean booleanValue,
        String claim,
        String sourceUrl,
        LocalDate asOfDate,
        boolean stale
) {
    public static FindingView from(ResearchFinding finding, ResearchFreshness freshness) {
        return new FindingView(finding.getCriterionKey(), finding.getNumericValue(),
                finding.getBooleanValue(), finding.getClaim(), finding.getSourceUrl(),
                finding.getAsOfDate(), freshness.isStale(finding.getAsOfDate()));
    }
}
