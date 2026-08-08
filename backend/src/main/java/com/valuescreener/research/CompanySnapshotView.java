package com.valuescreener.research;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

public record CompanySnapshotView(
        Long id,
        String ticker,
        String isin,
        String companyName,
        String sector,
        String country,
        String businessDescription,
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
        LocalDate asOfDate,
        Set<String> sources,
        Set<String> updatedFields
) {
    public static CompanySnapshotView from(CompanySnapshot snapshot) {
        FinancialStats stats = snapshot.getFinancialStats();
        return new CompanySnapshotView(
                snapshot.getId(), snapshot.getTicker(), snapshot.getIsin(), snapshot.getCompanyName(),
                snapshot.getSector(), snapshot.getCountry(), snapshot.getBusinessDescription(), snapshot.getMoatNote(),
                snapshot.getOpportunitiesAndRisksNote(),
                stats.getPeRatio(), stats.getPbRatio(), stats.getRoePercent(), stats.getDebtToEquity(),
                stats.getCurrentYearNetMarginPercent(), stats.getCurrentYearFcfPositive(),
                stats.getCurrentYearNetIncomeIncreasedYoy(), stats.getInsiderOwnershipPercent(),
                snapshot.getAsOfDate(), snapshot.getSources(), snapshot.getUpdatedFields());
    }
}
