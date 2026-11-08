package com.valuescreener.security;

import com.valuescreener.portfolio.PortfolioController;
import com.valuescreener.portfolio.PortfolioService;
import com.valuescreener.research.CompanySnapshotService;
import com.valuescreener.research.ResearchIngestController;
import com.valuescreener.research.ResearchSyncController;
import com.valuescreener.research.StockGroupService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static com.valuescreener.security.MachineTokenTestData.BODY;
import static com.valuescreener.security.MachineTokenTestData.TOKEN;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({ResearchIngestController.class, ResearchSyncController.class, PortfolioController.class})
@Import(SecuritySliceTestConfig.class)
class MachineTokenDisabledSecurityTest {

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
