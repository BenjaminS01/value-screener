package com.valuescreener.research.tool;

import com.valuescreener.research.agent.CompanyResearchAgent;
import com.valuescreener.research.agent.ResearchTimeoutException;
import com.valuescreener.research.model.CompanyResearchResult;
import com.valuescreener.research.model.ConfidenceLevel;
import com.valuescreener.research.model.QuickResearchResult;
import com.valuescreener.research.model.SourceReference;
import com.valuescreener.research.model.Stage1Snapshot;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CompanyResearchToolTest {

    private final CompanyResearchAgent agent = mock(CompanyResearchAgent.class);
    private final CompanyResearchTool tool = new CompanyResearchTool(agent);

    @Test
    void returnsSuccessfulStructuredResultOnResearchCompanySuccess() {
        CompanyResearchResult successResult = new CompanyResearchResult(
                "EXMP",
                new SourceReference("https://investor.example.com/q2-2026", "Margins held steady around 20%"),
                null, null, null, null, null, null, null,
                ConfidenceLevel.HIGH, null, CompanyResearchResult.CURRENT_PROMPT_VERSION);
        when(agent.research("EXMP", "Example Corp", null)).thenReturn(successResult);

        CallToolResult result = tool.researchCompany("EXMP", "Example Corp", null);

        assertThat(result.isError()).isNotEqualTo(true);
    }

    @Test
    void passesStage1SnapshotThroughToTheAgent() {
        Stage1Snapshot snapshot = new Stage1Snapshot(24.3, 3.1);
        CompanyResearchResult successResult = CompanyResearchResult.lowConfidence("EXMP", "reason");
        when(agent.research("EXMP", "Example Corp", snapshot)).thenReturn(successResult);

        tool.researchCompany("EXMP", "Example Corp", snapshot);

        verify(agent).research("EXMP", "Example Corp", snapshot);
    }

    @Test
    void returnsErrorResultWhenResearchCompanyAgentTimesOut() {
        when(agent.research("EXMP", "Example Corp", null))
                .thenThrow(new ResearchTimeoutException("timed out", new RuntimeException()));

        CallToolResult result = tool.researchCompany("EXMP", "Example Corp", null);

        assertThat(result.isError()).isTrue();
    }

    @Test
    void returnsSuccessfulStructuredResultOnQuickResearchCompanySuccess() {
        QuickResearchResult successResult = QuickResearchResult.noData("EXMP", "reason");
        when(agent.quickResearch("EXMP", "Example Corp")).thenReturn(successResult);

        CallToolResult result = tool.quickResearchCompany("EXMP", "Example Corp");

        assertThat(result.isError()).isNotEqualTo(true);
    }

    @Test
    void returnsErrorResultWhenQuickResearchCompanyAgentTimesOut() {
        when(agent.quickResearch("EXMP", "Example Corp"))
                .thenThrow(new ResearchTimeoutException("timed out", new RuntimeException()));

        CallToolResult result = tool.quickResearchCompany("EXMP", "Example Corp");

        assertThat(result.isError()).isTrue();
    }
}
