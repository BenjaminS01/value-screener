package com.valuescreener.deepanalysis;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LocalStubDeepAnalysisInvokerTest {

    private final Instant now = Instant.parse("2026-11-12T10:00:00Z");
    private final LocalStubDeepAnalysisInvoker invoker =
            new LocalStubDeepAnalysisInvoker(Clock.fixed(now, ZoneOffset.UTC));

    @Test
    void answersAsClearlyLabelledSimulation() {
        DeepAnalysisResult r = invoker.invoke(" us0378331005 ");

        assertThat(r.source()).isEqualTo(DeepAnalysisResult.Source.LOCAL_STUB);
        assertThat(r.status()).isEqualTo("NOT_IMPLEMENTED");
        assertThat(r.message()).isEqualTo(
                "Not implemented yet – a deep analysis for US0378331005 will appear here later.");
        assertThat(r.greeting()).isEqualTo("Local simulation – no AWS Lambda was called");
        assertThat(r.functionName()).isEqualTo("local-stub");
        assertThat(r.functionVersion()).isEqualTo("local");
        assertThat(r.region()).isEqualTo("local");
        assertThat(r.coldStart()).isFalse();
        assertThat(r.invokedAt()).isEqualTo(now);
        assertThat(UUID.fromString(r.awsRequestId())).isNotNull();
    }

    @Test
    void requestIdIsFreshEachCall() {
        assertThat(invoker.invoke("US0378331005").awsRequestId())
                .isNotEqualTo(invoker.invoke("US0378331005").awsRequestId());
    }
}
