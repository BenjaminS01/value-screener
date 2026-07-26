package com.valuescreener.research.agent;

import com.anthropic.models.messages.Model;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.valuescreener.research.model.CompanyResearchResult;
import com.valuescreener.research.model.ConfidenceLevel;
import com.valuescreener.research.model.SourceReference;
import com.valuescreener.research.prompt.ResearchPromptBuilder;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.AnthropicWebSearchTool;
import org.springframework.ai.anthropic.Citation;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

@Component
public class CompanyResearchAgent {

    private static final Logger log = LoggerFactory.getLogger(CompanyResearchAgent.class);

    private final ChatModel chatModel;
    private final ResearchPromptBuilder promptBuilder;
    private final ObjectMapper objectMapper;
    private final long timeoutSeconds;
    private final String model;
    private final long webSearchMaxUses;

    public CompanyResearchAgent(ChatModel chatModel,
                                 ResearchPromptBuilder promptBuilder,
                                 ObjectMapper objectMapper,
                                 @Value("${research.agent.timeout-seconds:55}") long timeoutSeconds,
                                 @Value("${spring.ai.anthropic.chat.model:claude-sonnet-5}") String model,
                                 @Value("${research.agent.web-search-max-uses:5}") long webSearchMaxUses) {
        this.chatModel = chatModel;
        this.promptBuilder = promptBuilder;
        this.objectMapper = objectMapper;
        this.timeoutSeconds = timeoutSeconds;
        this.model = model;
        this.webSearchMaxUses = webSearchMaxUses;
    }

    private final Executor executor = Executors.newVirtualThreadPerTaskExecutor();

    public CompanyResearchResult research(String ticker, String companyName) {
        ChatResponse response = callWithTimeout(ticker, companyName);
        logUsage(ticker, response);

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
                .filter(source -> source.claim() != null && !source.claim().isBlank())
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

    private ChatResponse callWithTimeout(String ticker, String companyName) {
        Prompt prompt = new Prompt(
                promptBuilder.build(ticker, companyName),
                AnthropicChatOptions.builder()
                        .model(Model.of(model))
                        .webSearchTool(AnthropicWebSearchTool.builder().maxUses(webSearchMaxUses).build())
                        .build());

        CompletableFuture<ChatResponse> future =
                CompletableFuture.supplyAsync(() -> chatModel.call(prompt), executor);
        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            // future.cancel(true) only stops us from waiting locally: the underlying OkHttp
            // call blocks on a plain socket read, which does not react to Thread.interrupt(),
            // so the request to Anthropic keeps running (and gets billed) in the background.
            log.warn("Research for {} timed out locally after {}s; the underlying Anthropic "
                    + "request may still be running and billed server-side", ticker, timeoutSeconds);
            throw new ResearchTimeoutException(
                    "Research for " + ticker + " did not complete within " + timeoutSeconds + "s", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResearchTimeoutException("Research for " + ticker + " was interrupted", e);
        } catch (ExecutionException e) {
            throw new ResearchTimeoutException(
                    "Research for " + ticker + " failed: " + e.getCause().getMessage(), e.getCause());
        }
    }

    private void logUsage(String ticker, ChatResponse response) {
        Usage usage = response.getMetadata().getUsage();
        if (usage == null) {
            log.warn("No usage metadata returned for research call on {}", ticker);
            return;
        }
        // Anthropic's own usage object has no separate thinking-token count: completionTokens
        // bundles thinking and the final answer together (confirmed via javap on the bundled
        // spring-ai-anthropic jar -- com.anthropic Usage.outputTokens() maps 1:1 onto this field).
        // A completionTokens figure far above the ~400-600 tokens the final JSON answer alone
        // needs is the signal that most of it was spent thinking, not writing the answer.
        log.info("Research usage for {}: promptTokens={}, completionTokens={}, totalTokens={}, "
                        + "cacheReadInputTokens={}, cacheWriteInputTokens={}",
                ticker, usage.getPromptTokens(), usage.getCompletionTokens(), usage.getTotalTokens(),
                usage.getCacheReadInputTokens(), usage.getCacheWriteInputTokens());
    }

    private RawResearchResponse parse(String responseText) {
        try {
            return objectMapper.readValue(responseText, RawResearchResponse.class);
        } catch (Exception e) {
            throw new ResearchResponseParseException(
                    "Could not parse research agent response as JSON", e);
        }
    }

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
