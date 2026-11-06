package com.valuescreener.research;

import jakarta.validation.constraints.Size;

/** Null fields stay unchanged. */
public record UpdateStockGroupRequest(@Size(max = 100) String name, Integer sortOrder) {
}
