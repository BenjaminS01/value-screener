package com.valuescreener.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class ResearchSnapshotSecurityTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void adminCredentials(DynamicPropertyRegistry registry) {
        registry.add("app.admin.username", () -> "admin");
        registry.add("app.admin.password-hash", () -> new BCryptPasswordEncoder().encode("test-password"));
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void acceptsUnauthenticatedRead() throws Exception {
        mockMvc.perform(get("/api/research/snapshots"))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsUnauthenticatedWrite() throws Exception {
        mockMvc.perform(post("/api/research/snapshots")
                        .contentType("application/json")
                        .content("""
                                {"ticker":"AAPL","isin":"US0378331005","companyName":"Apple Inc.",
                                 "sector":"Information Technology","country":"USA",
                                 "businessDescription":"Designs and sells consumer electronics.",
                                 "findings":[]}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void acceptsAuthenticatedWrite() throws Exception {
        mockMvc.perform(post("/api/research/snapshots")
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic("admin", "test-password"))
                        .contentType("application/json")
                        .content("""
                                {"ticker":"AAPL","isin":"US0378331005","companyName":"Apple Inc.",
                                 "sector":"Information Technology","country":"USA",
                                 "businessDescription":"Designs and sells consumer electronics.",
                                 "findings":[]}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void upsertMergesAcrossTwoRealPassesThroughFullStack() throws Exception {
        mockMvc.perform(post("/api/research/snapshots")
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic("admin", "test-password"))
                        .contentType("application/json")
                        .content("""
                                {"ticker":"MSFT","isin":"US5949181045","companyName":"Microsoft Corporation",
                                 "sector":"Information Technology","country":"USA",
                                 "businessDescription":"Develops and licenses software and cloud services.",
                                 "findings":[{"criterionKey":"MOAT_ASSESSMENT",
                                 "claim":"Durable enterprise switching costs.",
                                 "asOfDate":"2026-08-01"}]}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/research/snapshots")
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic("admin", "test-password"))
                        .contentType("application/json")
                        .content("""
                                {"ticker":"MSFT","isin":"US5949181045","companyName":"Microsoft Corporation",
                                 "sector":"Information Technology","country":"USA",
                                 "businessDescription":"Develops and licenses software and cloud services.",
                                 "findings":[{"criterionKey":"PE_RATIO","numericValue":35.2,
                                 "claim":"Trailing P/E of 35.2.","asOfDate":"2026-08-02"}]}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/research/snapshots/US5949181045")
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic("admin", "test-password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.findings[0].claim").value("Durable enterprise switching costs."))
                .andExpect(jsonPath("$.findings[1].numericValue").value(35.2));
    }
}
