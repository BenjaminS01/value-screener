package com.valuescreener.research.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.valuescreener.research.model.CompanyResearchResult;
import com.valuescreener.research.model.ConfidenceLevel;
import com.valuescreener.research.model.SourceReference;
import com.valuescreener.research.prompt.ResearchPromptBuilder;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.AnthropicWebSearchTool;
import org.springframework.ai.anthropic.Citation;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class CompanyResearchAgent {

    private final ChatModel chatModel;
    private final ResearchPromptBuilder promptBuilder;
    private final ObjectMapper objectMapper;
    private final long timeoutSeconds;

    public CompanyResearchAgent(ChatModel chatModel,
                                 ResearchPromptBuilder promptBuilder,
                                 ObjectMapper objectMapper,
                                 @Value("${research.agent.timeout-seconds:55}") long timeoutSeconds) {
        this.chatModel = chatModel;
        this.promptBuilder = promptBuilder;
        this.objectMapper = objectMapper;
        this.timeoutSeconds = timeoutSeconds;
    }

    public CompanyResearchResult research(String ticker, String companyName) {
        ChatResponse response = chatModel.call(new Prompt(
                promptBuilder.build(ticker, companyName),
                AnthropicChatOptions.builder()
                        .webSearchTool(AnthropicWebSearchTool.builder().build())
                        .build()));

        RawResearchResponse raw = parse(response.getResult().getOutput().getText());

        if (raw.noReliableReportFound()) {
            return CompanyResearchResult.lowConfidence(ticker,
                    raw.summary() == null || raw.summary().isBlank()
                            ? "No reliable current report found for this ticker."
                            : raw.summary());
        }

        Set<String> citedUrls = extractCitedUrls(response);
        List<SourceReference> verifiedSources = raw.sources().stream()
                .filter(source -> citedUrls.contains(source.url()))
                .map(source -> new SourceReference(source.url(), source.claim()))
                .toList();

        if (verifiedSources.isEmpty()) {
            return CompanyResearchResult.lowConfidence(ticker,
                    "Model returned sources that could not be verified against actual search results.");
        }

        return new CompanyResearchResult(
                ticker,
                raw.summary(),
                raw.valueTrapAssessment(),
                verifiedSources,
                ConfidenceLevel.HIGH,
                CompanyResearchResult.CURRENT_PROMPT_VERSION);
    }

    private RawResearchResponse parse(String responseText) {
        try {
            return objectMapper.readValue(responseText, RawResearchResponse.class);
        } catch (Exception e) {
            throw new ResearchResponseParseException(
                    "Could not parse research agent response as JSON", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Set<String> extractCitedUrls(ChatResponse response) {
        Object citations = response.getMetadata().get("citations");
        if (!(citations instanceof List<?> citationList)) {
            return Set.of();
        }
        return citationList.stream()
                .filter(Citation.class::isInstance)
                .map(Citation.class::cast)
                .map(Citation::getUrl)
                .filter(url -> url != null && !url.isBlank())
                .collect(Collectors.toSet());
    }
}
