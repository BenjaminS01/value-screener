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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = ResearchIngestController.class, excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE, classes = LoginThrottleWebConfig.class))
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = "app.research-ingest.enabled=true")
class ResearchIngestControllerTest {

    private static final String URL = "/api/research/ingest/snapshots";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CompanySnapshotService service;

    private static String body(String isin, String findings) {
        return """
                {"ticker":"AAPL","isin":"%s","companyName":"Apple Inc.",
                 "sector":"Information Technology","country":"USA",
                 "businessDescription":"Designs and sells consumer electronics.",
                 "findings":[%s]}
                """.formatted(isin, findings);
    }

    private static String finding(String sourceUrl) {
        return """
                {"criterionKey":"PE_RATIO","numericValue":28.0,"claim":"Trailing P/E of 28.0.",
                 "sourceUrl":"%s","asOfDate":"2026-08-01"}""".formatted(sourceUrl);
    }

    private static CompanySnapshotView sampleView() {
        FindingView pe = new FindingView(ResearchCriterion.PE_RATIO, new BigDecimal("28.0"), null,
                "Trailing P/E of 28.0.", "https://example.com/x", LocalDate.of(2026, 8, 1));
        return new CompanySnapshotView(1L, "AAPL", "US0378331005", "Apple Inc.", "IT", "USA", "x", List.of(pe));
    }

    @Test
    void upsertsSnapshotOnValidRequest() throws Exception {
        when(service.upsert(any())).thenReturn(sampleView());
        mockMvc.perform(post(URL).contentType("application/json")
                        .content(body("US0378331005", finding("https://example.com/x"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticker").value("AAPL"));
    }

    @Test
    void rejectsFtpAndJavascriptSourceUrl() throws Exception {
        for (String url : List.of("ftp://example.com/file", "javascript:alert(1)")) {
            mockMvc.perform(post(URL).contentType("application/json")
                            .content(body("US0378331005", finding(url))))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void rejectsMalformedIsin() throws Exception {
        for (String isin : List.of("US037833100", "US03783310056", "1S0378331005", "US037833100A", "US-378331005")) {
            mockMvc.perform(post(URL).contentType("application/json").content(body(isin, "")))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void acceptsLowercaseIsin() throws Exception {
        when(service.upsert(any())).thenReturn(sampleView());
        mockMvc.perform(post(URL).contentType("application/json").content(body(" us0378331005 ", "")))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsBlankBusinessDescription() throws Exception {
        mockMvc.perform(post(URL).contentType("application/json")
                        .content(body("US0378331005", "").replace("Designs and sells consumer electronics.", "")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsClaimOverTwoThousandCharacters() throws Exception {
        String f = """
                {"criterionKey":"PE_RATIO","numericValue":28.0,"claim":"%s","asOfDate":"2026-08-01"}"""
                .formatted("a".repeat(2001));
        mockMvc.perform(post(URL).contentType("application/json").content(body("US0378331005", f)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsMoreThanFiftyFindings() throws Exception {
        String f = java.util.stream.IntStream.range(0, 51)
                .mapToObj(i -> """
                        {"criterionKey":"PE_RATIO","numericValue":28.0,"claim":"Finding %d.","asOfDate":"2026-08-01"}"""
                        .formatted(i))
                .reduce((a, b) -> a + "," + b).orElseThrow();
        mockMvc.perform(post(URL).contentType("application/json").content(body("US0378331005", f)))
                .andExpect(status().isBadRequest());
    }
}
