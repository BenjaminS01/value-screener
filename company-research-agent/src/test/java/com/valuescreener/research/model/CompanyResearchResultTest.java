package com.valuescreener.research.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompanyResearchResultTest {

    @Test
    void lowConfidenceFactoryReturnsLowConfidenceResultWithNoCriteriaPopulated() {
        CompanyResearchResult result = CompanyResearchResult.lowConfidence("AAPL", "No recent filing found");

        assertThat(result.confidence()).isEqualTo(ConfidenceLevel.LOW);
        assertThat(result.lowConfidenceReason()).isEqualTo("No recent filing found");
        assertThat(result.marginTrend()).isNull();
        assertThat(result.valueTrapAssessment()).isNull();
        assertThat(result.promptVersion()).isEqualTo(CompanyResearchResult.CURRENT_PROMPT_VERSION);
    }

    @Test
    void allowsIndividualCriteriaToBeNullWithoutAffectingConfidence() {
        CompanyResearchResult result = new CompanyResearchResult(
                "AAPL",
                new SourceReference("https://example.com/margins", "Operating margin held steady at 22% over five years"),
                null,
                null,
                null,
                null,
                new SourceReference("https://example.com/moat", "Brand strength and switching costs support pricing power"),
                null,
                new SourceReference("https://example.com/valuation", "Current P/E sits below the company's own five-year average"),
                ConfidenceLevel.HIGH,
                null,
                CompanyResearchResult.CURRENT_PROMPT_VERSION);

        assertThat(result.marginTrend()).isNotNull();
        assertThat(result.freeCashFlowTrend()).isNull();
        assertThat(result.confidence()).isEqualTo(ConfidenceLevel.HIGH);
    }

    @Test
    void numericFindingCarriesValueAndSource() {
        NumericFinding finding = new NumericFinding(
                12.4, new SourceReference("https://example.com/coverage", "EBIT covers interest 12.4x"));

        assertThat(finding.value()).isEqualTo(12.4);
        assertThat(finding.source().url()).isEqualTo("https://example.com/coverage");
    }

    @Test
    void sourceReferenceRejectsBlankUrl() {
        assertThatThrownBy(() -> new SourceReference(" ", "some claim"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sourceReferenceRejectsBlankClaim() {
        assertThatThrownBy(() -> new SourceReference("https://example.com", " "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
