package com.valuescreener.research.snapshot;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/** groupSource is only honored on the sync path (reviewed export); the ingest path ignores it and always records AI. */
public record UpsertCompanySnapshotRequest(
        @NotBlank @Size(max = 10) String ticker,
        @NotBlank @Pattern(regexp = "\\s*[A-Za-z]{2}[A-Za-z0-9]{9}[0-9]\\s*") String isin,
        @NotBlank @Size(max = 200) String companyName,
        @NotBlank @Size(max = 100) String country,
        @NotBlank @Size(max = 50000) String businessDescription,
        @NotBlank String groupKey,
        GroupSource groupSource,
        @Size(max = 50) List<@Valid FindingRequest> findings
) {
}
