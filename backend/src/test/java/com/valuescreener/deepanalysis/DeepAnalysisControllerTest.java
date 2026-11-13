package com.valuescreener.deepanalysis;

import com.valuescreener.limits.JobType;
import com.valuescreener.limits.LimitReachedException;
import com.valuescreener.limits.Quota;
import com.valuescreener.limits.UsageLimiter;
import com.valuescreener.research.CompanySnapshotNotFoundException;
import com.valuescreener.research.CompanySnapshotService;
import com.valuescreener.security.Role;
import com.valuescreener.security.SecuritySliceTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DeepAnalysisController.class)
@Import(SecuritySliceTestConfig.class)
class DeepAnalysisControllerTest {

    private static final String ISIN = "US0378331005";
    private static final String URL = "/api/research/snapshots/" + ISIN + "/deep-analysis";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UsageLimiter usageLimiter;

    @MockBean
    private DeepAnalysisInvoker invoker;

    @MockBean
    private CompanySnapshotService snapshotService;

    private static DeepAnalysisResult result() {
        return new DeepAnalysisResult("NOT_IMPLEMENTED", "Noch nicht implementiert", "Hallo von AWS Lambda",
                "deep-analysis-demo", "$LATEST", "eu-central-1", 512, "req-1", true,
                Instant.parse("2026-11-12T10:00:00Z"), DeepAnalysisResult.Source.LAMBDA);
    }

    @Test
    void userGetsMergedResponse() throws Exception {
        when(usageLimiter.consume("alice", Role.USER, JobType.DEEP_ANALYSIS_DEMO)).thenReturn(new Quota(4, 5));
        when(invoker.invoke(ISIN)).thenReturn(result());

        mockMvc.perform(post(URL).with(user("alice").roles("USER")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NOT_IMPLEMENTED"))
                .andExpect(jsonPath("$.functionName").value("deep-analysis-demo"))
                .andExpect(jsonPath("$.memoryLimitMb").value(512))
                .andExpect(jsonPath("$.coldStart").value(true))
                .andExpect(jsonPath("$.source").value("LAMBDA"))
                .andExpect(jsonPath("$.remainingToday").value(4))
                .andExpect(jsonPath("$.limitPerDay").value(5));
    }

    @Test
    void adminIsConsumedWithAdminRole() throws Exception {
        when(usageLimiter.consume("admin", Role.ADMIN, JobType.DEEP_ANALYSIS_DEMO)).thenReturn(new Quota(19, 20));
        when(invoker.invoke(ISIN)).thenReturn(result());

        mockMvc.perform(post(URL).with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.limitPerDay").value(20));
    }

    @Test
    void lowercaseIsinIsNormalized() throws Exception {
        when(usageLimiter.consume(anyString(), any(), any())).thenReturn(new Quota(4, 5));
        when(invoker.invoke(ISIN)).thenReturn(result());

        mockMvc.perform(post("/api/research/snapshots/us0378331005/deep-analysis")
                        .with(user("alice").roles("USER")).with(csrf()))
                .andExpect(status().isOk());
        verify(snapshotService).getByIsin(ISIN);
    }

    @Test
    void anonymousIs401() throws Exception {
        mockMvc.perform(post(URL).with(csrf())).andExpect(status().isUnauthorized());
        verifyNoInteractions(usageLimiter, invoker);
    }

    @Test
    void missingCsrfIs403() throws Exception {
        mockMvc.perform(post(URL).with(user("alice").roles("USER"))).andExpect(status().isForbidden());
        verifyNoInteractions(usageLimiter, invoker);
    }

    @Test
    void unknownIsinIs404WithoutConsumingQuota() throws Exception {
        when(snapshotService.getByIsin(ISIN)).thenThrow(new CompanySnapshotNotFoundException(ISIN));

        mockMvc.perform(post(URL).with(user("alice").roles("USER")).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(content().json("{\"error\":\"Snapshot not found\"}"));
        verify(usageLimiter, never()).consume(anyString(), any(), any());
        verifyNoInteractions(invoker);
    }

    @Test
    void malformedIsinIs404WithoutConsumingQuota() throws Exception {
        mockMvc.perform(post("/api/research/snapshots/NOT-AN-ISIN/deep-analysis")
                        .with(user("alice").roles("USER")).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(content().json("{\"error\":\"Snapshot not found\"}"));
        verify(usageLimiter, never()).consume(anyString(), any(), any());
        verifyNoInteractions(invoker);
    }

    @Test
    void accountLimitIs429AndInvokerNotCalled() throws Exception {
        when(usageLimiter.consume("alice", Role.USER, JobType.DEEP_ANALYSIS_DEMO))
                .thenThrow(new LimitReachedException(LimitReachedException.Scope.ACCOUNT));

        mockMvc.perform(post(URL).with(user("alice").roles("USER")).with(csrf()))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().json("{\"error\":\"Tageslimit für die Tiefenanalyse erreicht\"}", true));
        verifyNoInteractions(invoker);
    }

    @Test
    void globalLimitIs429WithGlobalCopy() throws Exception {
        when(usageLimiter.consume("alice", Role.USER, JobType.DEEP_ANALYSIS_DEMO))
                .thenThrow(new LimitReachedException(LimitReachedException.Scope.GLOBAL));

        mockMvc.perform(post(URL).with(user("alice").roles("USER")).with(csrf()))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().json("{\"error\":\"Tageslimit der Anwendung erreicht\"}", true));
        verifyNoInteractions(invoker);
    }

    @Test
    void invokerFailureIs502WithGenericBody() throws Exception {
        when(usageLimiter.consume("alice", Role.USER, JobType.DEEP_ANALYSIS_DEMO)).thenReturn(new Quota(4, 5));
        when(invoker.invoke(ISIN)).thenThrow(new DeepAnalysisUnavailableException("arn:aws:lambda:secret"));

        mockMvc.perform(post(URL).with(user("alice").roles("USER")).with(csrf()))
                .andExpect(status().isBadGateway())
                .andExpect(content().json("{\"error\":\"Tiefenanalyse nicht verfügbar\"}", true));
    }

    @Test
    void quotaForUser() throws Exception {
        when(usageLimiter.quota("alice", Role.USER, JobType.DEEP_ANALYSIS_DEMO)).thenReturn(new Quota(3, 5));

        mockMvc.perform(get("/api/research/deep-analysis/quota").with(user("alice").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remainingToday").value(3))
                .andExpect(jsonPath("$.limitPerDay").value(5));
    }
}
