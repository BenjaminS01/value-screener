package com.valuescreener.deepanalysis;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("app.deep-analysis")
public record DeepAnalysisProperties(
        @DefaultValue("lambda") String mode,
        @DefaultValue("deep-analysis-demo") String functionName,
        @DefaultValue("eu-central-1") String region) {
}
