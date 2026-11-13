package com.valuescreener.limits;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.limits")
public record LimitsProperties(int deepAnalysisPerUser, int deepAnalysisPerAdmin, int globalPerDay) {
}
