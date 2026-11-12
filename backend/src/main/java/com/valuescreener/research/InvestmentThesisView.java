package com.valuescreener.research;

import java.time.Instant;

public record InvestmentThesisView(
        String isin,
        String whyInteresting,
        String expectedDevelopment,
        String exitCriteria,
        ThesisStatus status,
        String statusReason,
        Instant updatedAt,
        Long version,
        boolean holding
) {
}
