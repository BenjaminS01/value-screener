package com.valuescreener.portfolio;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PortfolioController.class)
@AutoConfigureMockMvc(addFilters = false)
class PortfolioControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PortfolioService portfolioService;

    @Test
    void returnsPublicPositionsAsJson() throws Exception {
        when(portfolioService.listPublicPositions())
                .thenReturn(List.of(new PublicPortfolioPositionView("AAPL", "Apple Inc.")));

        mockMvc.perform(get("/api/portfolio/public"))
                .andExpect(status().isOk())
                .andExpect(content().json("[{\"ticker\":\"AAPL\",\"companyName\":\"Apple Inc.\"}]"));
    }

    @Test
    void createsPositionOnValidRequest() throws Exception {
        mockMvc.perform(post("/api/portfolio")
                        .contentType("application/json")
                        .content("""
                                {"ticker":"AAPL","isin":"US0378331005","companyName":"Apple Inc.","quantity":10,"entryPrice":150.00,"purchaseDate":"2026-01-15"}
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    void rejectsAddPositionWithBlankTicker() throws Exception {
        mockMvc.perform(post("/api/portfolio")
                        .contentType("application/json")
                        .content("""
                                {"ticker":"","isin":"US0378331005","companyName":"Apple Inc.","quantity":10,"entryPrice":150.00,"purchaseDate":"2026-01-15"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sellsPositionOnValidRequest() throws Exception {
        mockMvc.perform(post("/api/portfolio/sell")
                        .contentType("application/json")
                        .content("""
                                {"isin":"US0378331005","quantity":4}
                                """))
                .andExpect(status().isNoContent());
    }
}
