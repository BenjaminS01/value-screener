package com.valuescreener.research;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FindingRequest(
        @NotNull ResearchCriterion criterionKey,
        BigDecimal numericValue,
        Boolean booleanValue,
        @NotBlank @Size(max = 2000) String claim,
        @URL String sourceUrl,
        @NotNull LocalDate asOfDate
) {
}
