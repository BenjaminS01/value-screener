package com.valuescreener.research.model;

import java.util.List;

public record CompanyResearchResult(
        String ticker,
        String summary,
        String valueTrapAssessment,
        List<SourceReference> sources,
        ConfidenceLevel confidence,
        String promptVersion) {

    /**
     * Bumped whenever the research prompt or output contract changes, so that stored analyses
     * from different generations can be told apart when comparing across quarters.
     */
    public static final String CURRENT_PROMPT_VERSION = "research-v1";

    public CompanyResearchResult {
        sources = List.copyOf(sources);
    }

    public static CompanyResearchResult lowConfidence(String ticker, String reason) {
        return new CompanyResearchResult(
                ticker,
                reason,
                "Insufficient sourced information to assess valuation drivers.",
                List.of(),
                ConfidenceLevel.LOW,
                CURRENT_PROMPT_VERSION);
    }
}
