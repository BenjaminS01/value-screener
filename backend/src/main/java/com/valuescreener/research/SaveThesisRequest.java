package com.valuescreener.research;

/** {@code version} is null when creating, otherwise the version the client last saw. */
public record SaveThesisRequest(
        String whyInteresting,
        String expectedDevelopment,
        String exitCriteria,
        ThesisStatus status,
        String statusReason,
        Long version
) {
}
