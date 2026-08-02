package com.valuescreener.research.agent;

record RawQuickResearchResponse(
        RawNumericFinding currentPe,
        RawNumericFinding currentPb,
        RawNumericFinding fiveYearAveragePe,
        RawNumericFinding fiveYearAveragePb,
        RawNumericFinding roe,
        RawNumericFinding debtToEquity,
        RawNumericFinding currentRatio,
        RawNumericFinding currentYearNetMargin,
        RawBooleanFinding currentYearFcfPositive,
        RawBooleanFinding currentYearNetIncomeGrew,
        RawNumericFinding insiderOwnershipShare,
        boolean noReliableDataFound,
        String noReliableDataFoundReason) {
}
