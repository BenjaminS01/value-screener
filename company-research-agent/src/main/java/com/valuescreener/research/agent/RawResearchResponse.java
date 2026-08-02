package com.valuescreener.research.agent;

record RawResearchResponse(
        RawSourceReference marginTrend,
        RawSourceReference freeCashFlowTrend,
        RawSourceReference profitStability,
        RawNumericFinding interestCoverage,
        RawNumericFinding currentRatio,
        RawSourceReference moatAssessment,
        RawSourceReference managementQuality,
        RawSourceReference valueTrapAssessment,
        boolean noReliableReportFound,
        String noReliableReportFoundReason) {
}
