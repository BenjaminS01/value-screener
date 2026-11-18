package com.valuescreener.research.tool;

import com.valuescreener.research.agent.CompanyResearchAgent;
import com.valuescreener.research.agent.ResearchResponseParseException;
import com.valuescreener.research.agent.ResearchTimeoutException;
import com.valuescreener.research.model.CompanyResearchResult;
import com.valuescreener.research.model.QuickResearchResult;
import com.valuescreener.research.model.Stage1Snapshot;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class CompanyResearchTool {

    private final CompanyResearchAgent agent;

    public CompanyResearchTool(CompanyResearchAgent agent) {
        this.agent = agent;
    }

    @McpTool(name = "research_company",
            description = "Full deep research on a company against the complete criteria set "
                    + "(margin trend, free cash flow trend, profit stability, interest coverage, "
                    + "current ratio, moat, management quality, value-trap assessment), each backed "
                    + "by a source reference. Accepts an optional Stage 1 valuation snapshot as "
                    + "grounding context. Returns an error result if research could not complete.")
    public CallToolResult researchCompany(
            @McpToolParam(description = "Stock ticker symbol", required = true) String ticker,
            @McpToolParam(description = "Full company name", required = true) String companyName,
            @McpToolParam(description = "Current P/E and P/B from an earlier quick_research_company "
                    + "call, used as grounding context; omit if unavailable", required = false)
            Stage1Snapshot stage1Snapshot) {

        try {
            CompanyResearchResult result = agent.research(ticker, companyName, stage1Snapshot);
            return CallToolResult.builder()
                    .addTextContent(result.lowConfidenceReason() == null
                            ? result.confidence().name()
                            : result.confidence().name() + ": " + result.lowConfidenceReason())
                    .structuredContent(result)
                    .build();
        } catch (ResearchTimeoutException | ResearchResponseParseException e) {
            return CallToolResult.builder()
                    .addTextContent("Research failed: " + e.getMessage())
                    .isError(true)
                    .build();
        }
    }

    @McpTool(name = "quick_research_company",
            description = "One bounded web search for a company's current numeric snapshot "
                    + "(P/E, P/B, ROE, debt/equity, current ratio if available, current-year "
                    + "reject-filter figures, insider ownership), each backed by a source "
                    + "reference. Returns an error result if research could not complete.")
    public CallToolResult quickResearchCompany(
            @McpToolParam(description = "Stock ticker symbol", required = true) String ticker,
            @McpToolParam(description = "Full company name", required = true) String companyName) {

        try {
            QuickResearchResult result = agent.quickResearch(ticker, companyName);
            return CallToolResult.builder()
                    .addTextContent(result.noReliableDataFound() ? "NO_DATA" : "OK")
                    .structuredContent(result)
                    .build();
        } catch (ResearchTimeoutException | ResearchResponseParseException e) {
            return CallToolResult.builder()
                    .addTextContent("Research failed: " + e.getMessage())
                    .isError(true)
                    .build();
        }
    }
}
