package com.valuescreener.portfolio;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import com.valuescreener.research.ThesisTooLongException;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = PortfolioController.class, excludeFilters = @org.springframework.context.annotation.ComponentScan.Filter(
        type = org.springframework.context.annotation.FilterType.ASSIGNABLE_TYPE,
        classes = com.valuescreener.security.LoginThrottleWebConfig.class))
@AutoConfigureMockMvc(addFilters = false)
class PortfolioControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PortfolioService portfolioService;

    private static final Authentication ADMIN = UsernamePasswordAuthenticationToken.authenticated(
            "admin", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    private static final Authentication USER = UsernamePasswordAuthenticationToken.authenticated(
            "alice", null, List.of(new SimpleGrantedAuthority("ROLE_USER")));

    private static final String CORRECTION = """
            {"ticker":"AAPL","companyName":"Apple Inc.","quantity":3,"entryPrice":99.5,"purchaseDate":"2026-04-01"}
            """;

    @Test
    void adminGetsHoldingsFields() throws Exception {
        when(portfolioService.listForAdmin()).thenReturn(List.of(new AdminPortfolioPositionView(
                "AAPL", "US0378331005", "Apple Inc.", null, new BigDecimal("10"), new BigDecimal("150.00"), LocalDate.of(2026, 1, 15))));

        mockMvc.perform(get("/api/portfolio").principal(ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].isin").value("US0378331005"))
                .andExpect(jsonPath("$[0].quantity").value(10))
                .andExpect(jsonPath("$[0].entryPrice").value(150.00))
                .andExpect(jsonPath("$[0].purchaseDate").value("2026-01-15"));
    }

    @Test
    void userResponseHasNoHoldingsKeys() throws Exception {
        when(portfolioService.listForUser())
                .thenReturn(List.of(new PortfolioPositionView("AAPL", "US0378331005", "Apple Inc.", null)));

        mockMvc.perform(get("/api/portfolio").principal(USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ticker").value("AAPL"))
                .andExpect(jsonPath("$[0].isin").value("US0378331005"))
                .andExpect(jsonPath("$[0].companyName").value("Apple Inc."))
                .andExpect(jsonPath("$[0].quantity").doesNotExist())
                .andExpect(jsonPath("$[0].entryPrice").doesNotExist())
                .andExpect(jsonPath("$[0].purchaseDate").doesNotExist());
        verify(portfolioService, never()).listForAdmin();
    }


    @Test
    void userViewHasGroupButNoHoldings() throws Exception {
        when(portfolioService.listForUser()).thenReturn(List.of(new PortfolioPositionView(
                "AAPL", "US0378331005", "Apple Inc.", new com.valuescreener.research.StockGroupRef("SEMICONDUCTORS", "Semiconductors"))));

        mockMvc.perform(get("/api/portfolio").principal(USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].group.key").value("SEMICONDUCTORS"))
                .andExpect(jsonPath("$[0].group.name").value("Semiconductors"))
                .andExpect(jsonPath("$[0].quantity").doesNotExist())
                .andExpect(jsonPath("$[0].entryPrice").doesNotExist())
                .andExpect(jsonPath("$[0].purchaseDate").doesNotExist());
    }
    @Test
    void putReturns204() throws Exception {
        mockMvc.perform(put("/api/portfolio/US0378331005").contentType("application/json").content(CORRECTION))
                .andExpect(status().isNoContent());
        verify(portfolioService).correct(eq("US0378331005"), any(CorrectPositionRequest.class));
    }

    @Test
    void putUnknownIsin404() throws Exception {
        doThrow(new PositionNotFoundException("US0378331005"))
                .when(portfolioService).correct(any(), any());

        mockMvc.perform(put("/api/portfolio/US0378331005").contentType("application/json").content(CORRECTION))
                .andExpect(status().isNotFound());
    }

    @Test
    void putWithInvalidBodyReturns400() throws Exception {
        mockMvc.perform(put("/api/portfolio/US0378331005").contentType("application/json")
                        .content("{\"ticker\":\"AAPL\",\"companyName\":\"Apple\",\"quantity\":0,\"entryPrice\":1,\"purchaseDate\":\"2026-04-01\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void putOnConcurrentModificationReturns409() throws Exception {
        doThrow(new ObjectOptimisticLockingFailureException(PortfolioPosition.class, 1L))
                .when(portfolioService).correct(any(), any());

        mockMvc.perform(put("/api/portfolio/US0378331005").contentType("application/json").content(CORRECTION))
                .andExpect(status().isConflict())
                .andExpect(content().json("{\"error\":\"Position was changed concurrently, please reload\"}"));
    }

    @Test
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete("/api/portfolio/US0378331005"))
                .andExpect(status().isNoContent());
        verify(portfolioService).delete("US0378331005");
    }

    @Test
    void deleteUnknownIsin404() throws Exception {
        doThrow(new PositionNotFoundException("US0378331005")).when(portfolioService).delete(any());

        mockMvc.perform(delete("/api/portfolio/US0378331005"))
                .andExpect(status().isNotFound());
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
    void buyPassesPurchaseReasonToService() throws Exception {
        mockMvc.perform(post("/api/portfolio")
                        .contentType("application/json")
                        .content("""
                                {"ticker":"AAPL","isin":"US0378331005","companyName":"Apple Inc.","quantity":10,"entryPrice":150.00,"purchaseDate":"2026-01-15","purchaseReason":"Moat intact"}
                                """))
                .andExpect(status().isCreated());

        ArgumentCaptor<BuyPositionRequest> captor = ArgumentCaptor.forClass(BuyPositionRequest.class);
        verify(portfolioService).buy(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().purchaseReason()).isEqualTo("Moat intact");
    }

    @Test
    void buyWithTooLongThesisAppendIs422() throws Exception {
        doThrow(new ThesisTooLongException()).when(portfolioService).buy(any(BuyPositionRequest.class));

        mockMvc.perform(post("/api/portfolio")
                        .contentType("application/json")
                        .content("""
                                {"ticker":"AAPL","isin":"US0378331005","companyName":"Apple Inc.","quantity":10,"entryPrice":150.00,"purchaseDate":"2026-01-15","purchaseReason":"x"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("Thesis text too long"));
    }

    @Test
    void buyWithOverlongPurchaseReasonIs400() throws Exception {
        String reason = "x".repeat(2001);

        mockMvc.perform(post("/api/portfolio")
                        .contentType("application/json")
                        .content("{\"ticker\":\"AAPL\",\"isin\":\"US0378331005\",\"companyName\":\"Apple Inc.\","
                                + "\"quantity\":10,\"entryPrice\":150.00,\"purchaseDate\":\"2026-01-15\",\"purchaseReason\":\"" + reason + "\"}"))
                .andExpect(status().isBadRequest());
        verify(portfolioService, never()).buy(any());
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

    @Test
    void sellingUnknownIsinReturnsNotFound() throws Exception {
        doThrow(new PositionNotFoundException("US0378331005"))
                .when(portfolioService).sell(any());

        mockMvc.perform(post("/api/portfolio/sell")
                        .contentType("application/json")
                        .content("""
                                {"isin":"US0378331005","quantity":4}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void sellingMoreThanOwnedQuantityReturnsBadRequest() throws Exception {
        doThrow(new IllegalArgumentException("sale quantity must not exceed current holding"))
                .when(portfolioService).sell(any());

        mockMvc.perform(post("/api/portfolio/sell")
                        .contentType("application/json")
                        .content("""
                                {"isin":"US0378331005","quantity":1000}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void buyingWithMalformedIsinReturnsBadRequest() throws Exception {
        doThrow(new IllegalArgumentException("isin must be a valid 12-character ISIN"))
                .when(portfolioService).buy(any());

        mockMvc.perform(post("/api/portfolio")
                        .contentType("application/json")
                        .content("""
                                {"ticker":"AAPL","isin":"NOTANISIN123","companyName":"Apple Inc.","quantity":10,"entryPrice":150.00,"purchaseDate":"2026-01-15"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void malformedJsonReturnsUniform400() throws Exception {
        mockMvc.perform(post("/api/portfolio").contentType("application/json").content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("{\"error\":\"Invalid request\"}", true));
    }

    @Test
    void notFoundHasErrorBody() throws Exception {
        doThrow(new PositionNotFoundException("US0378331005")).when(portfolioService).delete(any());

        mockMvc.perform(delete("/api/portfolio/US0378331005"))
                .andExpect(status().isNotFound())
                .andExpect(content().json("{\"error\":\"Position not found\"}", true));
    }

    @Test
    void illegalArgumentHasErrorBodyWithMessage() throws Exception {
        doThrow(new IllegalArgumentException("Cannot sell more than held")).when(portfolioService).sell(any());

        mockMvc.perform(post("/api/portfolio/sell").contentType("application/json")
                        .content("{\"isin\":\"US0378331005\",\"quantity\":1000}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("{\"error\":\"Cannot sell more than held\"}", true));
    }

    @Test
    void illegalArgumentWithoutMessageFallsBackToInvalidRequest() throws Exception {
        doThrow(new IllegalArgumentException()).when(portfolioService).sell(any());

        mockMvc.perform(post("/api/portfolio/sell").contentType("application/json")
                        .content("{\"isin\":\"US0378331005\",\"quantity\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("{\"error\":\"Invalid request\"}", true));
    }
}
