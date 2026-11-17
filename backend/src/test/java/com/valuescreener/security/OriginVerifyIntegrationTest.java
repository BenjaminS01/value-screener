package com.valuescreener.security;

import com.valuescreener.persistence.DynamoTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Full context with the origin secret set: the filter sits in front of Spring Security on the servlet path. */
@SpringBootTest
@AutoConfigureMockMvc
class OriginVerifyIntegrationTest extends DynamoTestSupport {

    private static final String SECRET = "test-secret";

    @Autowired
    private MockMvc mvc;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.dynamodb.table-name", DynamoTestSupport::freshTableName);
        registry.add("app.admin.username", () -> "admin");
        registry.add("app.admin.password-hash", () -> new BCryptPasswordEncoder().encode("test-password"));
        registry.add("app.security.origin-verify.secret", () -> SECRET);
        registry.add("app.security.origin-verify.required", () -> "true");
    }

    @Test
    void loginWithoutHeaderIs403() throws Exception {
        var result = mvc.perform(post("/api/auth/login").contentType("application/json")
                .content("{\"username\":\"admin\",\"password\":\"x\"}")).andExpect(status().isForbidden()).andReturn();
        assertThat(result.getResponse().getContentAsString()).isEmpty();
    }

    @Test
    void protectedGetWithoutHeaderIs403() throws Exception {
        mvc.perform(get("/api/portfolio")).andExpect(status().isForbidden());
    }

    @Test
    void correctHeaderReachesNormalBehaviour() throws Exception {
        mvc.perform(get("/api/portfolio").header("X-Origin-Verify", SECRET)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/csrf").header("X-Origin-Verify", SECRET)).andExpect(status().isOk());
    }
}
