package com.valuescreener.research.agent;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.anthropic.models.messages.OutputConfig;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.valuescreener.research.model.CompanyResearchResult;
import com.valuescreener.research.model.ConfidenceLevel;
import com.valuescreener.research.model.QuickResearchResult;
import com.valuescreener.research.model.Stage1Snapshot;
import com.valuescreener.research.prompt.QuickResearchPromptBuilder;
import com.valuescreener.research.prompt.ResearchPromptBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CompanyResearchAgentTest {

    private static final String[] TEST_ALLOWED_DOMAINS =
            {"sec.gov", "stockanalysis.com", "investor.example.com", "finance.example.com"};

    private final ChatModel chatModel = mock(ChatModel.class);
    private final CompanyResearchAgent agent = newAgent(chatModel, 55);

    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void attachLogAppender() {
        logAppender = new ListAppender<>();
        logAppender.start();
        agentLogger().addAppender(logAppender);
    }

    @AfterEach
    void detachLogAppender() {
        agentLogger().detachAppender(logAppender);
    }

    private Logger agentLogger() {
        return (Logger) LoggerFactory.getLogger(CompanyResearchAgent.class);
    }

    private static CompanyResearchAgent newAgent(ChatModel chatModel, long timeoutSeconds) {
        return new CompanyResearchAgent(chatModel, new ResearchPromptBuilder(), new QuickResearchPromptBuilder(),
                new ObjectMapper(), timeoutSeconds, "claude-sonnet-5", 3, TEST_ALLOWED_DOMAINS);
    }

    // ---- Stage 2: research() ----

    @Test
    void logsTokenUsageAfterASuccessfulStage2Call() {
        String responseJson = """
                {
                  "marginTrend": {"url": "https://investor.example.com/q2-2026", "claim": "Margins held steady around 20%"},
                  "noReliableReportFound": false
                }
                """;
        Usage usage = mock(Usage.class);
        when(usage.getPromptTokens()).thenReturn(3200);
        when(usage.getCompletionTokens()).thenReturn(7800);
        when(usage.getTotalTokens()).thenReturn(11000);
        when(usage.getCacheReadInputTokens()).thenReturn(0L);
        when(usage.getCacheWriteInputTokens()).thenReturn(0L);
        stubChatModelResponse(responseJson, usage);

        agent.research("EXMP", "Example Corp", null);

        assertThat(logAppender.list)
                .anyMatch(event -> event.getLevel() == Level.INFO
                        && event.getFormattedMessage().contains("promptTokens=3200")
                        && event.getFormattedMessage().contains("completionTokens=7800"));
    }

    @Test
    void warnsInsteadOfFailingWhenUsageMetadataIsMissing() {
        String responseJson = """
                {
                  "marginTrend": {"url": "https://investor.example.com/q2-2026", "claim": "Margins held steady around 20%"},
                  "noReliableReportFound": false
                }
                """;
        stubChatModelResponse(responseJson, null);

        CompanyResearchResult result = agent.research("EXMP", "Example Corp", null);

        assertThat(result.confidence()).isEqualTo(ConfidenceLevel.HIGH);
        assertThat(logAppender.list)
                .anyMatch(event -> event.getLevel() == Level.WARN
                        && event.getFormattedMessage().contains("No usage metadata returned"));
    }

    @Test
    void returnsHighConfidenceResultWithAllCriteriaVerifiedAgainstAllowedDomains() {
        String responseJson = """
                {
                  "marginTrend": {"url": "https://investor.example.com/q2-2026", "claim": "Margins held steady around 20%"},
                  "freeCashFlowTrend": {"url": "https://investor.example.com/q2-2026", "claim": "FCF grew 8% year over year"},
                  "moatAssessment": {"url": "https://investor.example.com/q2-2026", "claim": "Brand strength supports pricing power"},
                  "valueTrapAssessment": {"url": "https://investor.example.com/q2-2026", "claim": "No structural headwinds mentioned"},
                  "noReliableReportFound": false
                }
                """;
        stubChatModelResponse(responseJson);

        CompanyResearchResult result = agent.research("EXMP", "Example Corp", null);

        assertThat(result.confidence()).isEqualTo(ConfidenceLevel.HIGH);
        assertThat(result.marginTrend().url()).isEqualTo("https://investor.example.com/q2-2026");
        assertThat(result.freeCashFlowTrend()).isNotNull();
        assertThat(result.moatAssessment()).isNotNull();
        assertThat(result.valueTrapAssessment()).isNotNull();
    }

    @Test
    void onlyDropsTheUnverifiedCriterionWithoutFailingTheWholeResult() {
        String responseJson = """
                {
                  "marginTrend": {"url": "https://investor.example.com/q2-2026", "claim": "Margins held steady around 20%"},
                  "freeCashFlowTrend": {"url": "https://not-actually-searched.example.com", "claim": "FCF grew 8%"},
                  "noReliableReportFound": false
                }
                """;
        stubChatModelResponse(responseJson);

        CompanyResearchResult result = agent.research("EXMP", "Example Corp", null);

        assertThat(result.confidence()).isEqualTo(ConfidenceLevel.HIGH);
        assertThat(result.marginTrend()).isNotNull();
        assertThat(result.freeCashFlowTrend()).isNull();
    }

    @Test
    void fallsBackToLowConfidenceWhenNoCriterionCanBeVerifiedAtAll() {
        String responseJson = """
                {
                  "marginTrend": {"url": "https://not-actually-searched.example.com", "claim": "Margins held steady"},
                  "noReliableReportFound": false
                }
                """;
        stubChatModelResponse(responseJson);

        CompanyResearchResult result = agent.research("EXMP", "Example Corp", null);

        assertThat(result.confidence()).isEqualTo(ConfidenceLevel.LOW);
        assertThat(result.marginTrend()).isNull();
    }

    @Test
    void returnsLowConfidenceWhenModelReportsNoReliableReport() {
        String responseJson = """
                {
                  "noReliableReportFound": true,
                  "noReliableReportFoundReason": "No recent quarterly filing found for this ticker."
                }
                """;
        stubChatModelResponse(responseJson);

        CompanyResearchResult result = agent.research("EXMP", "Example Corp", null);

        assertThat(result.confidence()).isEqualTo(ConfidenceLevel.LOW);
        assertThat(result.lowConfidenceReason()).isEqualTo("No recent quarterly filing found for this ticker.");
    }

    @Test
    void throwsParseExceptionWhenFinalAnswerIsNotValidJson() {
        stubChatModelResponse("not json at all");

        assertThatThrownBy(() -> agent.research("EXMP", "Example Corp", null))
                .isInstanceOf(ResearchResponseParseException.class);
    }

    @Test
    void throwsParseExceptionInsteadOfNpeWhenChatResponseHasNoResult() {
        ChatResponseMetadata metadata = mock(ChatResponseMetadata.class);
        when(metadata.getUsage()).thenReturn(null);

        ChatResponse response = mock(ChatResponse.class);
        when(response.getResult()).thenReturn(null);
        when(response.getMetadata()).thenReturn(metadata);
        when(chatModel.call(any(Prompt.class))).thenReturn(response);

        assertThatThrownBy(() -> agent.research("EXMP", "Example Corp", null))
                .isInstanceOf(ResearchResponseParseException.class);
    }

    @Test
    void throwsResearchTimeoutExceptionWhenChatModelCallExceedsTimeout() {
        ChatModel slowChatModel = mock(ChatModel.class);
        when(slowChatModel.call(any(Prompt.class))).thenAnswer(invocation -> {
            Thread.sleep(500);
            throw new IllegalStateException("should have timed out before returning");
        });
        CompanyResearchAgent agentWithShortTimeout = newAgent(slowChatModel, 0);

        assertThatThrownBy(() -> agentWithShortTimeout.research("EXMP", "Example Corp", null))
                .isInstanceOf(ResearchTimeoutException.class);
    }

    @Test
    void ignoresUnknownJsonFieldsInModelResponse() {
        ObjectMapper lenientMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        CompanyResearchAgent lenientAgent = new CompanyResearchAgent(chatModel, new ResearchPromptBuilder(),
                new QuickResearchPromptBuilder(), lenientMapper, 55, "claude-sonnet-5", 5, TEST_ALLOWED_DOMAINS);
        String responseJson = """
                {
                  "marginTrend": {"url": "https://investor.example.com/q2-2026", "claim": "Margins held steady around 20%"},
                  "noReliableReportFound": false,
                  "unexpectedNewField": "some future model output"
                }
                """;
        stubChatModelResponse(responseJson);

        CompanyResearchResult result = lenientAgent.research("EXMP", "Example Corp", null);

        assertThat(result.confidence()).isEqualTo(ConfidenceLevel.HIGH);
    }

    @Test
    void treatsCriterionWithBlankClaimAsUnverified() {
        String responseJson = """
                {
                  "marginTrend": {"url": "https://investor.example.com/q2-2026", "claim": ""},
                  "noReliableReportFound": false
                }
                """;
        stubChatModelResponse(responseJson);

        CompanyResearchResult result = agent.research("EXMP", "Example Corp", null);

        assertThat(result.confidence()).isEqualTo(ConfidenceLevel.LOW);
        assertThat(result.marginTrend()).isNull();
    }

    @Test
    void configuresStage2WebSearchWithBoundedUsesAndAllowedDomains() {
        String responseJson = """
                {
                  "marginTrend": {"url": "https://investor.example.com/q2-2026", "claim": "Margins held steady around 20%"},
                  "noReliableReportFound": false
                }
                """;
        stubChatModelResponse(responseJson);

        agent.research("EXMP", "Example Corp", null);

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(promptCaptor.capture());
        AnthropicChatOptions options = (AnthropicChatOptions) promptCaptor.getValue().getOptions();
        assertThat(options.getWebSearchTool().getMaxUses()).isEqualTo(3L);
        assertThat(options.getWebSearchTool().getAllowedDomains())
                .containsExactly("sec.gov", "stockanalysis.com", "investor.example.com", "finance.example.com");
    }

    @Test
    void usesExplicitBoundedEffortAndDisablesThinkingForStage2() {
        String responseJson = """
                {
                  "marginTrend": {"url": "https://investor.example.com/q2-2026", "claim": "Margins held steady around 20%"},
                  "noReliableReportFound": false
                }
                """;
        stubChatModelResponse(responseJson);

        agent.research("EXMP", "Example Corp", null);

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(promptCaptor.capture());
        AnthropicChatOptions options = (AnthropicChatOptions) promptCaptor.getValue().getOptions();
        assertThat(options.getOutputConfig()).isNotNull();
        assertThat(options.getOutputConfig().effort()).contains(OutputConfig.Effort.LOW);
        assertThat(options.getThinking().isDisabled()).isTrue();
    }

    @Test
    void passesStage1SnapshotValuesIntoTheStage2Prompt() {
        String responseJson = """
                {
                  "marginTrend": {"url": "https://investor.example.com/q2-2026", "claim": "Margins held steady around 20%"},
                  "noReliableReportFound": false
                }
                """;
        stubChatModelResponse(responseJson);

        agent.research("EXMP", "Example Corp", new Stage1Snapshot(24.3, 3.1));

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(promptCaptor.capture());
        String promptText = promptCaptor.getValue().getInstructions().get(0).getText();
        assertThat(promptText).contains("24.3").contains("3.1");
    }

    // ---- Stage 1: quickResearch() ----

    @Test
    void quickResearchReturnsVerifiedSnapshotOnSuccess() {
        String responseJson = """
                {
                  "currentPe": {"value": 24.3, "url": "https://finance.example.com/EXMP", "claim": "P/E of 24.3 on the key-statistics page"},
                  "currentYearFcfPositive": {"value": true, "url": "https://finance.example.com/EXMP", "claim": "FCF was positive this year"},
                  "noReliableDataFound": false
                }
                """;
        stubChatModelResponse(responseJson);

        QuickResearchResult result = agent.quickResearch("EXMP", "Example Corp");

        assertThat(result.noReliableDataFound()).isFalse();
        assertThat(result.currentPe().value()).isEqualTo(24.3);
        assertThat(result.currentYearFcfPositive().value()).isTrue();
        assertThat(result.currentPb()).isNull();
    }

    @Test
    void quickResearchReturnsNoDataFlagWhenModelReportsNoReliableSnapshot() {
        String responseJson = """
                {
                  "noReliableDataFound": true,
                  "noReliableDataFoundReason": "No current key-statistics page found for this ticker."
                }
                """;
        stubChatModelResponse(responseJson);

        QuickResearchResult result = agent.quickResearch("EXMP", "Example Corp");

        assertThat(result.noReliableDataFound()).isTrue();
        assertThat(result.noReliableDataFoundReason())
                .isEqualTo("No current key-statistics page found for this ticker.");
    }

    @Test
    void quickResearchOnlyDropsTheUnverifiedFieldWithoutFailingTheWholeResult() {
        String responseJson = """
                {
                  "currentPe": {"value": 24.3, "url": "https://finance.example.com/EXMP", "claim": "P/E of 24.3"},
                  "roe": {"value": 18.5, "url": "https://not-actually-searched.example.com", "claim": "ROE of 18.5%"},
                  "noReliableDataFound": false
                }
                """;
        stubChatModelResponse(responseJson);

        QuickResearchResult result = agent.quickResearch("EXMP", "Example Corp");

        assertThat(result.noReliableDataFound()).isFalse();
        assertThat(result.currentPe()).isNotNull();
        assertThat(result.roe()).isNull();
    }

    @Test
    void quickResearchCapsWebSearchToASingleBoundedUse() {
        String responseJson = """
                {
                  "currentPe": {"value": 24.3, "url": "https://finance.example.com/EXMP", "claim": "P/E of 24.3"},
                  "noReliableDataFound": false
                }
                """;
        stubChatModelResponse(responseJson);

        agent.quickResearch("EXMP", "Example Corp");

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(promptCaptor.capture());
        AnthropicChatOptions options = (AnthropicChatOptions) promptCaptor.getValue().getOptions();
        assertThat(options.getWebSearchTool().getMaxUses()).isEqualTo(1L);
        assertThat(options.getWebSearchTool().getAllowedDomains())
                .containsExactly("sec.gov", "stockanalysis.com", "investor.example.com", "finance.example.com");
    }

    @Test
    void extractsTheFinalJsonBlockWhenModelReasonsInProseAndWrapsAnswerInMarkdownFences() {
        String responseText = """
                I'll use the primary statistics page. Let me draft a first pass.

                ```json
                {"roe": {"value": 999.0, "url": "https://finance.example.com/EXMP", "claim": "draft, ignore"}, "noReliableDataFound": false}
                ```

                Let me produce the final strict JSON per the requested schema, omitting unavailable fields.

                ```json
                {
                  "roe": {"value": 18.5, "url": "https://finance.example.com/EXMP", "claim": "ROE of 18.5% per the statistics page"},
                  "noReliableDataFound": false
                }
                ```
                """;
        stubChatModelResponse(responseText);

        QuickResearchResult result = agent.quickResearch("EXMP", "Example Corp");

        assertThat(result.noReliableDataFound()).isFalse();
        assertThat(result.roe().value()).isEqualTo(18.5);
    }

    @Test
    void usesExplicitBoundedEffortAndDisablesThinkingForStage1() {
        String responseJson = """
                {
                  "currentPe": {"value": 24.3, "url": "https://finance.example.com/EXMP", "claim": "P/E of 24.3"},
                  "noReliableDataFound": false
                }
                """;
        stubChatModelResponse(responseJson);

        agent.quickResearch("EXMP", "Example Corp");

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(promptCaptor.capture());
        AnthropicChatOptions options = (AnthropicChatOptions) promptCaptor.getValue().getOptions();
        assertThat(options.getOutputConfig()).isNotNull();
        assertThat(options.getOutputConfig().effort()).contains(OutputConfig.Effort.LOW);
        assertThat(options.getThinking().isDisabled()).isTrue();
    }

    private void stubChatModelResponse(String responseText) {
        Usage defaultUsage = mock(Usage.class);
        when(defaultUsage.getPromptTokens()).thenReturn(500);
        when(defaultUsage.getCompletionTokens()).thenReturn(500);
        when(defaultUsage.getTotalTokens()).thenReturn(1000);
        when(defaultUsage.getCacheReadInputTokens()).thenReturn(0L);
        when(defaultUsage.getCacheWriteInputTokens()).thenReturn(0L);
        stubChatModelResponse(responseText, defaultUsage);
    }

    // Real Sonnet 5 calls that retrieve search results via code_execution-mediated web_search
    // (Programmatic Tool Calling) come back with no citations metadata at all -- confirmed via a
    // live call for AAPL. Deliberately not stubbing "citations" here reproduces that: verification
    // must succeed purely from the claimed source's domain being on the allow-list, with no
    // citation metadata present.
    private void stubChatModelResponse(String responseText, Usage usage) {
        AssistantMessage output = mock(AssistantMessage.class);
        when(output.getText()).thenReturn(responseText);

        Generation generation = mock(Generation.class);
        when(generation.getOutput()).thenReturn(output);

        ChatResponseMetadata metadata = mock(ChatResponseMetadata.class);
        when(metadata.getUsage()).thenReturn(usage);

        ChatResponse response = mock(ChatResponse.class);
        when(response.getResult()).thenReturn(generation);
        when(response.getMetadata()).thenReturn(metadata);

        when(chatModel.call(any(Prompt.class))).thenReturn(response);
    }
}
