package com.valuescreener.research.model;

public record CompanyResearchResult(
        String ticker,
        SourceReference marginTrend,
        SourceReference freeCashFlowTrend,
        SourceReference profitStability,
        NumericFinding interestCoverage,
        NumericFinding currentRatio,
        SourceReference moatAssessment,
        SourceReference managementQuality,
        SourceReference valueTrapAssessment,
        ConfidenceLevel confidence,
        String lowConfidenceReason,
        String promptVersion) {

    /**
     * Bumped whenever the research prompt or output contract changes, so that stored analyses
     * from different generations can be told apart when comparing across quarters.
     */
    public static final String CURRENT_PROMPT_VERSION = "research-v2";

    public static CompanyResearchResult lowConfidence(String ticker, String reason) {
        return new CompanyResearchResult(
                ticker, null, null, null, null, null, null, null, null,
                ConfidenceLevel.LOW, reason, CURRENT_PROMPT_VERSION);
    }
}
