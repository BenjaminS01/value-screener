package com.valuescreener.research.history;

import com.valuescreener.research.snapshot.ResearchCriterion;
import java.math.BigDecimal;
import java.time.LocalDate;

/** The content of a finding before a re-research replaced it. */
public record ReplacedFinding(ResearchCriterion criterionKey, BigDecimal numericValue, Boolean booleanValue,
                              String claim, String sourceUrl, LocalDate asOfDate) {
}
