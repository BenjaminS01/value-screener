package com.valuescreener.portfolio;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Response for ADMIN: includes holdings data. */
public record AdminPortfolioPositionView(String ticker, String isin, String companyName,
                                         BigDecimal quantity, BigDecimal entryPrice, LocalDate purchaseDate) {
}
