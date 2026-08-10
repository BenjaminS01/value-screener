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
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class CompanyResearchAgent {

    private static final Logger log = LoggerFactory.getLogger(CompanyResearchAgent.class);

    // Fixed per the agent spec's Section 3 (Stage 1 mechanism decision) rather than configurable
    // like Stage 2's webSearchMaxUses -- one bounded search step is the whole point of Stage 1.
    private static final long STAGE1_MAX_USES = 1L;
    private static final OutputConfig.Effort STAGE1_EFFORT = OutputConfig.Effort.LOW;
    private static final OutputConfig.Effort STAGE2_EFFORT = OutputConfig.Effort.LOW;

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
                                 @Value("${research.agent.allowed-domains:sec.gov,www.sec.gov,stockanalysis.com,marketscreener.com,finance.yahoo.com,morningstar.com,macrotrends.net,boerse-frankfurt.de,finanzen.net,globenewswire.com,prnewswire.com,businesswire.com}")
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

        RawResearchResponse raw = parse(extractText(response), RawResearchResponse.class);

        if (raw.noReliableReportFound()) {
            return CompanyResearchResult.lowConfidence(ticker,
                    raw.noReliableReportFoundReason() == null || raw.noReliableReportFoundReason().isBlank()
                            ? "No reliable current report found for this ticker."
                            : raw.noReliableReportFoundReason());
        }

        SourceReference marginTrend = verify(raw.marginTrend());
        SourceReference freeCashFlowTrend = verify(raw.freeCashFlowTrend());
        SourceReference profitStability = verify(raw.profitStability());
        NumericFinding interestCoverage = verify(raw.interestCoverage());
        NumericFinding currentRatio = verify(raw.currentRatio());
        SourceReference moatAssessment = verify(raw.moatAssessment());
        SourceReference managementQuality = verify(raw.managementQuality());
        SourceReference valueTrapAssessment = verify(raw.valueTrapAssessment());

        if (marginTrend == null && freeCashFlowTrend == null && profitStability == null
                && interestCoverage == null && currentRatio == null && moatAssessment == null
                && managementQuality == null && valueTrapAssessment == null) {
            boolean rawHadNoCriteria = raw.marginTrend() == null && raw.freeCashFlowTrend() == null
                    && raw.profitStability() == null && raw.interestCoverage() == null
                    && raw.currentRatio() == null && raw.moatAssessment() == null
                    && raw.managementQuality() == null && raw.valueTrapAssessment() == null;
            return CompanyResearchResult.lowConfidence(ticker, rawHadNoCriteria
                    ? "Model did not return any criteria for this ticker."
                    : "Model returned sources whose domain is not on the trusted allow-list.");
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
                parse(extractText(response), RawQuickResearchResponse.class);

        if (raw.noReliableDataFound()) {
            return QuickResearchResult.noData(ticker,
                    raw.noReliableDataFoundReason() == null || raw.noReliableDataFoundReason().isBlank()
                            ? "No reliable current key-statistics page found for this ticker."
                            : raw.noReliableDataFoundReason());
        }

        NumericFinding currentPe = verify(raw.currentPe());
        NumericFinding currentPb = verify(raw.currentPb());
        NumericFinding fiveYearAveragePe = verify(raw.fiveYearAveragePe());
        NumericFinding fiveYearAveragePb = verify(raw.fiveYearAveragePb());
        NumericFinding roe = verify(raw.roe());
        NumericFinding debtToEquity = verify(raw.debtToEquity());
        NumericFinding currentRatio = verify(raw.currentRatio());
        NumericFinding currentYearNetMargin = verify(raw.currentYearNetMargin());
        BooleanFinding currentYearFcfPositive = verify(raw.currentYearFcfPositive());
        BooleanFinding currentYearNetIncomeGrew = verify(raw.currentYearNetIncomeGrew());
        NumericFinding insiderOwnershipShare = verify(raw.insiderOwnershipShare());

        if (currentPe == null && currentPb == null && fiveYearAveragePe == null && fiveYearAveragePb == null
                && roe == null && debtToEquity == null && currentRatio == null && currentYearNetMargin == null
                && currentYearFcfPositive == null && currentYearNetIncomeGrew == null
                && insiderOwnershipShare == null) {
            boolean rawHadNoFigures = raw.currentPe() == null && raw.currentPb() == null
                    && raw.fiveYearAveragePe() == null && raw.fiveYearAveragePb() == null
                    && raw.roe() == null && raw.debtToEquity() == null && raw.currentRatio() == null
                    && raw.currentYearNetMargin() == null && raw.currentYearFcfPositive() == null
                    && raw.currentYearNetIncomeGrew() == null && raw.insiderOwnershipShare() == null;
            return QuickResearchResult.noData(ticker, rawHadNoFigures
                    ? "Model did not return any figures for this ticker."
                    : "Model returned figures whose domain is not on the trusted allow-list.");
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

    private String extractText(ChatResponse response) {
        if (response.getResult() == null || response.getResult().getOutput() == null) {
            throw new ResearchResponseParseException("Model returned no answer content", null);
        }
        return response.getResult().getOutput().getText();
    }

    // Confirmed via a real live call: despite being told to answer with ONLY JSON, the model
    // sometimes reasons in prose first (especially when it uses code_execution along the way)
    // and/or wraps its answer -- including an earlier, discarded draft -- in markdown code
    // fences. Prompt wording alone did not prevent this, so parsing has to tolerate it: take the
    // LAST fenced JSON block (the model's own "final answer", per its own wording), falling back
    // to the first-to-last brace span for the plain, unfenced case.
    // Non-greedy on the fence content itself (not on brace-matching): a greedy \{.*} spans
    // past a block's own closing ``` and merges with a LATER block, since Jackson's readValue
    // silently ignores trailing tokens after the first complete JSON value by default -- that
    // combined match would then silently parse as the FIRST (draft) block instead of the last.
    private static final Pattern JSON_FENCE_PATTERN =
            Pattern.compile("```(?:json)?\\s*(.*?)\\s*```", Pattern.DOTALL);

    private String extractJson(String responseText) {
        Matcher matcher = JSON_FENCE_PATTERN.matcher(responseText);
        String lastFencedBlock = null;
        while (matcher.find()) {
            lastFencedBlock = matcher.group(1);
        }
        if (lastFencedBlock != null) {
            return lastFencedBlock;
        }
        int start = responseText.indexOf('{');
        int end = responseText.lastIndexOf('}');
        return start >= 0 && end > start ? responseText.substring(start, end + 1) : responseText;
    }

    private <T> T parse(String responseText, Class<T> type) {
        String jsonText = extractJson(responseText);
        try {
            return objectMapper.readValue(jsonText, type);
        } catch (Exception e) {
            log.error("Could not parse research agent response as JSON. Cause: {}. Raw response text:\n{}",
                    e.getMessage(), responseText);
            throw new ResearchResponseParseException(
                    "Could not parse research agent response as JSON", e);
        }
    }

    // Anthropic's citation metadata does not get attached when the model retrieves web content
    // via code_execution-mediated web_search calls (Programmatic Tool Calling) rather than a
    // direct, visible web_search tool call -- confirmed live for Sonnet 5, which uses PTC by
    // default. So citations can't be used as the verification signal. Falling back to checking
    // the claimed source's domain against the same allowed-domains list the web_search tool
    // itself is restricted to: weaker than confirming the exact URL was actually retrieved, but
    // the API already guarantees any search it performed was scoped to these domains.
    private boolean isAllowedDomain(String url) {
        String host;
        try {
            host = URI.create(url).getHost();
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (host == null) {
            return false;
        }
        String lowerHost = host.toLowerCase(Locale.ROOT);
        return allowedDomains.stream().anyMatch(domain -> domain.equalsIgnoreCase(lowerHost));
    }

    private SourceReference verify(RawSourceReference raw) {
        if (raw == null || raw.url() == null || raw.claim() == null || raw.claim().isBlank()
                || !isAllowedDomain(raw.url())) {
            return null;
        }
        return new SourceReference(raw.url(), raw.claim());
    }

    private NumericFinding verify(RawNumericFinding raw) {
        if (raw == null || raw.value() == null) {
            return null;
        }
        SourceReference source = verify(new RawSourceReference(raw.url(), raw.claim()));
        return source == null ? null : new NumericFinding(raw.value(), source);
    }

    private BooleanFinding verify(RawBooleanFinding raw) {
        if (raw == null || raw.value() == null) {
            return null;
        }
        SourceReference source = verify(new RawSourceReference(raw.url(), raw.claim()));
        return source == null ? null : new BooleanFinding(raw.value(), source);
    }
}
