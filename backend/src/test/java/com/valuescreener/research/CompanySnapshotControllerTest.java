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
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CompanySnapshotController.class)
@AutoConfigureMockMvc(addFilters = false)
class CompanySnapshotControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CompanySnapshotService service;

    private static CompanySnapshotView sampleView() {
        return new CompanySnapshotView(
                1L, "AAPL", "US0378331005", "Apple Inc.", "Information Technology", "USA",
                "Designs and sells consumer electronics.", "Strong brand moat.",
                "Regulatory risk in the EU; expansion opportunity in services.",
                new BigDecimal("28.0"), null, null, null, null, null, null, null,
                LocalDate.of(2026, 8, 1), Set.of("https://example.com/aapl-key-stats"),
                Set.of("peRatio", "moatNote", "opportunitiesAndRisksNote"));
    }

    @Test
    void upsertsSnapshotOnValidRequest() throws Exception {
        when(service.upsert(any())).thenReturn(sampleView());

        mockMvc.perform(post("/api/research/snapshots")
                        .contentType("application/json")
                        .content("""
                                {"ticker":"AAPL","isin":"US0378331005","companyName":"Apple Inc.",
                                 "sector":"Information Technology","country":"USA",
                                 "businessDescription":"Designs and sells consumer electronics.",
                                 "asOfDate":"2026-08-01"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticker").value("AAPL"));
    }

    @Test
    void rejectsUpsertWithBlankBusinessDescription() throws Exception {
        mockMvc.perform(post("/api/research/snapshots")
                        .contentType("application/json")
                        .content("""
                                {"ticker":"AAPL","isin":"US0378331005","companyName":"Apple Inc.",
                                 "sector":"Information Technology","country":"USA",
                                 "businessDescription":"","asOfDate":"2026-08-01"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listsAllSnapshots() throws Exception {
        when(service.listAll()).thenReturn(List.of(sampleView()));

        mockMvc.perform(get("/api/research/snapshots"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ticker").value("AAPL"));
    }

    @Test
    void getsSnapshotByIsin() throws Exception {
        when(service.getByIsin("US0378331005")).thenReturn(sampleView());

        mockMvc.perform(get("/api/research/snapshots/US0378331005"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isin").value("US0378331005"));
    }

    @Test
    void gettingUnknownIsinReturnsNotFound() throws Exception {
        doThrow(new CompanySnapshotNotFoundException("US0378331005"))
                .when(service).getByIsin("US0378331005");

        mockMvc.perform(get("/api/research/snapshots/US0378331005"))
                .andExpect(status().isNotFound());
    }
}
