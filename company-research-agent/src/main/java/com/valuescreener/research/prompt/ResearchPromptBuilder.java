package com.valuescreener.research.prompt;

import org.springframework.stereotype.Component;

@Component
public class ResearchPromptBuilder {

    public String build(String ticker, String companyName) {
        return """
                You are researching the company %s (ticker: %s) for a value-investing analysis tool.

                Use web search to find the company's most recent quarterly or interim report,
                management commentary, and disclosed risk factors. If the company is not subject to
                mandatory quarterly reporting (common outside the US) and you cannot find a reliable,
                recent report, set "noReliableReportFound" to true instead of relying on older
                training knowledge.

                Write in a descriptive, analytical tone. Never phrase findings as a recommendation
                or warning (for example, never write "this is a value trap" or "investors should
                avoid this stock"). Instead, describe what the source material says and let the
                reader draw conclusions, for example: "management commentary cites structural
                headwinds in segment X that may explain the below-median valuation."

                Do not quote source text verbatim. Paraphrase every claim in your own words and
                attach a link to the specific source it came from.

                Respond with a final answer containing ONLY a single JSON object with this exact
                shape, no other text before or after it:
                {
                  "summary": "paraphrased overview of what the report/commentary says",
                  "valueTrapAssessment": "descriptive assessment of whether the valuation appears explained by fundamentals, phrased neutrally",
                  "sources": [{"url": "https://...", "claim": "the specific paraphrased claim this source supports"}],
                  "noReliableReportFound": false
                }
                """.formatted(companyName, ticker);
    }
}
