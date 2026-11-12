package com.valuescreener.security;

import com.valuescreener.portfolio.PortfolioController;
import com.valuescreener.portfolio.PortfolioService;
import com.valuescreener.research.CompanySnapshotService;
import com.valuescreener.research.CompanySnapshotView;
import com.valuescreener.research.ResearchIngestController;
import com.valuescreener.research.ResearchSyncController;
import com.valuescreener.research.StockGroupService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

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

@WebMvcTest({ResearchIngestController.class, ResearchSyncController.class, PortfolioController.class})
@Import(SecuritySliceTestConfig.class)
@TestPropertySource(properties = {
        "app.research-ingest.enabled=true",
        "app.research-ingest.token-hash=" + TOKEN_HASH,
        "app.research-sync.enabled=true",
        "app.research-sync.token-hash=" + SYNC_TOKEN_HASH})
class MachineTokenIngestAndSyncSecurityTest {

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
                1L, "AAPL", "US0378331005", "Apple Inc.", "USA", "x", null, List.of(), null, true));
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
