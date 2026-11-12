package com.valuescreener.research;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = CompanySnapshotController.class, excludeFilters = @org.springframework.context.annotation.ComponentScan.Filter(
        type = org.springframework.context.annotation.FilterType.ASSIGNABLE_TYPE,
        classes = com.valuescreener.security.LoginThrottleWebConfig.class))
@AutoConfigureMockMvc(addFilters = false)
class CompanySnapshotControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CompanySnapshotService service;

    private static CompanySnapshotView sampleView() {
        FindingView peRatio = new FindingView(ResearchCriterion.PE_RATIO, new BigDecimal("28.0"), null,
                "Trailing P/E of 28.0.", "https://example.com/aapl-key-stats", LocalDate.of(2026, 8, 1), false);
        return new CompanySnapshotView(
                1L, "AAPL", "US0378331005", "Apple Inc.", "USA",
                "Designs and sells consumer electronics.",
                new SnapshotGroupView("SEMICONDUCTORS", "Semiconductors", GroupSource.AI), List.of(peRatio),
                LocalDate.of(2026, 8, 1), false);
    }

    @Test
    void listsAllSnapshots() throws Exception {
        when(service.list(null)).thenReturn(List.of(sampleView()));

        mockMvc.perform(get("/api/research/snapshots"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ticker").value("AAPL"));
    }

    @Test
    void listPassesGroupFilter() throws Exception {
        when(service.list("none")).thenReturn(List.of(sampleView()));

        mockMvc.perform(get("/api/research/snapshots").param("group", "none"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].isin").value("US0378331005"));
    }

    @Test
    void getsSnapshotByIsin() throws Exception {
        when(service.getByIsin("US0378331005")).thenReturn(sampleView());

        mockMvc.perform(get("/api/research/snapshots/US0378331005"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isin").value("US0378331005"));
    }

    @Test
    void viewExposesDataAgeFields() throws Exception {
        when(service.getByIsin("US0378331005")).thenReturn(sampleView());

        mockMvc.perform(get("/api/research/snapshots/US0378331005"))
                .andExpect(jsonPath("$.lastResearchedAt").value("2026-08-01"))
                .andExpect(jsonPath("$.stale").value(false))
                .andExpect(jsonPath("$.findings[0].stale").value(false));
    }

    @Test
    void viewHasGroupAndNoSector() throws Exception {
        when(service.getByIsin("US0378331005")).thenReturn(sampleView());

        mockMvc.perform(get("/api/research/snapshots/US0378331005"))
                .andExpect(jsonPath("$.group.key").value("SEMICONDUCTORS"))
                .andExpect(jsonPath("$.group.name").value("Semiconductors"))
                .andExpect(jsonPath("$.group.source").value("AI"))
                .andExpect(jsonPath("$.sector").doesNotExist());
    }

    @Test
    void gettingUnknownIsinReturnsNotFound() throws Exception {
        doThrow(new CompanySnapshotNotFoundException("US0378331005"))
                .when(service).getByIsin("US0378331005");

        mockMvc.perform(get("/api/research/snapshots/US0378331005"))
                .andExpect(status().isNotFound());
    }

    @Test
    void putGroupReturnsUpdatedView() throws Exception {
        when(service.assignGroupManually("US0378331005", "SEMICONDUCTORS")).thenReturn(sampleView());

        mockMvc.perform(put("/api/research/snapshots/US0378331005/group")
                        .contentType("application/json").content("{\"groupKey\":\"SEMICONDUCTORS\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.group.key").value("SEMICONDUCTORS"));
    }

    @Test
    void putGroupWithPaddedLowercaseIsinReachesServiceWhichNormalizes() throws Exception {
        when(service.assignGroupManually(" us0378331005 ", "SEMICONDUCTORS")).thenReturn(sampleView());

        mockMvc.perform(put("/api/research/snapshots/ us0378331005 /group")
                        .contentType("application/json").content("{\"groupKey\":\"SEMICONDUCTORS\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isin").value("US0378331005"));
    }

    @Test
    void putGroupWithBlankKeyIs400() throws Exception {
        mockMvc.perform(put("/api/research/snapshots/US0378331005/group")
                        .contentType("application/json").content("{\"groupKey\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid request"));
    }

    @Test
    void putGroupForUnknownSnapshotIs404() throws Exception {
        doThrow(new CompanySnapshotNotFoundException("US0378331005"))
                .when(service).assignGroupManually("US0378331005", "BANKS");

        mockMvc.perform(put("/api/research/snapshots/US0378331005/group")
                        .contentType("application/json").content("{\"groupKey\":\"BANKS\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Snapshot not found"));
    }

    @Test
    void unknownGroupIs422() throws Exception {
        doThrow(new UnknownGroupException("NOPE"))
                .when(service).assignGroupManually("US0378331005", "NOPE");

        mockMvc.perform(put("/api/research/snapshots/US0378331005/group")
                        .contentType("application/json").content("{\"groupKey\":\"NOPE\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("Unknown group key: NOPE"));
    }
}
