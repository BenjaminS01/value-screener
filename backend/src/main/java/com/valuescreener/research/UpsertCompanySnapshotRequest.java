package com.valuescreener.research;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UpsertCompanySnapshotRequest(
        @NotBlank String ticker,
        @NotBlank String isin,
        @NotBlank String companyName,
        @NotBlank String sector,
        @NotBlank String country,
        @NotBlank String businessDescription,
        @Size(max = 50) List<@Valid FindingRequest> findings
) {
}
