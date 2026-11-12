package com.valuescreener.research;

import com.valuescreener.security.LoginThrottleWebConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = ResearchSyncController.class, excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE, classes = LoginThrottleWebConfig.class))
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = "app.research-sync.enabled=true")
class ResearchSyncControllerTest {

    private static final String URL = "/api/research/sync/snapshots";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CompanySnapshotService service;

    private static String body(String extra) {
        return """
                {"ticker":"AAPL","isin":"US0378331005","companyName":"Apple Inc.",
                 "country":"USA","groupKey":"SEMICONDUCTORS",%s
                 "businessDescription":"Designs and sells consumer electronics.","findings":[]}
                """.formatted(extra);
    }

    private static CompanySnapshotView sampleView() {
        return new CompanySnapshotView(1L, "AAPL", "US0378331005", "Apple Inc.", "USA", "x",
                new SnapshotGroupView("SEMICONDUCTORS", "Semiconductors", GroupSource.MANUAL), List.of(), null, true);
    }

    @Test
    void syncAppliesManualSourceFromBody() throws Exception {
        when(service.upsert(any(), any(), any())).thenReturn(sampleView());

        mockMvc.perform(post(URL).contentType("application/json")
                        .content(body("\"groupSource\":\"MANUAL\",")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.group.source").value("MANUAL"));

        verify(service).upsert(any(), eq(GroupSource.MANUAL), eq(HistoryOrigin.SYNC));
    }

    @Test
    void syncDefaultsMissingSourceToAi() throws Exception {
        when(service.upsert(any(), any(), any())).thenReturn(sampleView());

        mockMvc.perform(post(URL).contentType("application/json").content(body("")))
                .andExpect(status().isOk());

        verify(service).upsert(any(), eq(GroupSource.AI), eq(HistoryOrigin.SYNC));
    }

    @Test
    void syncUnknownGroupIs422() throws Exception {
        when(service.upsert(any(), any(), any())).thenThrow(new UnknownGroupException("NOPE"));

        mockMvc.perform(post(URL).contentType("application/json").content(body("")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("Unknown group key: NOPE"));
    }

    @Test
    void syncRejectsInvalidBodyWithJsonError() throws Exception {
        mockMvc.perform(post(URL).contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid request"));
    }
}
