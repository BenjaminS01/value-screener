package com.valuescreener.research;

import com.valuescreener.security.SecuritySliceTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CompanySnapshotController.class)
@Import(SecuritySliceTestConfig.class)
class CompanySnapshotHistoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CompanySnapshotService service;

    private static SnapshotHistoryView sample() {
        return new SnapshotHistoryView(
                List.of(new SnapshotHistoryView.FindingHistoryEntry(ResearchCriterion.PE_RATIO,
                                new BigDecimal("28.0"), null, "Newer.", null, LocalDate.of(2026, 8, 1),
                                Instant.parse("2026-11-02T00:00:00Z"), HistoryOrigin.INGEST),
                        new SnapshotHistoryView.FindingHistoryEntry(ResearchCriterion.PE_RATIO,
                                new BigDecimal("25.0"), null, "Older.", null, LocalDate.of(2026, 2, 1),
                                Instant.parse("2026-08-01T00:00:00Z"), HistoryOrigin.SYNC)),
                List.of(new SnapshotHistoryView.GroupHistoryEntry(null, null,
                        new StockGroupRef("BANKS", "Banks"), GroupSource.MANUAL,
                        Instant.parse("2026-11-03T00:00:00Z"), HistoryOrigin.MANUAL)));
    }

    @Test
    void userGetsHistory() throws Exception {
        when(service.history("US0378331005")).thenReturn(sample());

        mockMvc.perform(get("/api/research/snapshots/US0378331005/history").with(user("alice").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.findings[0].claim").value("Newer."))
                .andExpect(jsonPath("$.findings[1].claim").value("Older."))
                .andExpect(jsonPath("$.findings[0].origin").value("INGEST"))
                .andExpect(jsonPath("$.groups[0].previousGroup").doesNotExist())
                .andExpect(jsonPath("$.groups[0].newGroup.key").value("BANKS"))
                .andExpect(jsonPath("$.groups[0].newSource").value("MANUAL"));
    }

    @Test
    void adminGetsHistory() throws Exception {
        when(service.history("US0378331005")).thenReturn(sample());

        mockMvc.perform(get("/api/research/snapshots/US0378331005/history").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groups[0].origin").value("MANUAL"));
    }

    @Test
    void unknownIsinIs404() throws Exception {
        when(service.history("US0000000000")).thenThrow(new CompanySnapshotNotFoundException("US0000000000"));

        mockMvc.perform(get("/api/research/snapshots/US0000000000/history").with(user("alice").roles("USER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Snapshot not found"));
    }

    @Test
    void anonymousIs401() throws Exception {
        mockMvc.perform(get("/api/research/snapshots/US0378331005/history"))
                .andExpect(status().isUnauthorized());
    }
}
