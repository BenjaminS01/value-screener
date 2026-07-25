package com.valuescreener.research.tool;

import com.valuescreener.research.agent.CompanyResearchAgent;
import com.valuescreener.research.agent.ResearchResponseParseException;
import com.valuescreener.research.agent.ResearchTimeoutException;
import com.valuescreener.research.model.CompanyResearchResult;
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
            description = "Researches a company's most recent quarterly report and disclosed "
                    + "risk factors, returning a sourced, descriptive summary with a value-trap "
                    + "assessment. Returns an error result if research could not complete.")
    public CallToolResult researchCompany(
            @McpToolParam(description = "Stock ticker symbol", required = true) String ticker,
            @McpToolParam(description = "Full company name", required = true) String companyName) {

        try {
            CompanyResearchResult result = agent.research(ticker, companyName);
            return CallToolResult.builder()
                    .addTextContent(result.summary())
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
