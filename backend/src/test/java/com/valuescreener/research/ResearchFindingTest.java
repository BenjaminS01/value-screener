package com.valuescreener.research;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResearchFindingTest {

    @Test
    void createsFindingWithNumericValue() {
        ResearchFinding finding = new ResearchFinding(
                ResearchCriterion.PE_RATIO, new BigDecimal("28.0"), null,
                "Trailing P/E of 28.0 per the latest 10-Q.", "https://example.com/aapl-key-stats",
                LocalDate.of(2026, 8, 1));

        assertThat(finding.getCriterionKey()).isEqualTo(ResearchCriterion.PE_RATIO);
        assertThat(finding.getNumericValue()).isEqualByComparingTo("28.0");
        assertThat(finding.getBooleanValue()).isNull();
        assertThat(finding.getClaim()).isEqualTo("Trailing P/E of 28.0 per the latest 10-Q.");
        assertThat(finding.getSourceUrl()).isEqualTo("https://example.com/aapl-key-stats");
        assertThat(finding.getAsOfDate()).isEqualTo(LocalDate.of(2026, 8, 1));
    }

    @Test
    void createsFindingWithBooleanValue() {
        ResearchFinding finding = new ResearchFinding(
                ResearchCriterion.CURRENT_YEAR_FCF_POSITIVE, null, Boolean.TRUE,
                "Free cash flow was positive in the current fiscal year.", null,
                LocalDate.of(2026, 8, 1));

        assertThat(finding.getBooleanValue()).isTrue();
        assertThat(finding.getNumericValue()).isNull();
    }

    @Test
    void rejectsNullCriterionKey() {
        assertThatThrownBy(() -> new ResearchFinding(
                null, new BigDecimal("28.0"), null, "claim text", null, LocalDate.of(2026, 8, 1)))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("criterionKey");
    }

    @Test
    void rejectsBlankClaim() {
        assertThatThrownBy(() -> new ResearchFinding(
                ResearchCriterion.PE_RATIO, new BigDecimal("28.0"), null, "  ", null,
                LocalDate.of(2026, 8, 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("claim");
    }

    @Test
    void applyUpdateOverwritesValueClaimSourceAndDate() {
        ResearchFinding finding = new ResearchFinding(
                ResearchCriterion.PE_RATIO, new BigDecimal("28.0"), null, "Original claim.",
                "https://example.com/original", LocalDate.of(2026, 8, 1));

        finding.applyUpdate(new BigDecimal("29.5"), null, "Updated claim.",
                "https://example.com/updated", LocalDate.of(2026, 8, 5));

        assertThat(finding.getNumericValue()).isEqualByComparingTo("29.5");
        assertThat(finding.getClaim()).isEqualTo("Updated claim.");
        assertThat(finding.getSourceUrl()).isEqualTo("https://example.com/updated");
        assertThat(finding.getAsOfDate()).isEqualTo(LocalDate.of(2026, 8, 5));
        assertThat(finding.getCriterionKey()).isEqualTo(ResearchCriterion.PE_RATIO);
    }
}
