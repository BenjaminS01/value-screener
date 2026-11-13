package com.valuescreener.deepanalysis;

import com.valuescreener.limits.Quota;

import java.time.Instant;

/** Lambda answer plus the caller's remaining daily quota. */
public record DeepAnalysisResponse(
        String status,
        String message,
        String greeting,
        String functionName,
        String functionVersion,
        String region,
        int memoryLimitMb,
        String awsRequestId,
        boolean coldStart,
        Instant invokedAt,
        DeepAnalysisResult.Source source,
        int remainingToday,
        int limitPerDay) {

    static DeepAnalysisResponse of(DeepAnalysisResult r, Quota quota) {
        return new DeepAnalysisResponse(r.status(), r.message(), r.greeting(), r.functionName(),
                r.functionVersion(), r.region(), r.memoryLimitMb(), r.awsRequestId(), r.coldStart(),
                r.invokedAt(), r.source(), quota.remainingToday(), quota.limitPerDay());
    }
}
