package com.valuescreener.deepanalysis;

import java.time.Instant;

public record DeepAnalysisResult(
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
        Source source) {

    public enum Source { LAMBDA, LOCAL_STUB }
}
