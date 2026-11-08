package com.valuescreener.security;

import com.valuescreener.portfolio.PortfolioController;
import com.valuescreener.portfolio.PortfolioService;
import com.valuescreener.research.CompanySnapshotService;
import com.valuescreener.research.CompanySnapshotView;
import com.valuescreener.research.ResearchIngestController;
import com.valuescreener.research.StockGroupService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static com.valuescreener.security.MachineTokenTestData.BODY;
import static com.valuescreener.security.MachineTokenTestData.SYNC_TOKEN_HASH;
import static com.valuescreener.security.MachineTokenTestData.TOKEN;
import static com.valuescreener.security.MachineTokenTestData.TOKEN_HASH;
import static com.valuescreener.security.MachineTokenTestData.UNAUTHENTICATED_JSON;

@WebMvcTest({ResearchIngestController.class, PortfolioController.class})
@Import(SecuritySliceTestConfig.class)
@TestPropertySource(properties = {
        "app.research-ingest.enabled=true",
        "app.research-ingest.token-hash=" + TOKEN_HASH})
class MachineTokenIngestSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CompanySnapshotService snapshotService;

    @MockBean
    private StockGroupService groupService;

    @MockBean
    private PortfolioService portfolioService;

    @Test
    void validTokenCanPostSnapshot() throws Exception {
        when(snapshotService.upsert(any(), any())).thenReturn(new CompanySnapshotView(
                1L, "AAPL", "US0378331005", "Apple Inc.", "USA", "x", null, List.of()));
        mockMvc.perform(post("/api/research/ingest/snapshots")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType("application/json").content(BODY))
                .andExpect(status().isOk());
    }

    @Test
    void tokenCanReadGroupCatalogAndAnonymousCannot() throws Exception {
        mockMvc.perform(get("/api/research/ingest/groups").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/research/ingest/groups"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json(UNAUTHENTICATED_JSON));
    }

    @Test
    void ingestIsStatelessAndNeedsNoCsrf() throws Exception {
        // no CSRF token sent, and no session must be created
        MvcResult result = mockMvc.perform(post("/api/research/ingest/snapshots")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType("application/json").content(BODY))
                .andExpect(status().is2xxSuccessful())
                .andReturn();
        assertThat(result.getResponse().getHeader("Set-Cookie")).isNull();
        assertThat(result.getRequest().getSession(false)).isNull();
    }

    @Test
    void missingOrWrongTokenIs401Json() throws Exception {
        mockMvc.perform(post("/api/research/ingest/snapshots")
                        .contentType("application/json").content(BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json(UNAUTHENTICATED_JSON));
        mockMvc.perform(post("/api/research/ingest/snapshots")
                        .header("Authorization", "Bearer wrong-token")
                        .contentType("application/json").content(BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json(UNAUTHENTICATED_JSON));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Basic x", "bearer test-ingest-token", "BEARER test-ingest-token", "Bearer ",
            "Bearer", "Bearer  test-ingest-token", "Bearer a b", "Bearer test-ingest-token ",
            "Token test-ingest-token"})
    void malformedAuthorizationHeadersAre401(String header) throws Exception {
        mockMvc.perform(post("/api/research/ingest/snapshots")
                        .header("Authorization", header)
                        .contentType("application/json").content(BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json(UNAUTHENTICATED_JSON));
    }

    @Test
    void ingestTokenDoesNotOpenSessionEndpoints() throws Exception {
        mockMvc.perform(get("/api/portfolio").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ingestTokenDoesNotOpenSyncPath() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/research/sync/snapshots")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType("application/json").content(BODY))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isIn(401, 404);
    }
}
