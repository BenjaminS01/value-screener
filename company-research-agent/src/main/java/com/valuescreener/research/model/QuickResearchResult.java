package com.valuescreener.research.model;

public record QuickResearchResult(
        String ticker,
        NumericFinding currentPe,
        NumericFinding currentPb,
        NumericFinding fiveYearAveragePe,
        NumericFinding fiveYearAveragePb,
        NumericFinding roe,
        NumericFinding debtToEquity,
        NumericFinding currentRatio,
        NumericFinding currentYearNetMargin,
        BooleanFinding currentYearFcfPositive,
        BooleanFinding currentYearNetIncomeGrew,
        NumericFinding insiderOwnershipShare,
        boolean noReliableDataFound,
        String noReliableDataFoundReason,
        String promptVersion) {

    public static final String CURRENT_PROMPT_VERSION = "quick-research-v1";

    public static QuickResearchResult noData(String ticker, String reason) {
        return new QuickResearchResult(
                ticker, null, null, null, null, null, null, null, null, null, null, null,
                true, reason, CURRENT_PROMPT_VERSION);
    }
}
