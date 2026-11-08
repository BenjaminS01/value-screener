package com.valuescreener.research;

import jakarta.validation.constraints.NotBlank;

public record AssignGroupRequest(@NotBlank String groupKey) {
}
