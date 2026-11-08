package com.valuescreener.research;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/** Null fields stay unchanged. */
public record UpdateStockGroupRequest(@Size(max = 100) String name, @Min(0) @Max(1_000_000) Integer sortOrder) {
}
