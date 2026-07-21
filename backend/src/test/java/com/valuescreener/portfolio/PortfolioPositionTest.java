package com.valuescreener.portfolio;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PortfolioPositionTest {

    @Test
    void createsPositionWithNormalizedUppercaseTickerAndIsin() {
        PortfolioPosition position = new PortfolioPosition(
                "aapl", "us0378331005", "Apple Inc.", new BigDecimal("10"), new BigDecimal("150.00"), LocalDate.of(2026, 1, 15));

        assertThat(position.getTicker()).isEqualTo("AAPL");
        assertThat(position.getIsin()).isEqualTo("US0378331005");
        assertThat(position.getCompanyName()).isEqualTo("Apple Inc.");
    }

    @Test
    void rejectsBlankTicker() {
        assertThatThrownBy(() -> new PortfolioPosition(
                "  ", "US0378331005", "Apple Inc.", new BigDecimal("10"), new BigDecimal("150.00"), LocalDate.of(2026, 1, 15)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ticker");
    }

    @Test
    void rejectsTickerLongerThanTenCharacters() {
        assertThatThrownBy(() -> new PortfolioPosition(
                "TOOLONGTICKER", "US0378331005", "Apple Inc.", new BigDecimal("10"), new BigDecimal("150.00"), LocalDate.of(2026, 1, 15)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ticker");
    }

    @Test
    void rejectsBlankIsin() {
        assertThatThrownBy(() -> new PortfolioPosition(
                "AAPL", "  ", "Apple Inc.", new BigDecimal("10"), new BigDecimal("150.00"), LocalDate.of(2026, 1, 15)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("isin");
    }

    @Test
    void rejectsMalformedIsin() {
        assertThatThrownBy(() -> new PortfolioPosition(
                "AAPL", "NOT-AN-ISIN", "Apple Inc.", new BigDecimal("10"), new BigDecimal("150.00"), LocalDate.of(2026, 1, 15)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("isin");
    }

    @Test
    void rejectsBlankCompanyName() {
        assertThatThrownBy(() -> new PortfolioPosition(
                "AAPL", "US0378331005", "  ", new BigDecimal("10"), new BigDecimal("150.00"), LocalDate.of(2026, 1, 15)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("companyName");
    }

    @Test
    void rejectsZeroOrNegativeQuantity() {
        assertThatThrownBy(() -> new PortfolioPosition(
                "AAPL", "US0378331005", "Apple Inc.", BigDecimal.ZERO, new BigDecimal("150.00"), LocalDate.of(2026, 1, 15)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("quantity");
    }

    @Test
    void rejectsZeroOrNegativeEntryPrice() {
        assertThatThrownBy(() -> new PortfolioPosition(
                "AAPL", "US0378331005", "Apple Inc.", new BigDecimal("10"), BigDecimal.ZERO, LocalDate.of(2026, 1, 15)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("entryPrice");
    }

    @Test
    void rejectsNullPurchaseDate() {
        assertThatThrownBy(() -> new PortfolioPosition(
                "AAPL", "US0378331005", "Apple Inc.", new BigDecimal("10"), new BigDecimal("150.00"), null))
                .isInstanceOf(NullPointerException.class);
    }
}
