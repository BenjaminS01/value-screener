package com.valuescreener.research;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UpsertCompanySnapshotRequest(
        @NotBlank String ticker,
        @NotBlank @Pattern(regexp = "\\s*[A-Za-z]{2}[A-Za-z0-9]{9}[0-9]\\s*") String isin,
        @NotBlank String companyName,
        @NotBlank String sector,
        @NotBlank String country,
        @NotBlank String businessDescription,
        @Size(max = 50) List<@Valid FindingRequest> findings
) {
}
