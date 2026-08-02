package com.valuescreener.research.agent;

import com.anthropic.models.messages.Model;
import com.anthropic.models.messages.OutputConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.valuescreener.research.model.BooleanFinding;
import com.valuescreener.research.model.CompanyResearchResult;
import com.valuescreener.research.model.ConfidenceLevel;
import com.valuescreener.research.model.NumericFinding;
import com.valuescreener.research.model.QuickResearchResult;
import com.valuescreener.research.model.SourceReference;
import com.valuescreener.research.model.Stage1Snapshot;
import com.valuescreener.research.prompt.QuickResearchPromptBuilder;
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

    // Fixed per the agent spec's Section 3 (Stage 1 mechanism decision) rather than configurable
    // like Stage 2's webSearchMaxUses -- one bounded search step is the whole point of Stage 1.
    private static final long STAGE1_MAX_USES = 1L;
    private static final OutputConfig.Effort STAGE1_EFFORT = OutputConfig.Effort.LOW;
    private static final OutputConfig.Effort STAGE2_EFFORT = OutputConfig.Effort.MEDIUM;

    private final ChatModel chatModel;
    private final ResearchPromptBuilder promptBuilder;
    private final QuickResearchPromptBuilder quickPromptBuilder;
    private final ObjectMapper objectMapper;
    private final long timeoutSeconds;
    private final String model;
    private final long webSearchMaxUses;
    private final List<String> allowedDomains;

    public CompanyResearchAgent(ChatModel chatModel,
                                 ResearchPromptBuilder promptBuilder,
                                 QuickResearchPromptBuilder quickPromptBuilder,
                                 ObjectMapper objectMapper,
                                 @Value("${research.agent.timeout-seconds:55}") long timeoutSeconds,
                                 @Value("${spring.ai.anthropic.chat.model:claude-sonnet-5}") String model,
                                 @Value("${research.agent.web-search-max-uses:5}") long webSearchMaxUses,
                                 @Value("${research.agent.allowed-domains:sec.gov,www.sec.gov,stockanalysis.com,marketscreener.com,finance.yahoo.com,morningstar.com,reuters.com,wsj.com,macrotrends.net,boerse-frankfurt.de,finanzen.net,globenewswire.com,prnewswire.com,businesswire.com}")
                                 String[] allowedDomains) {
        this.chatModel = chatModel;
        this.promptBuilder = promptBuilder;
        this.quickPromptBuilder = quickPromptBuilder;
        this.objectMapper = objectMapper;
        this.timeoutSeconds = timeoutSeconds;
        this.model = model;
        this.webSearchMaxUses = webSearchMaxUses;
        this.allowedDomains = List.of(allowedDomains);
    }

    private final Executor executor = Executors.newVirtualThreadPerTaskExecutor();

    public CompanyResearchResult research(String ticker, String companyName, Stage1Snapshot stage1Snapshot) {
        ChatResponse response = callWithTimeout(
                ticker, promptBuilder.build(ticker, companyName, stage1Snapshot), webSearchMaxUses, STAGE2_EFFORT);
        logUsage(ticker, response);

        RawResearchResponse raw = parse(response.getResult().getOutput().getText(), RawResearchResponse.class);

        if (raw.noReliableReportFound()) {
            return CompanyResearchResult.lowConfidence(ticker,
                    raw.noReliableReportFoundReason() == null || raw.noReliableReportFoundReason().isBlank()
                            ? "No reliable current report found for this ticker."
                            : raw.noReliableReportFoundReason());
        }

        Set<String> citedUrls = extractCitedUrls(response);

        SourceReference marginTrend = verify(raw.marginTrend(), citedUrls);
        SourceReference freeCashFlowTrend = verify(raw.freeCashFlowTrend(), citedUrls);
        SourceReference profitStability = verify(raw.profitStability(), citedUrls);
        NumericFinding interestCoverage = verify(raw.interestCoverage(), citedUrls);
        NumericFinding currentRatio = verify(raw.currentRatio(), citedUrls);
        SourceReference moatAssessment = verify(raw.moatAssessment(), citedUrls);
        SourceReference managementQuality = verify(raw.managementQuality(), citedUrls);
        SourceReference valueTrapAssessment = verify(raw.valueTrapAssessment(), citedUrls);

        if (marginTrend == null && freeCashFlowTrend == null && profitStability == null
                && interestCoverage == null && currentRatio == null && moatAssessment == null
                && managementQuality == null && valueTrapAssessment == null) {
            return CompanyResearchResult.lowConfidence(ticker,
                    "Model returned sources that could not be verified against actual search results.");
        }

        return new CompanyResearchResult(
                ticker, marginTrend, freeCashFlowTrend, profitStability, interestCoverage, currentRatio,
                moatAssessment, managementQuality, valueTrapAssessment, ConfidenceLevel.HIGH, null,
                CompanyResearchResult.CURRENT_PROMPT_VERSION);
    }

    public QuickResearchResult quickResearch(String ticker, String companyName) {
        ChatResponse response = callWithTimeout(
                ticker, quickPromptBuilder.build(ticker, companyName), STAGE1_MAX_USES, STAGE1_EFFORT);
        logUsage(ticker, response);

        RawQuickResearchResponse raw =
                parse(response.getResult().getOutput().getText(), RawQuickResearchResponse.class);

        if (raw.noReliableDataFound()) {
            return QuickResearchResult.noData(ticker,
                    raw.noReliableDataFoundReason() == null || raw.noReliableDataFoundReason().isBlank()
                            ? "No reliable current key-statistics page found for this ticker."
                            : raw.noReliableDataFoundReason());
        }

        Set<String> citedUrls = extractCitedUrls(response);

        NumericFinding currentPe = verify(raw.currentPe(), citedUrls);
        NumericFinding currentPb = verify(raw.currentPb(), citedUrls);
        NumericFinding fiveYearAveragePe = verify(raw.fiveYearAveragePe(), citedUrls);
        NumericFinding fiveYearAveragePb = verify(raw.fiveYearAveragePb(), citedUrls);
        NumericFinding roe = verify(raw.roe(), citedUrls);
        NumericFinding debtToEquity = verify(raw.debtToEquity(), citedUrls);
        NumericFinding currentRatio = verify(raw.currentRatio(), citedUrls);
        NumericFinding currentYearNetMargin = verify(raw.currentYearNetMargin(), citedUrls);
        BooleanFinding currentYearFcfPositive = verify(raw.currentYearFcfPositive(), citedUrls);
        BooleanFinding currentYearNetIncomeGrew = verify(raw.currentYearNetIncomeGrew(), citedUrls);
        NumericFinding insiderOwnershipShare = verify(raw.insiderOwnershipShare(), citedUrls);

        if (currentPe == null && currentPb == null && fiveYearAveragePe == null && fiveYearAveragePb == null
                && roe == null && debtToEquity == null && currentRatio == null && currentYearNetMargin == null
                && currentYearFcfPositive == null && currentYearNetIncomeGrew == null
                && insiderOwnershipShare == null) {
            return QuickResearchResult.noData(ticker,
                    "Model returned figures that could not be verified against actual search results.");
        }

        return new QuickResearchResult(
                ticker, currentPe, currentPb, fiveYearAveragePe, fiveYearAveragePb, roe, debtToEquity,
                currentRatio, currentYearNetMargin, currentYearFcfPositive, currentYearNetIncomeGrew,
                insiderOwnershipShare, false, null, QuickResearchResult.CURRENT_PROMPT_VERSION);
    }

    private ChatResponse callWithTimeout(String ticker, String promptText, long maxUses,
                                          OutputConfig.Effort effort) {
        Prompt prompt = new Prompt(
                promptText,
                AnthropicChatOptions.builder()
                        .model(Model.of(model))
                        .webSearchTool(AnthropicWebSearchTool.builder()
                                .maxUses(maxUses)
                                .allowedDomains(allowedDomains)
                                .build())
                        .effort(effort)
                        .thinkingDisabled()
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

    private <T> T parse(String responseText, Class<T> type) {
        try {
            return objectMapper.readValue(responseText, type);
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

    private SourceReference verify(RawSourceReference raw, Set<String> citedUrls) {
        if (raw == null || raw.url() == null || raw.claim() == null || raw.claim().isBlank()
                || !citedUrls.contains(raw.url())) {
            return null;
        }
        return new SourceReference(raw.url(), raw.claim());
    }

    private NumericFinding verify(RawNumericFinding raw, Set<String> citedUrls) {
        if (raw == null || raw.value() == null) {
            return null;
        }
        SourceReference source = verify(new RawSourceReference(raw.url(), raw.claim()), citedUrls);
        return source == null ? null : new NumericFinding(raw.value(), source);
    }

    private BooleanFinding verify(RawBooleanFinding raw, Set<String> citedUrls) {
        if (raw == null || raw.value() == null) {
            return null;
        }
        SourceReference source = verify(new RawSourceReference(raw.url(), raw.claim()), citedUrls);
        return source == null ? null : new BooleanFinding(raw.value(), source);
    }
}
