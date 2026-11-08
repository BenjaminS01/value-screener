package com.valuescreener.research;

import com.valuescreener.security.LoginThrottleWebConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
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
    void groupCreateDataIntegrityViolationReturns409WithoutLeakingMessage() throws Exception {
        when(groupService.create(any())).thenThrow(new DataIntegrityViolationException("insert into secret_table"));

        mockMvc.perform(post("/api/research/groups").contentType("application/json").content(GROUP_BODY))
                .andExpect(status().isConflict())
                .andExpect(content().json(CONFLICT_BODY, true));
    }

    @Test
    void groupCreateOptimisticLockReturns409() throws Exception {
        when(groupService.create(any())).thenThrow(new ObjectOptimisticLockingFailureException(StockGroup.class, 1L));

        mockMvc.perform(post("/api/research/groups").contentType("application/json").content(GROUP_BODY))
                .andExpect(status().isConflict())
                .andExpect(content().json(CONFLICT_BODY, true));
    }

    @Test
    void ingestDataIntegrityViolationReturns409() throws Exception {
        when(snapshotService.upsert(any(), any())).thenThrow(new DataIntegrityViolationException("duplicate key"));

        mockMvc.perform(post("/api/research/ingest/snapshots").contentType("application/json").content(INGEST_BODY))
                .andExpect(status().isConflict())
                .andExpect(content().json(CONFLICT_BODY, true));
    }

    @Test
    void ingestOptimisticLockReturns409() throws Exception {
        when(snapshotService.upsert(any(), any()))
                .thenThrow(new ObjectOptimisticLockingFailureException(CompanySnapshot.class, 1L));

        mockMvc.perform(post("/api/research/ingest/snapshots").contentType("application/json").content(INGEST_BODY))
                .andExpect(status().isConflict())
                .andExpect(content().json(CONFLICT_BODY, true));
    }
}
