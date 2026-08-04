package com.valuescreener.research;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class FinancialStatsTest {

    @Test
    void mergedWithPrefersNewValuesWhenPresent() {
        FinancialStats existing = new FinancialStats(
                new BigDecimal("15.0"), new BigDecimal("2.0"), new BigDecimal("18.0"), new BigDecimal("0.5"),
                new BigDecimal("12.0"), Boolean.TRUE, Boolean.TRUE, new BigDecimal("5.0"));
        FinancialStats update = new FinancialStats(
                new BigDecimal("16.0"), null, null, null, null, null, null, null);

        FinancialStats merged = existing.mergedWith(update);

        assertThat(merged.getPeRatio()).isEqualByComparingTo("16.0");
        assertThat(merged.getPbRatio()).isEqualByComparingTo("2.0");
        assertThat(merged.getRoePercent()).isEqualByComparingTo("18.0");
    }

    @Test
    void mergedWithKeepsExistingValuesWhenUpdateFieldIsNull() {
        FinancialStats existing = new FinancialStats(
                new BigDecimal("15.0"), null, null, null, null, null, null, null);
        FinancialStats update = FinancialStats.empty();

        FinancialStats merged = existing.mergedWith(update);

        assertThat(merged.getPeRatio()).isEqualByComparingTo("15.0");
    }

    @Test
    void presentFieldNamesReturnsOnlyNonNullFields() {
        FinancialStats stats = new FinancialStats(
                new BigDecimal("15.0"), null, new BigDecimal("18.0"), null, null, null, null, null);

        assertThat(stats.presentFieldNames()).containsExactlyInAnyOrder("peRatio", "roePercent");
    }

    @Test
    void presentFieldNamesReturnsEmptySetWhenAllFieldsAreNull() {
        assertThat(FinancialStats.empty().presentFieldNames()).isEmpty();
    }
}
