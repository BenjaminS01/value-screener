package com.valuescreener.deepanalysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvocationType;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LambdaDeepAnalysisInvokerTest {

    private static final String ARN = "arn:aws:lambda:eu-central-1:123456789012:function:secret-fn";
    private static final String OK = """
            {"status":"NOT_IMPLEMENTED","message":"Noch nicht implementiert","greeting":"Hallo von AWS Lambda",
             "functionName":"deep-analysis-demo","functionVersion":"$LATEST","region":"eu-central-1",
             "memoryLimitMb":512,"awsRequestId":"req-1","coldStart":true,"invokedAt":"2026-11-12T10:15:30.123Z"}
            """;

    private LambdaClient client;
    private LambdaDeepAnalysisInvoker invoker;

    @BeforeEach
    void setUp() {
        client = mock(LambdaClient.class);
        invoker = new LambdaDeepAnalysisInvoker(client, new ObjectMapper(), ARN);
    }

    private static InvokeResponse response(int status, String functionError, String body) {
        return InvokeResponse.builder().statusCode(status).functionError(functionError)
                .payload(SdkBytes.fromUtf8String(body)).build();
    }

    @Test
    void mapsSuccessPayload() {
        when(client.invoke(any(InvokeRequest.class))).thenReturn(response(200, null, OK));

        DeepAnalysisResult r = invoker.invoke("US0378331005");

        assertThat(r.status()).isEqualTo("NOT_IMPLEMENTED");
        assertThat(r.greeting()).isEqualTo("Hallo von AWS Lambda");
        assertThat(r.functionName()).isEqualTo("deep-analysis-demo");
        assertThat(r.functionVersion()).isEqualTo("$LATEST");
        assertThat(r.region()).isEqualTo("eu-central-1");
        assertThat(r.memoryLimitMb()).isEqualTo(512);
        assertThat(r.awsRequestId()).isEqualTo("req-1");
        assertThat(r.coldStart()).isTrue();
        assertThat(r.invokedAt()).isEqualTo(Instant.parse("2026-11-12T10:15:30.123Z"));
        assertThat(r.source()).isEqualTo(DeepAnalysisResult.Source.LAMBDA);
    }

    @Test
    void sendsNormalizedIsinAndFunctionName() {
        when(client.invoke(any(InvokeRequest.class))).thenReturn(response(200, null, OK));

        invoker.invoke("  us0378331005 ");

        ArgumentCaptor<InvokeRequest> cap = ArgumentCaptor.forClass(InvokeRequest.class);
        verify(client).invoke(cap.capture());
        assertThat(cap.getValue().functionName()).isEqualTo(ARN);
        assertThat(cap.getValue().invocationType()).isEqualTo(InvocationType.REQUEST_RESPONSE);
        assertThat(cap.getValue().payload().asUtf8String()).isEqualTo("{\"isin\":\"US0378331005\"}");
    }

    @Test
    void functionErrorIsUnavailable() {
        when(client.invoke(any(InvokeRequest.class))).thenReturn(response(200, "Unhandled", "{\"errorMessage\":\"x\"}"));
        assertThatThrownBy(() -> invoker.invoke("US0378331005"))
                .isInstanceOf(DeepAnalysisUnavailableException.class)
                .hasMessageNotContaining("arn:").hasMessageNotContaining("123456789012");
    }

    @Test
    void non200IsUnavailable() {
        when(client.invoke(any(InvokeRequest.class))).thenReturn(response(500, null, OK));
        assertThatThrownBy(() -> invoker.invoke("US0378331005"))
                .isInstanceOf(DeepAnalysisUnavailableException.class);
    }

    @Test
    void sdkExceptionIsUnavailableWithoutLeakingArn() {
        when(client.invoke(any(InvokeRequest.class)))
                .thenThrow(SdkClientException.create("Unable to load credentials for " + ARN));
        assertThatThrownBy(() -> invoker.invoke("US0378331005"))
                .isInstanceOf(DeepAnalysisUnavailableException.class)
                .hasMessageNotContaining("arn:").hasMessageNotContaining("123456789012");
    }

    @Test
    void garbagePayloadIsUnavailable() {
        when(client.invoke(any(InvokeRequest.class))).thenReturn(response(200, null, "not json"));
        assertThatThrownBy(() -> invoker.invoke("US0378331005"))
                .isInstanceOf(DeepAnalysisUnavailableException.class);
    }

    @Test
    void badInvokedAtIsUnavailable() {
        when(client.invoke(any(InvokeRequest.class))).thenReturn(response(200, null, OK.replace("2026-11-12T10:15:30.123Z", "yesterday")));
        assertThatThrownBy(() -> invoker.invoke("US0378331005"))
                .isInstanceOf(DeepAnalysisUnavailableException.class);
    }
}
