package com.valuescreener.research.ingest;

import com.valuescreener.research.group.StockGroupController;
import com.valuescreener.research.group.StockGroupService;
import com.valuescreener.research.group.UnknownGroupException;
import com.valuescreener.research.snapshot.CompanySnapshotService;
import com.valuescreener.security.throttle.LoginThrottleWebConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import com.valuescreener.persistence.ConcurrentUpdateException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {StockGroupController.class, ResearchIngestController.class},
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = LoginThrottleWebConfig.class))
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = "app.research-ingest.enabled=true")
class ConflictResponseTest {

    private static final String CONFLICT_BODY = "{\"error\":\"Conflict, please reload\"}";
    private static final String GROUP_BODY = "{\"key\":\"BANKS\",\"name\":\"Banks\"}";
    private static final String INGEST_BODY = """
            {"ticker":"AAPL","isin":"US0378331005","companyName":"Apple Inc.","country":"USA",
             "groupKey":"SEMICONDUCTORS","businessDescription":"Designs electronics.","findings":[]}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StockGroupService groupService;

    @MockBean
    private CompanySnapshotService snapshotService;

    @Test
    void groupCreateConcurrentUpdateReturns409WithoutLeakingMessage() throws Exception {
        when(groupService.create(any())).thenThrow(new ConcurrentUpdateException("conditional write on secret_table failed", null));

        mockMvc.perform(post("/api/research/groups").contentType("application/json").content(GROUP_BODY))
                .andExpect(status().isConflict())
                .andExpect(content().json(CONFLICT_BODY, true));
    }

    @Test
    void ingestConcurrentUpdateReturns409WithSameBody() throws Exception {
        when(snapshotService.upsert(any(), any(), any()))
                .thenThrow(new ConcurrentUpdateException("Snapshot US0378331005 was changed concurrently", null));

        mockMvc.perform(post("/api/research/ingest/snapshots").contentType("application/json").content(INGEST_BODY))
                .andExpect(status().isConflict())
                .andExpect(content().json(CONFLICT_BODY, true));
    }

    @Test
    void ingestGroupDeletedMeanwhileReturns422() throws Exception {
        when(snapshotService.upsert(any(), any(), any())).thenThrow(new UnknownGroupException("SEMICONDUCTORS"));

        mockMvc.perform(post("/api/research/ingest/snapshots").contentType("application/json").content(INGEST_BODY))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().json("{\"error\":\"Unknown group key: SEMICONDUCTORS\"}", true));
    }
}
