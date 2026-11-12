package com.valuescreener.research;

import com.valuescreener.security.SecuritySliceTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InvestmentThesisController.class)
@Import(SecuritySliceTestConfig.class)
class InvestmentThesisControllerTest {

    private static final String ISIN = "US0378331005";
    private static final String BODY = """
            {"whyInteresting":"why","expectedDevelopment":"exp","exitCriteria":"exit",
             "status":"WATCHING","statusReason":"pricey","version":null}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private InvestmentThesisService service;

    private static InvestmentThesisView sample() {
        return new InvestmentThesisView(ISIN, "why", "exp", "exit", ThesisStatus.WATCHING, "pricey",
                Instant.parse("2026-11-12T10:00:00Z"), 0L, true);
    }

    @Test
    void userGetsThesis() throws Exception {
        when(service.get(ISIN)).thenReturn(sample());

        mockMvc.perform(get("/api/research/theses/" + ISIN).with(user("alice").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.whyInteresting").value("why"))
                .andExpect(jsonPath("$.status").value("WATCHING"))
                .andExpect(jsonPath("$.holding").value(true))
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void lowercaseIsinPathIsAccepted() throws Exception {
        when(service.get("us0378331005")).thenReturn(sample());

        mockMvc.perform(get("/api/research/theses/us0378331005").with(user("alice").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isin").value(ISIN));
    }

    @Test
    void unknownThesisIs404WithFixedBody() throws Exception {
        when(service.get(ISIN)).thenThrow(new ThesisNotFoundException(ISIN));

        mockMvc.perform(get("/api/research/theses/" + ISIN).with(user("alice").roles("USER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Thesis not found"));
    }

    @Test
    void anonymousGetIs401() throws Exception {
        mockMvc.perform(get("/api/research/theses/" + ISIN)).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void adminPutReturnsView() throws Exception {
        when(service.save(eq(ISIN), any(SaveThesisRequest.class))).thenReturn(sample());

        mockMvc.perform(put("/api/research/theses/" + ISIN).with(user("admin").roles("ADMIN")).with(csrf())
                        .contentType("application/json").content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exitCriteria").value("exit"));
    }

    @Test
    void userPutIs403AndServiceNeverCalled() throws Exception {
        mockMvc.perform(put("/api/research/theses/" + ISIN).with(user("alice").roles("USER")).with(csrf())
                        .contentType("application/json").content(BODY))
                .andExpect(status().isForbidden());

        verify(service, never()).save(any(), any());
    }

    @Test
    void anonymousPutIs401AndServiceNeverCalled() throws Exception {
        mockMvc.perform(put("/api/research/theses/" + ISIN).with(csrf())
                        .contentType("application/json").content(BODY))
                .andExpect(status().isUnauthorized());

        verify(service, never()).save(any(), any());
    }

    @Test
    void staleVersionIs409() throws Exception {
        when(service.save(eq(ISIN), any(SaveThesisRequest.class)))
                .thenThrow(new ObjectOptimisticLockingFailureException(InvestmentThesis.class, ISIN));

        mockMvc.perform(put("/api/research/theses/" + ISIN).with(user("admin").roles("ADMIN")).with(csrf())
                        .contentType("application/json").content(BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Conflict, please reload"));
    }

    @Test
    void statusReasonWithoutStatusIs400() throws Exception {
        when(service.save(eq(ISIN), any(SaveThesisRequest.class)))
                .thenThrow(new IllegalArgumentException("statusReason requires status"));

        mockMvc.perform(put("/api/research/theses/" + ISIN).with(user("admin").roles("ADMIN")).with(csrf())
                        .contentType("application/json").content(BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("statusReason requires status"));
    }

    @Test
    void tooLongTextIs422() throws Exception {
        when(service.save(eq(ISIN), any(SaveThesisRequest.class))).thenThrow(new ThesisTooLongException());

        mockMvc.perform(put("/api/research/theses/" + ISIN).with(user("admin").roles("ADMIN")).with(csrf())
                        .contentType("application/json").content(BODY))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("Thesis text too long"));
    }

    @Test
    void unknownStatusValueIs400() throws Exception {
        mockMvc.perform(put("/api/research/theses/" + ISIN).with(user("admin").roles("ADMIN")).with(csrf())
                        .contentType("application/json").content("{\"status\":\"HOLDING\"}"))
                .andExpect(status().isBadRequest());
    }
}
