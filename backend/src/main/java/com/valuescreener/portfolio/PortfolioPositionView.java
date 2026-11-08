package com.valuescreener.portfolio;

import com.valuescreener.research.StockGroupRef;

/** Response for USER: structurally carries no holdings data (quantity, price, date). Group may be null. */
public record PortfolioPositionView(String ticker, String isin, String companyName, StockGroupRef group) {
}
