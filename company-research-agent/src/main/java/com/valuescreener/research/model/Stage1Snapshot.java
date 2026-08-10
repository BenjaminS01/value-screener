package com.valuescreener.research.model;

import org.springframework.ai.mcp.annotation.McpToolParam;

// Without an explicit required=false here, the MCP schema generator (victools, via
// AbstractSpringAiSchemaModule) defaults record components with no nullability marker to
// required in the *nested* object schema -- independent of stage1Snapshot itself being
// optional at the top level in CompanyResearchTool. That made it impossible to omit
// stage1Snapshot entirely from a research_company call (confirmed live via MCP Inspector).
public record Stage1Snapshot(
        @McpToolParam(description = "Current P/E from an earlier quick_research_company call",
                required = false) Double currentPe,
        @McpToolParam(description = "Current P/B from an earlier quick_research_company call",
                required = false) Double currentPb) {
}
