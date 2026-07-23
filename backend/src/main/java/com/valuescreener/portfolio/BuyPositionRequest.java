package com.valuescreener.portfolio;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BuyPositionRequest(
        @NotBlank String ticker,
        @NotBlank String isin,
        @NotBlank String companyName,
        @NotNull @Positive BigDecimal quantity,
        @NotNull @Positive BigDecimal entryPrice,
        @NotNull LocalDate purchaseDate
) {
}
