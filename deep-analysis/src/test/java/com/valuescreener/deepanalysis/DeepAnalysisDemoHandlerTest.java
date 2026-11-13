package com.valuescreener.deepanalysis;

import static org.junit.jupiter.api.Assertions.*;

import com.amazonaws.services.lambda.runtime.ClientContext;
import com.amazonaws.services.lambda.runtime.CognitoIdentity;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class DeepAnalysisDemoHandlerTest {

    @Inject
    @Named("deep-analysis-demo")
    DeepAnalysisDemoHandler handler;

    @BeforeEach
    void freshExecutionEnvironment() {
        DeepAnalysisDemoHandler.resetColdStartForTest();
    }

    @Test
    void validIsinReturnsNotImplementedWithContextMetadata() {
        DeepAnalysisResponse r = handler.handleRequest(new DeepAnalysisRequest("US0378331005"), new TestContext());

        assertEquals("NOT_IMPLEMENTED", r.status());
        assertEquals("Noch nicht implementiert – hier wird künftig eine Tiefenanalyse für US0378331005 ausgegeben.",
                r.message());
        assertEquals("Hallo von AWS Lambda", r.greeting());
        assertEquals("deep-analysis-demo", r.functionName());
        assertEquals("7", r.functionVersion());
        assertEquals(512, r.memoryLimitMb());
        assertEquals("req-1", r.awsRequestId());
        assertNotNull(r.region());
        assertDoesNotThrow(() -> Instant.parse(r.invokedAt()));
    }

    @Test
    void paddedLowercaseIsinIsNormalized() {
        DeepAnalysisResponse r = handler.handleRequest(new DeepAnalysisRequest(" us0378331005 "), new TestContext());

        assertEquals("NOT_IMPLEMENTED", r.status());
        assertTrue(r.message().contains("US0378331005"));
    }

    @Test
    void invalidIsinReturnsInvalidRequestWithoutException() {
        DeepAnalysisResponse r = handler.handleRequest(new DeepAnalysisRequest("X"), new TestContext());

        assertEquals("INVALID_REQUEST", r.status());
        assertEquals("Ungültige ISIN", r.message());
    }

    @Test
    void nullRequestAndNullIsinAreInvalid() {
        assertEquals("INVALID_REQUEST", handler.handleRequest(null, new TestContext()).status());
        assertEquals("INVALID_REQUEST", handler.handleRequest(new DeepAnalysisRequest(null), new TestContext()).status());
    }

    @Test
    void serializedResponseLeaksNeitherArnNorAccountId() throws Exception {
        DeepAnalysisResponse r = handler.handleRequest(new DeepAnalysisRequest("US0378331005"), new TestContext());
        String json = new ObjectMapper().writeValueAsString(r);

        assertFalse(json.contains("arn:"), json);
        assertFalse(json.contains("123456789012"), json);
    }

    @Test
    void serializedResponseHasExactlyTheTenFields() throws Exception {
        DeepAnalysisResponse r = handler.handleRequest(new DeepAnalysisRequest("US0378331005"), new TestContext());
        var node = new ObjectMapper().valueToTree(r);
        var names = new java.util.TreeSet<String>();
        node.fieldNames().forEachRemaining(names::add);

        assertEquals(new java.util.TreeSet<>(java.util.List.of("status", "message", "greeting", "functionName",
                "functionVersion", "region", "memoryLimitMb", "awsRequestId", "coldStart", "invokedAt")), names);
    }

    @Test
    void coldStartIsTrueOnFirstCallAndFalseOnSecond() {
        DeepAnalysisResponse first = handler.handleRequest(new DeepAnalysisRequest("US0378331005"), new TestContext());
        DeepAnalysisResponse second = handler.handleRequest(new DeepAnalysisRequest("US0378331005"), new TestContext());

        assertTrue(first.coldStart());
        assertFalse(second.coldStart());
    }

    static class TestContext implements Context {
        public String getAwsRequestId() { return "req-1"; }
        public String getLogGroupName() { return "/aws/lambda/secret-group"; }
        public String getLogStreamName() { return "secret-stream"; }
        public String getFunctionName() { return "deep-analysis-demo"; }
        public String getFunctionVersion() { return "7"; }
        public String getInvokedFunctionArn() {
            return "arn:aws:lambda:eu-central-1:123456789012:function:deep-analysis-demo";
        }
        public CognitoIdentity getIdentity() { return null; }
        public ClientContext getClientContext() { return null; }
        public int getRemainingTimeInMillis() { return 1000; }
        public int getMemoryLimitInMB() { return 512; }
        public LambdaLogger getLogger() { return null; }
    }
}
