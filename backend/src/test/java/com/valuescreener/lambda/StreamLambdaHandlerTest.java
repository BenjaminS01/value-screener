package com.valuescreener.lambda;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.valuescreener.persistence.DynamoTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drives the real handler with HTTP API events in payload format 1.0 (as API Gateway sends them) against
 * DynamoDB Local. The handler initialises Spring in a static block, so the configuration is passed as system
 * properties that are set before the class loads (Spring Boot reads system properties as a property source).
 * No production code is test-aware.
 *
 * <p>Format 1.0 because API Gateway reads response headers of format 2.0 only from the single-value
 * {@code headers} map: the adapter keeps just the first value per name there, so of two Set-Cookie headers
 * (session + CSRF on login) one was dropped. Format 1.0 reads {@code multiValueHeaders}.
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
        Map<String, String> headers = new LinkedHashMap<>();
        if (originHeader != null) {
            headers.put("X-Origin-Verify", originHeader);
        }
        return invoke("GET", path, headers, null);
    }

    /** An HTTP API event in payload format 1.0, built as JSON like API Gateway sends it. */
    private static JsonNode invoke(String method, String path, Map<String, String> headers, String body)
            throws Exception {
        Map<String, String> all = new LinkedHashMap<>();
        all.put("Accept", "application/json");
        all.putAll(headers);

        ObjectNode event = MAPPER.createObjectNode();
        event.put("version", "1.0");
        event.put("resource", "/api/{proxy+}");
        event.put("path", path);
        event.put("httpMethod", method);
        ObjectNode single = event.putObject("headers");
        ObjectNode multi = event.putObject("multiValueHeaders");
        all.forEach((name, value) -> {
            single.put(name, value);
            multi.putArray(name).add(value);
        });
        ObjectNode context = event.putObject("requestContext");
        context.put("stage", "$default");
        context.put("httpMethod", method);
        context.put("path", path);
        context.putObject("identity").put("sourceIp", "203.0.113.7");
        if (body == null) {
            event.putNull("body");
        } else {
            event.put("body", body);
        }
        event.put("isBase64Encoded", false);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new StreamLambdaHandler().handleRequest(new ByteArrayInputStream(MAPPER.writeValueAsBytes(event)), out, null);
        return MAPPER.readTree(out.toByteArray());
    }

    /** All Set-Cookie values API Gateway sees in a format 1.0 response, as name=value (attributes cut off). */
    private static Map<String, String> setCookies(JsonNode response) {
        Map<String, String> cookies = new LinkedHashMap<>();
        response.path("multiValueHeaders").fields().forEachRemaining(header -> {
            if (header.getKey().equalsIgnoreCase("Set-Cookie")) {
                header.getValue().forEach(v -> {
                    String pair = v.asText().split(";", 2)[0];
                    cookies.put(pair.substring(0, pair.indexOf('=')), pair.substring(pair.indexOf('=') + 1));
                });
            }
        });
        return cookies;
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
        assertThat(invoke("POST", "/api/auth/login", Map.of(), null).get("statusCode").asInt()).isEqualTo(403);
    }

    /** The live bug of 2026-11-23: after login the browser got only XSRF-TOKEN, never JSESSIONID. */
    @Test
    void loginDeliversSessionAndCsrfCookiesAndTheSessionWorks() throws Exception {
        String token = MAPPER.readTree(invoke("/api/auth/csrf", SECRET).get("body").asText()).get("token").asText();

        Map<String, String> loginHeaders = new LinkedHashMap<>();
        loginHeaders.put("X-Origin-Verify", SECRET);
        loginHeaders.put("Content-Type", "application/json");
        loginHeaders.put("Cookie", "XSRF-TOKEN=" + token);
        loginHeaders.put("X-XSRF-TOKEN", token);
        JsonNode login = invoke("POST", "/api/auth/login", loginHeaders,
                "{\"username\":\"admin\",\"password\":\"test-password\"}");

        assertThat(login.get("statusCode").asInt()).isEqualTo(200);
        Map<String, String> cookies = setCookies(login);
        assertThat(cookies).containsKeys("JSESSIONID", "XSRF-TOKEN");

        Map<String, String> meHeaders = new LinkedHashMap<>();
        meHeaders.put("X-Origin-Verify", SECRET);
        meHeaders.put("Cookie", "JSESSIONID=" + cookies.get("JSESSIONID"));
        JsonNode me = invoke("GET", "/api/auth/me", meHeaders, null);
        assertThat(me.get("statusCode").asInt()).isEqualTo(200);
        assertThat(MAPPER.readTree(me.get("body").asText()).get("username").asText()).isEqualTo("admin");
    }
}
