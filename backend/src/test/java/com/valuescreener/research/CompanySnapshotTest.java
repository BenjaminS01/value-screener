package com.valuescreener.research;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompanySnapshotTest {

    private static CompanySnapshot minimalSnapshot() {
        return new CompanySnapshot(
                "aapl", "us0378331005", "Apple Inc.", "Information Technology", "USA",
                "Designs and sells consumer electronics.");
    }

    @Test
    void createsSnapshotWithNormalizedUppercaseTickerAndIsin() {
        CompanySnapshot snapshot = minimalSnapshot();

        assertThat(snapshot.getTicker()).isEqualTo("AAPL");
        assertThat(snapshot.getIsin()).isEqualTo("US0378331005");
        assertThat(snapshot.getFindings()).isEmpty();
    }

    @Test
    void rejectsBlankTicker() {
        assertThatThrownBy(() -> new CompanySnapshot(
                "  ", "US0378331005", "Apple Inc.", "Information Technology", "USA",
                "Designs and sells consumer electronics."))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ticker");
    }

    @Test
    void rejectsMalformedIsin() {
        assertThatThrownBy(() -> new CompanySnapshot(
                "AAPL", "NOT-AN-ISIN", "Apple Inc.", "Information Technology", "USA",
                "Designs and sells consumer electronics."))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("isin");
    }

    @Test
    void rejectsBlankBusinessDescription() {
        assertThatThrownBy(() -> new CompanySnapshot(
                "AAPL", "US0378331005", "Apple Inc.", "Information Technology", "USA", "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("businessDescription");
    }

    @Test
    void applyUpdateOverwritesIdentityFields() {
        CompanySnapshot snapshot = minimalSnapshot();

        snapshot.applyUpdate("Apple Inc.", "Information Technology", "USA",
                "Designs and sells consumer electronics and services.");

        assertThat(snapshot.getBusinessDescription())
                .isEqualTo("Designs and sells consumer electronics and services.");
    }

    @Test
    void upsertFindingAddsNewFindingWhenCriterionNotYetPresent() {
        CompanySnapshot snapshot = minimalSnapshot();

        snapshot.upsertFinding(ResearchCriterion.PE_RATIO, new BigDecimal("28.0"), null,
                "Trailing P/E of 28.0.", "https://example.com/aapl-key-stats", LocalDate.of(2026, 8, 1));

        assertThat(snapshot.getFindings()).hasSize(1);
        assertThat(snapshot.getFindings().get(0).getCriterionKey()).isEqualTo(ResearchCriterion.PE_RATIO);
        assertThat(snapshot.getFindings().get(0).getNumericValue()).isEqualByComparingTo("28.0");
    }

    @Test
    void upsertFindingReplacesExistingFindingForSameCriterionInPlace() {
        CompanySnapshot snapshot = minimalSnapshot();
        snapshot.upsertFinding(ResearchCriterion.PE_RATIO, new BigDecimal("28.0"), null,
                "Original claim.", "https://example.com/original", LocalDate.of(2026, 8, 1));

        snapshot.upsertFinding(ResearchCriterion.PE_RATIO, new BigDecimal("29.5"), null,
                "Updated claim.", "https://example.com/updated", LocalDate.of(2026, 8, 5));

        assertThat(snapshot.getFindings()).hasSize(1);
        assertThat(snapshot.getFindings().get(0).getNumericValue()).isEqualByComparingTo("29.5");
        assertThat(snapshot.getFindings().get(0).getClaim()).isEqualTo("Updated claim.");
    }

    @Test
    void upsertFindingForDifferentCriteriaLeavesEachOtherUntouched() {
        CompanySnapshot snapshot = minimalSnapshot();
        snapshot.upsertFinding(ResearchCriterion.PE_RATIO, new BigDecimal("28.0"), null,
                "Trailing P/E of 28.0.", "https://example.com/aapl-key-stats", LocalDate.of(2026, 8, 1));

        snapshot.upsertFinding(ResearchCriterion.MOAT_ASSESSMENT, null, null,
                "Wide moat from ecosystem lock-in.", "https://example.com/moat-analysis",
                LocalDate.of(2026, 8, 5));

        assertThat(snapshot.getFindings()).hasSize(2);
        assertThat(snapshot.getFindings())
                .extracting(ResearchFinding::getCriterionKey)
                .containsExactlyInAnyOrder(ResearchCriterion.PE_RATIO, ResearchCriterion.MOAT_ASSESSMENT);
    }
}
