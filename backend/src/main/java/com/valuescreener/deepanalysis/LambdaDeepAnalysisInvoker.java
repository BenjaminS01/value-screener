package com.valuescreener.deepanalysis;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvocationType;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;

/** Calls the deep-analysis Lambda synchronously through the AWS SDK. */
public class LambdaDeepAnalysisInvoker implements DeepAnalysisInvoker {

    private static final Logger log = LoggerFactory.getLogger(LambdaDeepAnalysisInvoker.class);
    private static final String UNAVAILABLE = "Deep analysis unavailable";

    private record Payload(String status, String message, String greeting, String functionName,
                           String functionVersion, String region, int memoryLimitMb,
                           String awsRequestId, boolean coldStart, String invokedAt) {}

    private final LambdaClient client;
    private final ObjectMapper mapper;
    private final String functionName;

    public LambdaDeepAnalysisInvoker(LambdaClient client, ObjectMapper mapper, String functionName) {
        this.client = client;
        this.mapper = mapper;
        this.functionName = functionName;
    }

    @Override
    public DeepAnalysisResult invoke(String isin) {
        String normalized = isin.trim().toUpperCase(Locale.ROOT);
        try {
            InvokeRequest request = InvokeRequest.builder()
                    .functionName(functionName)
                    .invocationType(InvocationType.REQUEST_RESPONSE)
                    .payload(SdkBytes.fromUtf8String(mapper.writeValueAsString(Map.of("isin", normalized))))
                    .build();
            InvokeResponse response = client.invoke(request);
            if (response.functionError() != null) {
                log.warn("Deep-analysis Lambda reported a function error: {}", response.functionError());
                throw new DeepAnalysisUnavailableException(UNAVAILABLE);
            }
            if (response.statusCode() == null || response.statusCode() != 200) {
                log.warn("Deep-analysis Lambda returned HTTP status {}", response.statusCode());
                throw new DeepAnalysisUnavailableException(UNAVAILABLE);
            }
            Payload p = mapper.readerFor(Payload.class)
                    .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(response.payload().asUtf8String());
            return new DeepAnalysisResult(p.status(), p.message(), p.greeting(), p.functionName(),
                    p.functionVersion(), p.region(), p.memoryLimitMb(), p.awsRequestId(), p.coldStart(),
                    Instant.parse(p.invokedAt()), DeepAnalysisResult.Source.LAMBDA);
        } catch (DeepAnalysisUnavailableException e) {
            throw e;
        } catch (SdkException e) {
            String requestId = e instanceof AwsServiceException a ? a.requestId() : null;
            log.warn("Deep-analysis Lambda call failed (requestId={}): {}", requestId, sanitize(e.getMessage()));
            throw new DeepAnalysisUnavailableException(UNAVAILABLE);
        } catch (Exception e) {
            log.warn("Deep-analysis Lambda response unusable: {}", sanitize(e.getMessage()));
            throw new DeepAnalysisUnavailableException(UNAVAILABLE);
        }
    }

    /** Keeps the function name / ARN out of logs. */
    private String sanitize(String message) {
        if (message == null) {
            return "";
        }
        String s = message.replace(functionName, "<function>");
        return s.replaceAll("arn:\\S+", "<arn>");
    }
}
