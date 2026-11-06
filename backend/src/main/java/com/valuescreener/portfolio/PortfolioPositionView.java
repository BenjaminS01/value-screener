package com.valuescreener.portfolio;

/** Response for USER: structurally carries no holdings data (quantity, price, date). */
public record PortfolioPositionView(String ticker, String isin, String companyName) {
}
