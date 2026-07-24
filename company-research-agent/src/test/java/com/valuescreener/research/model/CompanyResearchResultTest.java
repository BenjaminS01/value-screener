package com.valuescreener.research.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompanyResearchResultTest {

    @Test
    void sourcesListIsImmutable() {
        List<SourceReference> mutableSources = new ArrayList<>();
        mutableSources.add(new SourceReference("https://example.com/report", "Revenue grew 5%"));

        CompanyResearchResult result = new CompanyResearchResult(
                "AAPL", "summary", "assessment", mutableSources, ConfidenceLevel.HIGH,
                CompanyResearchResult.CURRENT_PROMPT_VERSION);

        assertThatThrownBy(() -> result.sources().add(
                new SourceReference("https://example.com/other", "Other claim")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void lowConfidenceFactoryReturnsLowConfidenceResultWithNoSources() {
        CompanyResearchResult result = CompanyResearchResult.lowConfidence("AAPL", "No recent filing found");

        assertThat(result.confidence()).isEqualTo(ConfidenceLevel.LOW);
        assertThat(result.sources()).isEmpty();
        assertThat(result.summary()).isEqualTo("No recent filing found");
        assertThat(result.promptVersion()).isEqualTo(CompanyResearchResult.CURRENT_PROMPT_VERSION);
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
