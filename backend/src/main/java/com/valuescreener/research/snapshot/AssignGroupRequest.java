package com.valuescreener.research.snapshot;

import jakarta.validation.constraints.NotBlank;

public record AssignGroupRequest(@NotBlank String groupKey) {
}
