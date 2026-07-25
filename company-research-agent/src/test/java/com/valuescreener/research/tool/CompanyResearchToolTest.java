package com.valuescreener.research.tool;

import com.valuescreener.research.agent.CompanyResearchAgent;
import com.valuescreener.research.agent.ResearchTimeoutException;
import com.valuescreener.research.model.CompanyResearchResult;
import com.valuescreener.research.model.ConfidenceLevel;
import com.valuescreener.research.model.SourceReference;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CompanyResearchToolTest {

    private final CompanyResearchAgent agent = mock(CompanyResearchAgent.class);
    private final CompanyResearchTool tool = new CompanyResearchTool(agent);

    @Test
    void returnsSuccessfulStructuredResultOnSuccess() {
        CompanyResearchResult successResult = new CompanyResearchResult(
                "EXMP", "Revenue grew 8%.", "No structural headwinds mentioned.",
                List.of(new SourceReference("https://investor.example.com/q2-2026", "Revenue grew 8%")),
                ConfidenceLevel.HIGH, CompanyResearchResult.CURRENT_PROMPT_VERSION);
        when(agent.research("EXMP", "Example Corp")).thenReturn(successResult);

        CallToolResult result = tool.researchCompany("EXMP", "Example Corp");

        assertThat(result.isError()).isNotEqualTo(true);
    }

    @Test
    void returnsErrorResultWhenAgentTimesOut() {
        when(agent.research("EXMP", "Example Corp"))
                .thenThrow(new ResearchTimeoutException("timed out", new RuntimeException()));

        CallToolResult result = tool.researchCompany("EXMP", "Example Corp");

        assertThat(result.isError()).isTrue();
    }
}
