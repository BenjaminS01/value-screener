package com.valuescreener.deepanalysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import software.amazon.awssdk.services.lambda.LambdaClient;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;

class DeepAnalysisConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withBean(Clock.class, Clock::systemUTC)
            .withUserConfiguration(DeepAnalysisConfig.class);

    @Test
    void stubModeHasStubAndNoLambdaClient() {
        runner.withPropertyValues("app.deep-analysis.mode=stub").run(ctx -> {
            assertThat(ctx).hasSingleBean(DeepAnalysisInvoker.class);
            assertThat(ctx.getBean(DeepAnalysisInvoker.class)).isInstanceOf(LocalStubDeepAnalysisInvoker.class);
            assertThat(ctx).doesNotHaveBean(LambdaClient.class);
        });
    }

    @Test
    void lambdaModeIsExplicitlyConfigured() {
        runner.withPropertyValues("app.deep-analysis.mode=lambda").run(ctx -> {
            assertThat(ctx.getBean(DeepAnalysisInvoker.class)).isInstanceOf(LambdaDeepAnalysisInvoker.class);
            assertThat(ctx).hasSingleBean(LambdaClient.class);
        });
    }

    @Test
    void lambdaIsTheDefaultAndNeedsNoAwsEnvironment() {
        runner.run(ctx -> {
            assertThat(ctx.getBean(DeepAnalysisInvoker.class)).isInstanceOf(LambdaDeepAnalysisInvoker.class);
            assertThat(ctx.getBean(LambdaClient.class).serviceClientConfiguration().region().id())
                    .isEqualTo("eu-central-1");
        });
    }

    @Test
    void regionComesFromProperty() {
        runner.withPropertyValues("app.deep-analysis.region=us-east-1").run(ctx ->
                assertThat(ctx.getBean(LambdaClient.class).serviceClientConfiguration().region().id())
                        .isEqualTo("us-east-1"));
    }

    @Test
    void lambdaClientHasTenSecondTimeoutAndNoSdkRetries() {
        runner.run(ctx -> {
            var override = ctx.getBean(LambdaClient.class).serviceClientConfiguration().overrideConfiguration();
            assertThat(override.apiCallTimeout()).contains(java.time.Duration.ofSeconds(10));
            assertThat(override.retryStrategy()).isPresent();
            assertThat(override.retryStrategy().get().maxAttempts()).isEqualTo(1);
        });
    }
}
