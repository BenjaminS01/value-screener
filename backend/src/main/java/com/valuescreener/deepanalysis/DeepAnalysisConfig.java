package com.valuescreener.deepanalysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.lambda.LambdaClient;

import java.time.Clock;
import java.time.Duration;

@Configuration
@EnableConfigurationProperties(DeepAnalysisProperties.class)
public class DeepAnalysisConfig {

    /**
     * Region is set explicitly: the SDK's default region chain would fail at build time without AWS_REGION.
     * Credentials are only resolved on the first call.
     */
    @Bean
    @ConditionalOnProperty(name = "app.deep-analysis.mode", havingValue = "lambda", matchIfMissing = true)
    public LambdaClient lambdaClient(DeepAnalysisProperties props) {
        return LambdaClient.builder()
                .region(Region.of(props.region()))
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .apiCallTimeout(Duration.ofSeconds(10))
                        .build())
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "app.deep-analysis.mode", havingValue = "lambda", matchIfMissing = true)
    public DeepAnalysisInvoker lambdaDeepAnalysisInvoker(LambdaClient client, ObjectMapper mapper,
                                                         DeepAnalysisProperties props) {
        return new LambdaDeepAnalysisInvoker(client, mapper, props.functionName());
    }

    @Bean
    @ConditionalOnProperty(name = "app.deep-analysis.mode", havingValue = "stub")
    public DeepAnalysisInvoker localStubDeepAnalysisInvoker(Clock clock) {
        return new LocalStubDeepAnalysisInvoker(clock);
    }
}
