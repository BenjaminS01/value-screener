package com.valuescreener.research.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QuickResearchResultTest {

    @Test
    void noDataFactoryReturnsFlaggedResultWithNoFieldsPopulated() {
        QuickResearchResult result = QuickResearchResult.noData("XYZ", "No current key-statistics page found");

        assertThat(result.noReliableDataFound()).isTrue();
        assertThat(result.noReliableDataFoundReason()).isEqualTo("No current key-statistics page found");
        assertThat(result.currentPe()).isNull();
        assertThat(result.promptVersion()).isEqualTo(QuickResearchResult.CURRENT_PROMPT_VERSION);
    }

    @Test
    void allowsPartialDataWhenOnlySomeFieldsAreFound() {
        QuickResearchResult result = new QuickResearchResult(
                "XYZ",
                new NumericFinding(24.3, new SourceReference("https://example.com/stats", "Current P/E of 24.3")),
                null, null, null, null, null, null, null, null, null, null,
                false, null,
                QuickResearchResult.CURRENT_PROMPT_VERSION);

        assertThat(result.noReliableDataFound()).isFalse();
        assertThat(result.currentPe().value()).isEqualTo(24.3);
        assertThat(result.currentPb()).isNull();
    }

    @Test
    void booleanFindingCarriesValueAndSource() {
        BooleanFinding finding = new BooleanFinding(
                true, new SourceReference("https://example.com/cashflow", "Free cash flow was positive this year"));

        assertThat(finding.value()).isTrue();
        assertThat(finding.source().claim()).contains("positive");
    }

    @Test
    void stage1SnapshotAllowsEitherFieldToBeNull() {
        Stage1Snapshot peOnly = new Stage1Snapshot(24.3, null);

        assertThat(peOnly.currentPe()).isEqualTo(24.3);
        assertThat(peOnly.currentPb()).isNull();
    }
}
