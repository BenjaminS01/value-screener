package com.valuescreener.deepanalysis;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;

/**
 * Proves the Quarkus wiring: in test mode quarkus-amazon-lambda starts the mock Lambda event server
 * (default test port 8081); a POST there is delivered to the configured handler like a real invocation.
 */
@QuarkusTest
class DeepAnalysisLambdaWiringTest {

    @Test
    void quarkusInvokesHandlerWithJsonInput() throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:8081/"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{\"isin\":\"us0378331005\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode(), response.body());
        JsonNode json = new ObjectMapper().readTree(response.body());
        assertEquals("NOT_IMPLEMENTED", json.get("status").asText());
        assertTrue(json.get("message").asText().contains("US0378331005"));
        assertEquals("Hallo von AWS Lambda", json.get("greeting").asText());
        assertFalse(json.get("awsRequestId").asText().isBlank());
        assertFalse(response.body().contains("arn:"));
    }
}
