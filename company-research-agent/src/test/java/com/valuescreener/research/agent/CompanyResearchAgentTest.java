package com.valuescreener.research.agent;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.valuescreener.research.model.CompanyResearchResult;
import com.valuescreener.research.model.ConfidenceLevel;
import com.valuescreener.research.prompt.ResearchPromptBuilder;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.Citation;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CompanyResearchAgentTest {

    private final ChatModel chatModel = mock(ChatModel.class);
    private final CompanyResearchAgent agent =
            new CompanyResearchAgent(chatModel, new ResearchPromptBuilder(), new ObjectMapper(), 55, "claude-sonnet-5", 5);

    @Test
    void returnsHighConfidenceResultWithSourcesVerifiedAgainstCitations() {
        String responseJson = """
                {
                  "summary": "Revenue grew 8% year over year.",
                  "valueTrapAssessment": "Management commentary cites no structural headwinds.",
                  "sources": [{"url": "https://investor.example.com/q2-2026", "claim": "Revenue grew 8%"}],
                  "noReliableReportFound": false
                }
                """;
        stubChatModelResponse(responseJson, List.of("https://investor.example.com/q2-2026"));

        CompanyResearchResult result = agent.research("EXMP", "Example Corp");

        assertThat(result.confidence()).isEqualTo(ConfidenceLevel.HIGH);
        assertThat(result.sources()).hasSize(1);
        assertThat(result.sources().get(0).url()).isEqualTo("https://investor.example.com/q2-2026");
    }

    @Test
    void dropsSourcesNotBackedByActualCitationsAndFallsBackToLowConfidence() {
        String responseJson = """
                {
                  "summary": "Revenue grew 8% year over year.",
                  "valueTrapAssessment": "No structural headwinds mentioned.",
                  "sources": [{"url": "https://not-actually-searched.example.com", "claim": "Revenue grew 8%"}],
                  "noReliableReportFound": false
                }
                """;
        stubChatModelResponse(responseJson, List.of("https://investor.example.com/q2-2026"));

        CompanyResearchResult result = agent.research("EXMP", "Example Corp");

        assertThat(result.confidence()).isEqualTo(ConfidenceLevel.LOW);
        assertThat(result.sources()).isEmpty();
    }

    @Test
    void returnsLowConfidenceWhenModelReportsNoReliableReport() {
        String responseJson = """
                {
                  "summary": "No recent quarterly filing found for this ticker.",
                  "valueTrapAssessment": "",
                  "sources": [],
                  "noReliableReportFound": true
                }
                """;
        stubChatModelResponse(responseJson, List.of());

        CompanyResearchResult result = agent.research("EXMP", "Example Corp");

        assertThat(result.confidence()).isEqualTo(ConfidenceLevel.LOW);
        assertThat(result.summary()).isEqualTo("No recent quarterly filing found for this ticker.");
    }

    @Test
    void throwsParseExceptionWhenFinalAnswerIsNotValidJson() {
        stubChatModelResponse("not json at all", List.of());

        assertThatThrownBy(() -> agent.research("EXMP", "Example Corp"))
                .isInstanceOf(ResearchResponseParseException.class);
    }

    @Test
    void throwsResearchTimeoutExceptionWhenChatModelCallExceedsTimeout() {
        ChatModel slowChatModel = mock(ChatModel.class);
        when(slowChatModel.call(any(Prompt.class))).thenAnswer(invocation -> {
            Thread.sleep(500);
            throw new IllegalStateException("should have timed out before returning");
        });
        CompanyResearchAgent agentWithShortTimeout =
                new CompanyResearchAgent(slowChatModel, new ResearchPromptBuilder(), new ObjectMapper(), 0, "claude-sonnet-5", 5);

        assertThatThrownBy(() -> agentWithShortTimeout.research("EXMP", "Example Corp"))
                .isInstanceOf(ResearchTimeoutException.class);
    }

    @Test
    void ignoresUnknownJsonFieldsInModelResponse() {
        ObjectMapper lenientMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        CompanyResearchAgent lenientAgent =
                new CompanyResearchAgent(chatModel, new ResearchPromptBuilder(), lenientMapper, 55, "claude-sonnet-5", 5);
        String responseJson = """
                {
                  "summary": "Revenue grew 8% year over year.",
                  "valueTrapAssessment": "No structural headwinds mentioned.",
                  "sources": [{"url": "https://investor.example.com/q2-2026", "claim": "Revenue grew 8%"}],
                  "noReliableReportFound": false,
                  "unexpectedNewField": "some future model output"
                }
                """;
        stubChatModelResponse(responseJson, List.of("https://investor.example.com/q2-2026"));

        CompanyResearchResult result = lenientAgent.research("EXMP", "Example Corp");

        assertThat(result.confidence()).isEqualTo(ConfidenceLevel.HIGH);
    }

    @Test
    void dropsSourcesWithBlankClaimAndFallsBackToLowConfidence() {
        String responseJson = """
                {
                  "summary": "Revenue grew 8% year over year.",
                  "valueTrapAssessment": "No structural headwinds mentioned.",
                  "sources": [{"url": "https://investor.example.com/q2-2026", "claim": ""}],
                  "noReliableReportFound": false
                }
                """;
        stubChatModelResponse(responseJson, List.of("https://investor.example.com/q2-2026"));

        CompanyResearchResult result = agent.research("EXMP", "Example Corp");

        assertThat(result.confidence()).isEqualTo(ConfidenceLevel.LOW);
        assertThat(result.sources()).isEmpty();
    }

    @Test
    void capsWebSearchUsesToBoundCostAndLatencyPerRequest() {
        String responseJson = """
                {
                  "summary": "Revenue grew 8% year over year.",
                  "valueTrapAssessment": "No structural headwinds mentioned.",
                  "sources": [{"url": "https://investor.example.com/q2-2026", "claim": "Revenue grew 8%"}],
                  "noReliableReportFound": false
                }
                """;
        stubChatModelResponse(responseJson, List.of("https://investor.example.com/q2-2026"));

        agent.research("EXMP", "Example Corp");

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(promptCaptor.capture());
        AnthropicChatOptions options = (AnthropicChatOptions) promptCaptor.getValue().getOptions();
        assertThat(options.getWebSearchTool().getMaxUses()).isEqualTo(5L);
    }

    private void stubChatModelResponse(String responseText, List<String> citedUrls) {
        AssistantMessage output = mock(AssistantMessage.class);
        when(output.getText()).thenReturn(responseText);

        Generation generation = mock(Generation.class);
        when(generation.getOutput()).thenReturn(output);

        List<Citation> citations = citedUrls.stream()
                .map(url -> {
                    Citation citation = mock(Citation.class);
                    when(citation.getUrl()).thenReturn(url);
                    return citation;
                })
                .toList();

        ChatResponseMetadata metadata = mock(ChatResponseMetadata.class);
        when(metadata.get("citations")).thenReturn(citations);

        ChatResponse response = mock(ChatResponse.class);
        when(response.getResult()).thenReturn(generation);
        when(response.getMetadata()).thenReturn(metadata);

        when(chatModel.call(any(Prompt.class))).thenReturn(response);
    }
}
