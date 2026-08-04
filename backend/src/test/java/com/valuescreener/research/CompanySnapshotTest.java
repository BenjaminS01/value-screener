package com.valuescreener.research;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompanySnapshotTest {

    private static CompanySnapshot minimalSnapshot() {
        return new CompanySnapshot(
                "aapl", "us0378331005", "Apple Inc.", "Information Technology", "USA",
                "Designs and sells consumer electronics.", null, null, null,
                LocalDate.of(2026, 8, 1), Set.of("https://example.com/aapl-key-stats"));
    }

    @Test
    void createsSnapshotWithNormalizedUppercaseTickerAndIsin() {
        CompanySnapshot snapshot = minimalSnapshot();

        assertThat(snapshot.getTicker()).isEqualTo("AAPL");
        assertThat(snapshot.getIsin()).isEqualTo("US0378331005");
        assertThat(snapshot.getSources()).containsExactly("https://example.com/aapl-key-stats");
    }

    @Test
    void rejectsBlankTicker() {
        assertThatThrownBy(() -> new CompanySnapshot(
                "  ", "US0378331005", "Apple Inc.", "Information Technology", "USA",
                "Designs and sells consumer electronics.", null, null, null, LocalDate.of(2026, 8, 1), Set.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ticker");
    }

    @Test
    void rejectsMalformedIsin() {
        assertThatThrownBy(() -> new CompanySnapshot(
                "AAPL", "NOT-AN-ISIN", "Apple Inc.", "Information Technology", "USA",
                "Designs and sells consumer electronics.", null, null, null, LocalDate.of(2026, 8, 1), Set.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("isin");
    }

    @Test
    void rejectsBlankBusinessDescription() {
        assertThatThrownBy(() -> new CompanySnapshot(
                "AAPL", "US0378331005", "Apple Inc.", "Information Technology", "USA",
                "  ", null, null, null, LocalDate.of(2026, 8, 1), Set.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("businessDescription");
    }

    @Test
    void constructorRecordsWhichOptionalFieldsWerePresent() {
        FinancialStats stats = new FinancialStats(new BigDecimal("28.0"), null, null, null, null, null, null, null);
        CompanySnapshot snapshot = new CompanySnapshot(
                "AAPL", "US0378331005", "Apple Inc.", "Information Technology", "USA",
                "Designs and sells consumer electronics.", "Strong brand moat.",
                "Regulatory risk in the EU; expansion opportunity in services.", stats,
                LocalDate.of(2026, 8, 1), Set.of());

        assertThat(snapshot.getUpdatedFields()).containsExactlyInAnyOrder(
                "peRatio", "moatNote", "opportunitiesAndRisksNote");
    }

    @Test
    void applyUpdateOverwritesProvidedFieldsAndKeepsExistingOnesWhenNotProvided() {
        FinancialStats initialStats = new FinancialStats(new BigDecimal("28.0"), new BigDecimal("40.0"), null, null, null, null, null, null);
        CompanySnapshot snapshot = new CompanySnapshot(
                "AAPL", "US0378331005", "Apple Inc.", "Information Technology", "USA",
                "Designs and sells consumer electronics.", "Strong brand moat.",
                "Regulatory risk in the EU.", initialStats,
                LocalDate.of(2026, 8, 1), Set.of("https://example.com/first-source"));

        FinancialStats freshPeOnly = new FinancialStats(new BigDecimal("29.5"), null, null, null, null, null, null, null);
        snapshot.applyUpdate("Apple Inc.", "Information Technology", "USA",
                "Designs and sells consumer electronics and services.", null, null, freshPeOnly,
                LocalDate.of(2026, 8, 5), Set.of("https://example.com/second-source"));

        assertThat(snapshot.getFinancialStats().getPeRatio()).isEqualByComparingTo("29.5");
        assertThat(snapshot.getFinancialStats().getPbRatio()).isEqualByComparingTo("40.0");
        assertThat(snapshot.getMoatNote()).isEqualTo("Strong brand moat.");
        assertThat(snapshot.getOpportunitiesAndRisksNote()).isEqualTo("Regulatory risk in the EU.");
        assertThat(snapshot.getAsOfDate()).isEqualTo(LocalDate.of(2026, 8, 5));
        assertThat(snapshot.getSources()).containsExactlyInAnyOrder(
                "https://example.com/first-source", "https://example.com/second-source");
    }

    @Test
    void applyUpdateTracksOnlyFieldsProvidedInThisCall() {
        CompanySnapshot snapshot = minimalSnapshot();

        FinancialStats freshRoeOnly = new FinancialStats(null, null, new BigDecimal("35.0"), null, null, null, null, null);
        snapshot.applyUpdate("Apple Inc.", "Information Technology", "USA",
                "Designs and sells consumer electronics.", null, null, freshRoeOnly, LocalDate.of(2026, 8, 5), Set.of());

        assertThat(snapshot.getUpdatedFields()).containsExactly("roePercent");
    }

    @Test
    void applyUpdateOverwritesOpportunitiesAndRisksNoteWhenProvided() {
        CompanySnapshot snapshot = minimalSnapshot();

        snapshot.applyUpdate("Apple Inc.", "Information Technology", "USA",
                "Designs and sells consumer electronics.", null,
                "New risk: supply chain concentration in one country.", null,
                LocalDate.of(2026, 8, 5), Set.of());

        assertThat(snapshot.getOpportunitiesAndRisksNote())
                .isEqualTo("New risk: supply chain concentration in one country.");
        assertThat(snapshot.getUpdatedFields()).containsExactly("opportunitiesAndRisksNote");
    }
}
