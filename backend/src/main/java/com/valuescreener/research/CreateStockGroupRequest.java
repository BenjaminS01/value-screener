package com.valuescreener.research;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateStockGroupRequest(@NotBlank String key, @NotBlank @Size(max = 100) String name) {
}
