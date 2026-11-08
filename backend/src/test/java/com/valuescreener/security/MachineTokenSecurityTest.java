package com.valuescreener.security;

import com.valuescreener.portfolio.PortfolioController;
import com.valuescreener.portfolio.PortfolioService;
import com.valuescreener.research.CompanySnapshotService;
import com.valuescreener.research.CompanySnapshotView;
import com.valuescreener.research.ResearchIngestController;
import com.valuescreener.research.ResearchSyncController;
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

class MachineTokenSecurityTest {

    static final String TOKEN = "test-ingest-token";
    /** SHA-256 of "test-ingest-token". */
    static final String TOKEN_HASH = "12c9d8eb671641ca114db94b4bd7cea966626f0d9492a996808b1c6ee8ad6739";
    /** SHA-256 of "test-sync-token". */
    static final String SYNC_TOKEN_HASH = "a2f6fa2e108788e52648c1ff4d7c11c54e7f3909d4534b9b9063668f913f476b";
    static final String BODY = """
            {"ticker":"AAPL","isin":"US0378331005","companyName":"Apple Inc.",
             "country":"USA","groupKey":"SEMICONDUCTORS",
             "businessDescription":"Designs consumer electronics.","findings":[]}
            """;
    static final String UNAUTHENTICATED_JSON = "{\"error\":\"Authentication required\"}";

    @WebMvcTest({ResearchIngestController.class, PortfolioController.class})
    @Import(SecuritySliceTestConfig.class)
    @TestPropertySource(properties = {
            "app.research-ingest.enabled=true",
            "app.research-ingest.token-hash=" + TOKEN_HASH})
    static class IngestEnabled {

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

    @WebMvcTest({ResearchIngestController.class, ResearchSyncController.class, PortfolioController.class})
    @Import(SecuritySliceTestConfig.class)
    @TestPropertySource(properties = {
            "app.research-ingest.enabled=true",
            "app.research-ingest.token-hash=" + TOKEN_HASH,
            "app.research-sync.enabled=true",
            "app.research-sync.token-hash=" + SYNC_TOKEN_HASH})
    static class IngestAndSyncEnabled {

        @Autowired
        private MockMvc mockMvc;

        @MockBean
        private CompanySnapshotService snapshotService;

        @MockBean
        private StockGroupService groupService;

        @MockBean
        private PortfolioService portfolioService;

        @Test
        void syncTokenCanImport() throws Exception {
            when(snapshotService.upsert(any(), any())).thenReturn(new CompanySnapshotView(
                    1L, "AAPL", "US0378331005", "Apple Inc.", "USA", "x", null, List.of()));
            mockMvc.perform(post("/api/research/sync/snapshots")
                            .header("Authorization", "Bearer test-sync-token")
                            .contentType("application/json").content(BODY))
                    .andExpect(status().isOk());
        }

        @Test
        void ingestTokenCannotImport() throws Exception {
            mockMvc.perform(post("/api/research/sync/snapshots")
                            .header("Authorization", "Bearer " + TOKEN)
                            .contentType("application/json").content(BODY))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void syncTokenCannotUseIngest() throws Exception {
            mockMvc.perform(get("/api/research/ingest/groups")
                            .header("Authorization", "Bearer test-sync-token"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void ingestTokenIs401OnEnabledSyncPath() throws Exception {
            mockMvc.perform(post("/api/research/sync/snapshots")
                            .header("Authorization", "Bearer " + TOKEN)
                            .contentType("application/json").content(BODY))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().json(UNAUTHENTICATED_JSON));
        }

        @Test
        void syncTokenIs401OnIngestPath() throws Exception {
            mockMvc.perform(post("/api/research/ingest/snapshots")
                            .header("Authorization", "Bearer test-sync-token")
                            .contentType("application/json").content(BODY))
                    .andExpect(status().isUnauthorized());
        }
    }

    @WebMvcTest({PortfolioController.class})
    @Import(SecuritySliceTestConfig.class)
    static class IngestDisabled {

        @Autowired
        private MockMvc mockMvc;

        @MockBean
        private CompanySnapshotService snapshotService;

        @MockBean
        private StockGroupService groupService;

        @MockBean
        private PortfolioService portfolioService;

        @Test
        void ingestPathIs404EvenAnonymous() throws Exception {
            mockMvc.perform(post("/api/research/ingest/snapshots")
                            .contentType("application/json").content(BODY))
                    .andExpect(status().isNotFound());
        }

        @Test
        void ingestPathIs404EvenWithTokenHeader() throws Exception {
            mockMvc.perform(post("/api/research/ingest/snapshots")
                            .header("Authorization", "Bearer " + TOKEN)
                            .contentType("application/json").content(BODY))
                    .andExpect(status().isNotFound());
        }

        @Test
        void syncPathIs404EvenAnonymous() throws Exception {
            mockMvc.perform(post("/api/research/sync/snapshots")
                            .contentType("application/json").content(BODY))
                    .andExpect(status().isNotFound());
        }
    }
}
