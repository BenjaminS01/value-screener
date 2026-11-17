package com.valuescreener.research;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FindingRequest(
        @NotNull ResearchCriterion criterionKey,
        @Digits(integer = 8, fraction = 10) BigDecimal numericValue,
        Boolean booleanValue,
        @NotBlank @Size(max = 2000) String claim,
        @Size(max = 1000) @URL @Pattern(regexp = "^https?://.+", flags = Pattern.Flag.CASE_INSENSITIVE) String sourceUrl,
        @NotNull LocalDate asOfDate
) {
}
