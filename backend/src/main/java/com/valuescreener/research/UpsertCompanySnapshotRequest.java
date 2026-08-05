package com.valuescreener.research;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

public record UpsertCompanySnapshotRequest(
        @NotBlank String ticker,
        @NotBlank String isin,
        @NotBlank String companyName,
        @NotBlank String sector,
        @NotBlank String country,
        @NotBlank String businessDescription,
        String moatNote,
        String opportunitiesAndRisksNote,
        BigDecimal peRatio,
        BigDecimal pbRatio,
        BigDecimal roePercent,
        BigDecimal debtToEquity,
        BigDecimal currentYearNetMarginPercent,
        Boolean currentYearFcfPositive,
        Boolean currentYearNetIncomeIncreasedYoy,
        BigDecimal insiderOwnershipPercent,
        @NotNull LocalDate asOfDate,
        Set<String> sources
) {
}
