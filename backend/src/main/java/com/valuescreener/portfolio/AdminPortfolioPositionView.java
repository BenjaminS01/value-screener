package com.valuescreener.portfolio;

import com.valuescreener.research.StockGroupRef;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Response for ADMIN: includes holdings data. Group may be null. */
public record AdminPortfolioPositionView(String ticker, String isin, String companyName, StockGroupRef group,
                                         BigDecimal quantity, BigDecimal entryPrice, LocalDate purchaseDate) {
}
