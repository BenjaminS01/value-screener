package com.valuescreener.lambda;

import com.amazonaws.serverless.proxy.model.HttpApiV2HttpContext;
import com.amazonaws.serverless.proxy.model.HttpApiV2ProxyRequest;
import com.amazonaws.serverless.proxy.model.HttpApiV2ProxyRequestContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.valuescreener.persistence.DynamoTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drives the real handler with HTTP API v2 events against DynamoDB Local. The handler initialises Spring in
 * a static block, so the configuration is passed as system properties that are set before the class loads
 * (Spring Boot reads system properties as a property source). No production code is test-aware.
 */
class StreamLambdaHandlerTest {

    private static final String SECRET = "test-secret";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final List<String> PROPERTIES = List.of(
            "app.dynamodb.endpoint", "app.dynamodb.create-table", "app.dynamodb.table-name",
            "app.admin.username", "app.admin.password-hash", "app.security.origin-verify.secret");

    @BeforeAll
    static void configure() {
        System.setProperty("app.dynamodb.endpoint", DynamoTestSupport.endpoint());
        System.setProperty("app.dynamodb.create-table", "true");
        System.setProperty("app.dynamodb.table-name", DynamoTestSupport.freshTableName());
        System.setProperty("app.admin.username", "admin");
        System.setProperty("app.admin.password-hash", new BCryptPasswordEncoder().encode("test-password"));
        System.setProperty("app.security.origin-verify.secret", SECRET);
    }

    @AfterAll
    static void cleanUp() {
        PROPERTIES.forEach(System::clearProperty);
    }

    private static JsonNode invoke(String path, String originHeader) throws Exception {
        return invoke("GET", path, originHeader);
    }

    private static JsonNode invoke(String method, String path, String originHeader) throws Exception {
        HttpApiV2ProxyRequest event = new HttpApiV2ProxyRequest();
        event.setVersion("2.0");
        event.setRawPath(path);
        event.setRawQueryString("");
        event.setBody(null);
        HttpApiV2ProxyRequestContext context = new HttpApiV2ProxyRequestContext();
        context.setHttp(new HttpApiV2HttpContext());
        context.getHttp().setMethod(method);
        context.getHttp().setPath(path);
        context.getHttp().setProtocol("HTTP/1.1");
        context.getHttp().setSourceIp("203.0.113.7");
        context.setStage("$default");
        event.setRequestContext(context);
        java.util.Map<String, String> headers = new java.util.HashMap<>();
        headers.put("accept", "application/json");
        if (originHeader != null) {
            headers.put("x-origin-verify", originHeader);
        }
        event.setHeaders(headers);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new StreamLambdaHandler().handleRequest(
                new ByteArrayInputStream(MAPPER.writeValueAsBytes(event)), out, null);
        return MAPPER.readTree(out.toByteArray());
    }

    @Test
    void csrfEndpointReturnsToken() throws Exception {
        JsonNode response = invoke("/api/auth/csrf", SECRET);

        assertThat(response.get("statusCode").asInt()).isEqualTo(200);
        JsonNode body = MAPPER.readTree(response.get("body").asText());
        assertThat(body.get("token").asText()).isNotBlank();
    }

    @Test
    void portfolioWithoutSessionIs401() throws Exception {
        assertThat(invoke("/api/portfolio", SECRET).get("statusCode").asInt()).isEqualTo(401);
    }

    @Test
    void originFilterRunsInFrontOfSecurityInLambda() throws Exception {
        JsonNode missing = invoke("/api/auth/csrf", null);
        assertThat(missing.get("statusCode").asInt()).isEqualTo(403);
        assertThat(missing.path("body").asText("")).isEmpty();

        assertThat(invoke("/api/auth/csrf", "wrong").get("statusCode").asInt()).isEqualTo(403);
    }

    /** Security alone would answer 401 here; 403 proves the origin filter runs before it. */
    @Test
    void originFilterRunsBeforeSecurityOnProtectedAndLoginPaths() throws Exception {
        assertThat(invoke("/api/portfolio", null).get("statusCode").asInt()).isEqualTo(403);
        assertThat(invoke("POST", "/api/auth/login", null).get("statusCode").asInt()).isEqualTo(403);
    }
}
