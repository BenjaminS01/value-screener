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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class PortfolioSecurityTest {

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
    void allowsUnauthenticatedReadOfPublicPortfolio() throws Exception {
        mockMvc.perform(get("/api/portfolio/public"))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsUnauthenticatedWrite() throws Exception {
        mockMvc.perform(post("/api/portfolio")
                        .contentType("application/json")
                        .content("""
                                {"ticker":"AAPL","isin":"US0378331005","companyName":"Apple Inc.","quantity":10,"entryPrice":150.00,"purchaseDate":"2026-01-15"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void acceptsAuthenticatedWrite() throws Exception {
        mockMvc.perform(post("/api/portfolio")
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic("admin", "test-password"))
                        .contentType("application/json")
                        .content("""
                                {"ticker":"AAPL","isin":"US0378331005","companyName":"Apple Inc.","quantity":10,"entryPrice":150.00,"purchaseDate":"2026-01-15"}
                                """))
                .andExpect(status().isCreated());
    }
}
