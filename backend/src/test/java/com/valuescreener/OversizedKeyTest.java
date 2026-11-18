package com.valuescreener;

import com.valuescreener.portfolio.PortfolioController;
import com.valuescreener.portfolio.PortfolioPositionRepository;
import com.valuescreener.portfolio.PortfolioService;
import com.valuescreener.portfolio.PurchaseWriter;
import com.valuescreener.research.snapshot.CompanySnapshotController;
import com.valuescreener.research.snapshot.CompanySnapshotRepository;
import com.valuescreener.research.snapshot.CompanySnapshotService;
import com.valuescreener.thesis.InvestmentThesisController;
import com.valuescreener.thesis.InvestmentThesisRepository;
import com.valuescreener.thesis.InvestmentThesisService;
import com.valuescreener.research.history.ResearchFindingHistoryRepository;
import com.valuescreener.research.snapshot.ResearchFreshness;
import com.valuescreener.research.group.StockGroupController;
import com.valuescreener.research.history.StockGroupHistoryRepository;
import com.valuescreener.research.group.StockGroupLookup;
import com.valuescreener.research.group.StockGroupRepository;
import com.valuescreener.research.group.StockGroupService;
import com.valuescreener.security.LoginThrottleWebConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Real controllers and services, mocked repositories: an ISIN or group key that can never exist (here 2000
 * characters, DynamoDB allows 1024 bytes per key) must get the status codes it had before the DynamoDB
 * migration (404 unknown, 400 invalid, 422 unknown group) without ever reaching a repository.
 */
@WebMvcTest(controllers = {CompanySnapshotController.class, StockGroupController.class, PortfolioController.class,
        InvestmentThesisController.class},
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = LoginThrottleWebConfig.class))
@AutoConfigureMockMvc(addFilters = false)
@Import({CompanySnapshotService.class, StockGroupService.class, PortfolioService.class,
        InvestmentThesisService.class, OversizedKeyTest.Beans.class})
class OversizedKeyTest {

    static class Beans {
        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }
    }

    private static final String LONG = "A".repeat(2000);
    private static final String LONG_ISIN = "US" + "1".repeat(1998);

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CompanySnapshotRepository snapshots;
    @MockBean
    private StockGroupRepository groups;
    @MockBean
    private ResearchFreshness freshness;
    @MockBean
    private ResearchFindingHistoryRepository findingHistory;
    @MockBean
    private StockGroupHistoryRepository groupHistory;
    @MockBean
    private PortfolioPositionRepository positions;
    @MockBean
    private StockGroupLookup lookup;
    @MockBean
    private PurchaseWriter purchaseWriter;
    @MockBean
    private InvestmentThesisRepository theses;

    private void noRepositoryWasTouched() {
        verifyNoInteractions(snapshots, groups, positions, theses, findingHistory, groupHistory, purchaseWriter);
    }

    @Test
    void snapshotEndpointsReturn404ForAnOversizedIsin() throws Exception {
        mockMvc.perform(get("/api/research/snapshots/" + LONG_ISIN)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/research/snapshots/" + LONG_ISIN + "/history")).andExpect(status().isNotFound());
        mockMvc.perform(put("/api/research/snapshots/" + LONG_ISIN + "/group").contentType("application/json")
                .content("{\"groupKey\":\"BANKS\"}")).andExpect(status().isNotFound());
        noRepositoryWasTouched();
    }

    @Test
    void assigningAnOversizedGroupKeyReturns422WithoutGroupLookup() throws Exception {
        org.mockito.Mockito.when(snapshots.findByIsin("US0378331005")).thenReturn(java.util.Optional.of(
                new com.valuescreener.research.snapshot.CompanySnapshot("AAPL", "US0378331005", "Apple", "USA", "x")));

        mockMvc.perform(put("/api/research/snapshots/US0378331005/group").contentType("application/json")
                .content("{\"groupKey\":\"" + LONG + "\"}")).andExpect(status().isUnprocessableEntity());

        verifyNoInteractions(groups);
    }

    @Test
    void groupEndpointsReturn404ForAnOversizedKey() throws Exception {
        mockMvc.perform(delete("/api/research/groups/" + LONG)).andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/research/groups/" + LONG).contentType("application/json")
                .content("{\"name\":\"x\"}")).andExpect(status().isNotFound());
        noRepositoryWasTouched();
    }

    @Test
    void createGroupWithAnOversizedKeyIsA400() throws Exception {
        mockMvc.perform(post("/api/research/groups").contentType("application/json")
                .content("{\"key\":\"" + LONG + "\",\"name\":\"x\"}")).andExpect(status().isBadRequest());
        noRepositoryWasTouched();
    }

    @Test
    void portfolioEndpointsRejectAnOversizedIsinWithoutLookup() throws Exception {
        mockMvc.perform(put("/api/portfolio/" + LONG_ISIN).contentType("application/json").content(
                "{\"ticker\":\"X\",\"companyName\":\"X\",\"quantity\":1,\"entryPrice\":1,\"purchaseDate\":\"2026-01-01\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/portfolio/" + LONG_ISIN)).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/portfolio/sell").contentType("application/json")
                .content("{\"isin\":\"" + LONG_ISIN + "\",\"quantity\":1}")).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/portfolio").contentType("application/json").content(
                "{\"ticker\":\"X\",\"isin\":\"" + LONG_ISIN + "\",\"companyName\":\"X\",\"quantity\":1,"
                        + "\"entryPrice\":1,\"purchaseDate\":\"2026-01-01\",\"purchaseReason\":\"why\"}"))
                .andExpect(status().isBadRequest());
        noRepositoryWasTouched();
    }

    @Test
    void thesisEndpointsReturn400ForAnOversizedIsin() throws Exception {
        mockMvc.perform(get("/api/research/theses/" + LONG_ISIN)).andExpect(status().isBadRequest());
        noRepositoryWasTouched();
    }
}
