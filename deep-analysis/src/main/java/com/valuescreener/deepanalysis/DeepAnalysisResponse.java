package com.valuescreener.deepanalysis;

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
        String invokedAt) {}
