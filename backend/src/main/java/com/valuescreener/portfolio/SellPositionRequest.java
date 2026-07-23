package com.valuescreener.portfolio;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record SellPositionRequest(
        @NotBlank String isin,
        @NotNull @Positive BigDecimal quantity
) {
}
