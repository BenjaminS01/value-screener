# Company Research Agent Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the standalone, serverless MCP server described in
`docs/superpowers/specs/2026-07-24-company-research-agent-design.md` — a `research_company(ticker,
companyName)` tool that researches a company's most recent quarterly report / investor relations
content via Claude with web search, and returns a sourced, descriptive, guardrail-checked result.

**Architecture:** A new, independent Maven module (`company-research-agent/`) inside the
value-screener repo, built with Spring Boot 3 / Java 21. It uses Spring AI's MCP server starter
(`spring-ai-starter-mcp-server-webmvc`, Streamable HTTP protocol) to expose one `@McpTool`, and
Spring AI's Anthropic starter with the built-in web search tool to run the research agent loop
internally. Deployed as an AWS Lambda behind a Function URL via the `aws-serverless-java-container`
adapter — no always-on server, no custom Lambda handler class needed. This module does not depend
on and is not depended on by `backend/` — the only contract between them is the MCP tool interface,
which lets both be developed in parallel (see spec Section 3).

**Tech Stack:** Java 21, Spring Boot 3.4.x, Spring AI 1.1.8 (MCP server WebMVC starter + Anthropic
model starter), JUnit 5 / Mockito / AssertJ, AWS Lambda (Java 21 runtime) + AWS SAM, Jackson.

## Global Constraints

- Java 21, Spring Boot 3.x — consistent with the rest of the value-screener stack (see
  `PROJECT-STATUS.md`).
- Serverless deployment (AWS Lambda + Function URL), not an always-on server (design spec Section
  3) — cost-driven decision, do not introduce an always-on alternative.
- MCP transport is Streamable HTTP, not stdio (design spec Section 3) — required by the Lambda
  request/response model.
- The module owns its own Anthropic API key, separate from the main `backend/` application's key
  (design spec Section 3).
- All AI-generated wording must stay descriptive, never recommending/judgmental (design spec
  Section 5, Guardrail A) — this applies to the prompt instructions, not just the eventual UI.
- No verbatim quotes from source material — paraphrase + link only (design spec Section 5,
  Guardrail D), for the copyright reasons documented in the spec.
- Guardrail B (fact-check against `FundamentalSnapshot`) is explicitly **out of scope** for this
  module — it happens in the main application after it receives this tool's output (spec Section
  5). Do not add `FundamentalSnapshot` awareness here.
- No real personal data (email addresses, etc.) committed to the repo — parameterize instead (see
  Task 9), consistent with the existing project rule in `PROJECT-STATUS.md`.
- **Note on exact package names:** a few Spring AI classes referenced below
  (`AnthropicWebSearchTool`, `Citation`, `@McpTool`, `@McpToolParam`, `CallToolResult`) come from
  fast-moving starters; their exact package may shift slightly between Spring AI patch versions.
  Where a step's "run test to verify" fails with an import/compile error rather than an assertion
  error, use your IDE's symbol search (or `mvn dependency:tree` + browsing the resolved jar) to find
  the correct package for that version, fix the import, and re-run. The class names, method
  signatures, and test assertions below are the source of truth for what the code must do.

---

### Task 1: Module scaffold

**Files:**
- Create: `company-research-agent/pom.xml`
- Create: `company-research-agent/.gitignore`
- Create: `company-research-agent/src/main/java/com/valuescreener/research/ResearchAgentApplication.java`
- Create: `company-research-agent/src/main/resources/application.yml`
- Create: `company-research-agent/src/test/resources/application.yml`
- Test: `company-research-agent/src/test/java/com/valuescreener/research/ResearchAgentApplicationTests.java`

**Interfaces:**
- Produces: a bootable Spring Boot module at `company-research-agent/`, independent of `backend/`.

- [ ] **Step 1: Create the module directory and `.gitignore`**

```bash
mkdir -p company-research-agent/src/main/java/com/valuescreener/research
mkdir -p company-research-agent/src/main/resources
mkdir -p company-research-agent/src/test/java/com/valuescreener/research
mkdir -p company-research-agent/src/test/resources
```

`company-research-agent/.gitignore`:
```
target/
*.class
.idea/
*.iml
.aws-sam/
```

- [ ] **Step 2: Write `pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.4.1</version>
        <relativePath/>
    </parent>

    <groupId>com.valuescreener</groupId>
    <artifactId>company-research-agent</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <name>company-research-agent</name>
    <description>Standalone MCP server researching quarterly reports/company context for the Value Screener</description>

    <properties>
        <java.version>21</java.version>
        <spring-ai.version>1.1.8</spring-ai.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.ai</groupId>
                <artifactId>spring-ai-bom</artifactId>
                <version>${spring-ai.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <dependencies>
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-starter-mcp-server-webmvc</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-starter-model-anthropic</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <configuration>
                    <excludedGroups>eval</excludedGroups>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

Before running the next step, check Maven Central for the current latest stable (non-milestone)
`spring-ai-bom` version and adjust `spring-ai.version` if a newer one exists — `1.1.8` is the
latest confirmed stable version as of writing this plan.

- [ ] **Step 3: Write the application class**

`company-research-agent/src/main/java/com/valuescreener/research/ResearchAgentApplication.java`:
```java
package com.valuescreener.research;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ResearchAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(ResearchAgentApplication.class, args);
    }
}
```

- [ ] **Step 4: Write main and test configuration**

`company-research-agent/src/main/resources/application.yml`:
```yaml
spring:
  application:
    name: company-research-agent
  ai:
    anthropic:
      api-key: ${ANTHROPIC_API_KEY}
    mcp:
      server:
        name: company-research-agent
        version: 0.1.0
        protocol: STREAMABLE
        type: SYNC
        annotation-scanner:
          enabled: true

research:
  agent:
    timeout-seconds: 55
```

`company-research-agent/src/test/resources/application.yml` (dummy key so the Spring context can
load in tests without a real network credential — unit tests never call the real API):
```yaml
spring:
  ai:
    anthropic:
      api-key: test-key-not-used-in-unit-tests
```

- [ ] **Step 5: Write the smoke test**

`company-research-agent/src/test/java/com/valuescreener/research/ResearchAgentApplicationTests.java`:
```java
package com.valuescreener.research;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ResearchAgentApplicationTests {

    @Test
    void contextLoads() {
    }
}
```

- [ ] **Step 6: Run the build and verify it passes**

Run: `cd company-research-agent && mvn test`
Expected: `BUILD SUCCESS`, one test run (`contextLoads`), 0 failures. If it fails on missing/renamed
starter artifacts, check the exact `spring-ai-starter-mcp-server-webmvc` and
`spring-ai-starter-model-anthropic` coordinates for the resolved `spring-ai.version` on Maven
Central and adjust.

- [ ] **Step 7: Commit**

```bash
git add company-research-agent/
git commit -m "feat: scaffold company-research-agent module"
```

---

### Task 2: Domain model

**Files:**
- Create: `company-research-agent/src/main/java/com/valuescreener/research/model/ConfidenceLevel.java`
- Create: `company-research-agent/src/main/java/com/valuescreener/research/model/SourceReference.java`
- Create: `company-research-agent/src/main/java/com/valuescreener/research/model/CompanyResearchResult.java`
- Test: `company-research-agent/src/test/java/com/valuescreener/research/model/CompanyResearchResultTest.java`

**Interfaces:**
- Produces: `CompanyResearchResult` (record), `SourceReference` (record), `ConfidenceLevel` (enum) —
  used by `CompanyResearchAgent` (Task 4) and `CompanyResearchTool` (Task 6).

- [ ] **Step 1: Write the failing test**

`company-research-agent/src/test/java/com/valuescreener/research/model/CompanyResearchResultTest.java`:
```java
package com.valuescreener.research.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompanyResearchResultTest {

    @Test
    void sourcesListIsImmutable() {
        List<SourceReference> mutableSources = new ArrayList<>();
        mutableSources.add(new SourceReference("https://example.com/report", "Revenue grew 5%"));

        CompanyResearchResult result = new CompanyResearchResult(
                "AAPL", "summary", "assessment", mutableSources, ConfidenceLevel.HIGH,
                CompanyResearchResult.CURRENT_PROMPT_VERSION);

        assertThatThrownBy(() -> result.sources().add(
                new SourceReference("https://example.com/other", "Other claim")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void lowConfidenceFactoryReturnsLowConfidenceResultWithNoSources() {
        CompanyResearchResult result = CompanyResearchResult.lowConfidence("AAPL", "No recent filing found");

        assertThat(result.confidence()).isEqualTo(ConfidenceLevel.LOW);
        assertThat(result.sources()).isEmpty();
        assertThat(result.summary()).isEqualTo("No recent filing found");
        assertThat(result.promptVersion()).isEqualTo(CompanyResearchResult.CURRENT_PROMPT_VERSION);
    }

    @Test
    void sourceReferenceRejectsBlankUrl() {
        assertThatThrownBy(() -> new SourceReference(" ", "some claim"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sourceReferenceRejectsBlankClaim() {
        assertThatThrownBy(() -> new SourceReference("https://example.com", " "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd company-research-agent && mvn test -Dtest=CompanyResearchResultTest`
Expected: FAIL — compile error, `ConfidenceLevel`/`SourceReference`/`CompanyResearchResult` don't
exist yet.

- [ ] **Step 3: Write the implementation**

`company-research-agent/src/main/java/com/valuescreener/research/model/ConfidenceLevel.java`:
```java
package com.valuescreener.research.model;

public enum ConfidenceLevel {
    HIGH,
    LOW
}
```

`company-research-agent/src/main/java/com/valuescreener/research/model/SourceReference.java`:
```java
package com.valuescreener.research.model;

public record SourceReference(String url, String claim) {

    public SourceReference {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("url must not be blank");
        }
        if (claim == null || claim.isBlank()) {
            throw new IllegalArgumentException("claim must not be blank");
        }
    }
}
```

`company-research-agent/src/main/java/com/valuescreener/research/model/CompanyResearchResult.java`:
```java
package com.valuescreener.research.model;

import java.util.List;

public record CompanyResearchResult(
        String ticker,
        String summary,
        String valueTrapAssessment,
        List<SourceReference> sources,
        ConfidenceLevel confidence,
        String promptVersion) {

    /**
     * Bumped whenever the research prompt or output contract changes, so that stored analyses
     * from different generations can be told apart when comparing across quarters.
     */
    public static final String CURRENT_PROMPT_VERSION = "research-v1";

    public CompanyResearchResult {
        sources = List.copyOf(sources);
    }

    public static CompanyResearchResult lowConfidence(String ticker, String reason) {
        return new CompanyResearchResult(
                ticker,
                reason,
                "Insufficient sourced information to assess valuation drivers.",
                List.of(),
                ConfidenceLevel.LOW,
                CURRENT_PROMPT_VERSION);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd company-research-agent && mvn test -Dtest=CompanyResearchResultTest`
Expected: PASS, 4 tests green.

- [ ] **Step 5: Commit**

```bash
git add company-research-agent/src/main/java/com/valuescreener/research/model/
git add company-research-agent/src/test/java/com/valuescreener/research/model/
git commit -m "feat: add CompanyResearchResult domain model"
```

---

### Task 3: Prompt builder (Guardrails A, C, D as model instructions)

**Files:**
- Create: `company-research-agent/src/main/java/com/valuescreener/research/prompt/ResearchPromptBuilder.java`
- Test: `company-research-agent/src/test/java/com/valuescreener/research/prompt/ResearchPromptBuilderTest.java`

**Interfaces:**
- Produces: `ResearchPromptBuilder.build(String ticker, String companyName) -> String` — consumed by
  `CompanyResearchAgent` (Task 4).

- [ ] **Step 1: Write the failing test**

`company-research-agent/src/test/java/com/valuescreener/research/prompt/ResearchPromptBuilderTest.java`:
```java
package com.valuescreener.research.prompt;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResearchPromptBuilderTest {

    private final ResearchPromptBuilder builder = new ResearchPromptBuilder();

    @Test
    void includesTickerAndCompanyName() {
        String prompt = builder.build("AAPL", "Apple Inc.");

        assertThat(prompt).contains("AAPL").contains("Apple Inc.");
    }

    @Test
    void instructsDescriptiveNotRecommendingWording() {
        String prompt = builder.build("AAPL", "Apple Inc.");

        assertThat(prompt).contains("never phrase findings as a recommendation");
    }

    @Test
    void instructsParaphraseInsteadOfVerbatimQuotes() {
        String prompt = builder.build("AAPL", "Apple Inc.");

        assertThat(prompt).contains("Do not quote source text verbatim");
    }

    @Test
    void instructsLowConfidenceFlagWhenNoReliableReportExists() {
        String prompt = builder.build("AAPL", "Apple Inc.");

        assertThat(prompt).contains("noReliableReportFound");
    }

    @Test
    void requestsJsonOnlyFinalAnswer() {
        String prompt = builder.build("AAPL", "Apple Inc.");

        assertThat(prompt).contains("\"summary\"")
                .contains("\"valueTrapAssessment\"")
                .contains("\"sources\"");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd company-research-agent && mvn test -Dtest=ResearchPromptBuilderTest`
Expected: FAIL — `ResearchPromptBuilder` doesn't exist yet.

- [ ] **Step 3: Write the implementation**

`company-research-agent/src/main/java/com/valuescreener/research/prompt/ResearchPromptBuilder.java`:
```java
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
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd company-research-agent && mvn test -Dtest=ResearchPromptBuilderTest`
Expected: PASS, 5 tests green.

- [ ] **Step 5: Commit**

```bash
git add company-research-agent/src/main/java/com/valuescreener/research/prompt/
git add company-research-agent/src/test/java/com/valuescreener/research/prompt/
git commit -m "feat: add research prompt builder with wording/sourcing guardrails"
```

---

### Task 4: Research agent core (Claude call, JSON parsing, citation cross-check)

This is where Guardrail D (source-reference requirement) gets technically enforced, not just
requested: any source URL the model claims is discarded unless Anthropic's web search tool actually
returned that URL as a citation. Guardrail C (low-confidence flag) is honored from the model's own
`noReliableReportFound` flag, and additionally forced to LOW if every claimed source fails the
citation cross-check (a model can't be trusted to say "high confidence" if none of its sources are
real).

**Files:**
- Create: `company-research-agent/src/main/java/com/valuescreener/research/agent/CompanyResearchAgent.java`
- Create: `company-research-agent/src/main/java/com/valuescreener/research/agent/RawResearchResponse.java`
- Create: `company-research-agent/src/main/java/com/valuescreener/research/agent/ResearchResponseParseException.java`
- Test: `company-research-agent/src/test/java/com/valuescreener/research/agent/CompanyResearchAgentTest.java`

**Interfaces:**
- Consumes: `ResearchPromptBuilder.build(String, String) -> String` (Task 3), `CompanyResearchResult`
  / `SourceReference` / `ConfidenceLevel` (Task 2).
- Produces: `CompanyResearchAgent.research(String ticker, String companyName) -> CompanyResearchResult`
  — consumed by `CompanyResearchTool` (Task 6). Throws `ResearchResponseParseException` (unchecked)
  if the model's final answer isn't valid JSON matching the expected shape.

- [ ] **Step 1: Write the failing test**

`company-research-agent/src/test/java/com/valuescreener/research/agent/CompanyResearchAgentTest.java`:
```java
package com.valuescreener.research.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.valuescreener.research.model.CompanyResearchResult;
import com.valuescreener.research.model.ConfidenceLevel;
import com.valuescreener.research.prompt.ResearchPromptBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Citation;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CompanyResearchAgentTest {

    private final ChatModel chatModel = mock(ChatModel.class);
    private final CompanyResearchAgent agent =
            new CompanyResearchAgent(chatModel, new ResearchPromptBuilder(), new ObjectMapper(), 55);

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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd company-research-agent && mvn test -Dtest=CompanyResearchAgentTest`
Expected: FAIL — `CompanyResearchAgent` doesn't exist yet.

- [ ] **Step 3: Write the implementation**

`company-research-agent/src/main/java/com/valuescreener/research/agent/RawResearchResponse.java`:
```java
package com.valuescreener.research.agent;

import java.util.List;

record RawResearchResponse(
        String summary,
        String valueTrapAssessment,
        List<RawSourceReference> sources,
        boolean noReliableReportFound) {

    record RawSourceReference(String url, String claim) {
    }
}
```

`company-research-agent/src/main/java/com/valuescreener/research/agent/ResearchResponseParseException.java`:
```java
package com.valuescreener.research.agent;

public class ResearchResponseParseException extends RuntimeException {

    public ResearchResponseParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

`company-research-agent/src/main/java/com/valuescreener/research/agent/CompanyResearchAgent.java`:
```java
package com.valuescreener.research.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.valuescreener.research.model.CompanyResearchResult;
import com.valuescreener.research.model.ConfidenceLevel;
import com.valuescreener.research.model.SourceReference;
import com.valuescreener.research.prompt.ResearchPromptBuilder;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.api.tool.AnthropicWebSearchTool;
import org.springframework.ai.chat.metadata.Citation;
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
```

Note: `timeoutSeconds` is threaded through here so Task 5 can wrap the `chatModel.call(...)` line
in a bounded executor without changing the constructor signature again.

- [ ] **Step 4: Run test to verify it passes**

Run: `cd company-research-agent && mvn test -Dtest=CompanyResearchAgentTest`
Expected: PASS, 4 tests green. If `ChatResponseMetadata`, `Citation`, or `AnthropicWebSearchTool`
fail to resolve, see the Global Constraints note on package names — fix the import, re-run.

- [ ] **Step 5: Commit**

```bash
git add company-research-agent/src/main/java/com/valuescreener/research/agent/
git add company-research-agent/src/test/java/com/valuescreener/research/agent/
git commit -m "feat: add company research agent with citation cross-check guardrail"
```

---

### Task 5: Timeout handling (part of the operational success factors from the spec)

**Files:**
- Modify: `company-research-agent/src/main/java/com/valuescreener/research/agent/CompanyResearchAgent.java`
- Create: `company-research-agent/src/main/java/com/valuescreener/research/agent/ResearchTimeoutException.java`
- Modify: `company-research-agent/src/test/java/com/valuescreener/research/agent/CompanyResearchAgentTest.java`

**Interfaces:**
- Produces: `CompanyResearchAgent.research(...)` now also throws `ResearchTimeoutException`
  (unchecked) if the underlying Claude call doesn't complete within `research.agent.timeout-seconds`
  — consumed by `CompanyResearchTool` (Task 6) to build the "research failed" error result.

- [ ] **Step 1: Write the failing test**

Add to `CompanyResearchAgentTest`:
```java
    @Test
    void throwsResearchTimeoutExceptionWhenChatModelCallExceedsTimeout() {
        ChatModel slowChatModel = mock(ChatModel.class);
        when(slowChatModel.call(any(Prompt.class))).thenAnswer(invocation -> {
            Thread.sleep(500);
            throw new IllegalStateException("should have timed out before returning");
        });
        CompanyResearchAgent agentWithShortTimeout =
                new CompanyResearchAgent(slowChatModel, new ResearchPromptBuilder(), new ObjectMapper(), 0);

        assertThatThrownBy(() -> agentWithShortTimeout.research("EXMP", "Example Corp"))
                .isInstanceOf(ResearchTimeoutException.class);
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd company-research-agent && mvn test -Dtest=CompanyResearchAgentTest`
Expected: FAIL — `ResearchTimeoutException` doesn't exist yet, and the current implementation
doesn't enforce a timeout (this test would hang/fail differently without Step 3).

- [ ] **Step 3: Write the implementation**

`company-research-agent/src/main/java/com/valuescreener/research/agent/ResearchTimeoutException.java`:
```java
package com.valuescreener.research.agent;

public class ResearchTimeoutException extends RuntimeException {

    public ResearchTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

In `CompanyResearchAgent.java`, replace the direct `chatModel.call(...)` line in `research(...)`
with a timeout-bounded call. Replace:
```java
        ChatResponse response = chatModel.call(new Prompt(
                promptBuilder.build(ticker, companyName),
                AnthropicChatOptions.builder()
                        .webSearchTool(AnthropicWebSearchTool.builder().build())
                        .build()));
```
with:
```java
        ChatResponse response = callWithTimeout(ticker, companyName);
```

Add the new private method and imports:
```java
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
```
```java
    private final Executor executor = Executors.newVirtualThreadPerTaskExecutor();

    private ChatResponse callWithTimeout(String ticker, String companyName) {
        Prompt prompt = new Prompt(
                promptBuilder.build(ticker, companyName),
                AnthropicChatOptions.builder()
                        .webSearchTool(AnthropicWebSearchTool.builder().build())
                        .build());

        CompletableFuture<ChatResponse> future =
                CompletableFuture.supplyAsync(() -> chatModel.call(prompt), executor);
        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
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
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd company-research-agent && mvn test -Dtest=CompanyResearchAgentTest`
Expected: PASS, all 5 tests green.

- [ ] **Step 5: Commit**

```bash
git add company-research-agent/src/main/java/com/valuescreener/research/agent/
git add company-research-agent/src/test/java/com/valuescreener/research/agent/
git commit -m "feat: enforce timeout on research agent calls"
```

---

### Task 6: Expose `research_company` as an MCP tool, with graceful failure

**Files:**
- Create: `company-research-agent/src/main/java/com/valuescreener/research/tool/CompanyResearchTool.java`
- Test: `company-research-agent/src/test/java/com/valuescreener/research/tool/CompanyResearchToolTest.java`

**Interfaces:**
- Consumes: `CompanyResearchAgent.research(String, String) -> CompanyResearchResult` (Tasks 4–5),
  throwing `ResearchTimeoutException` / `ResearchResponseParseException` on failure.
- Produces: the `research_company` MCP tool, registered automatically by Spring AI's MCP server
  annotation scanner (already enabled in `application.yml` from Task 1) — this is the external
  contract the main `backend/` application will call once integrated.

- [ ] **Step 1: Write the failing test**

`company-research-agent/src/test/java/com/valuescreener/research/tool/CompanyResearchToolTest.java`:
```java
package com.valuescreener.research.tool;

import com.valuescreener.research.agent.CompanyResearchAgent;
import com.valuescreener.research.agent.ResearchTimeoutException;
import com.valuescreener.research.model.CompanyResearchResult;
import com.valuescreener.research.model.ConfidenceLevel;
import com.valuescreener.research.model.SourceReference;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CompanyResearchToolTest {

    private final CompanyResearchAgent agent = mock(CompanyResearchAgent.class);
    private final CompanyResearchTool tool = new CompanyResearchTool(agent);

    @Test
    void returnsSuccessfulStructuredResultOnSuccess() {
        CompanyResearchResult successResult = new CompanyResearchResult(
                "EXMP", "Revenue grew 8%.", "No structural headwinds mentioned.",
                List.of(new SourceReference("https://investor.example.com/q2-2026", "Revenue grew 8%")),
                ConfidenceLevel.HIGH, CompanyResearchResult.CURRENT_PROMPT_VERSION);
        when(agent.research("EXMP", "Example Corp")).thenReturn(successResult);

        CallToolResult result = tool.researchCompany("EXMP", "Example Corp");

        assertThat(result.isError()).isNotEqualTo(true);
    }

    @Test
    void returnsErrorResultWhenAgentTimesOut() {
        when(agent.research("EXMP", "Example Corp"))
                .thenThrow(new ResearchTimeoutException("timed out", new RuntimeException()));

        CallToolResult result = tool.researchCompany("EXMP", "Example Corp");

        assertThat(result.isError()).isTrue();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd company-research-agent && mvn test -Dtest=CompanyResearchToolTest`
Expected: FAIL — `CompanyResearchTool` doesn't exist yet.

- [ ] **Step 3: Write the implementation**

`company-research-agent/src/main/java/com/valuescreener/research/tool/CompanyResearchTool.java`:
```java
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
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd company-research-agent && mvn test -Dtest=CompanyResearchToolTest`
Expected: PASS, 2 tests green. If `io.modelcontextprotocol.spec.McpSchema.CallToolResult` or
`@McpTool`/`@McpToolParam` fail to resolve, see the Global Constraints note — search the resolved
`spring-ai-starter-mcp-server-webmvc` jar for the actual package (IDE: "Go to class" for
`CallToolResult`/`McpTool`) and fix the imports.

- [ ] **Step 5: Commit**

```bash
git add company-research-agent/src/main/java/com/valuescreener/research/tool/
git add company-research-agent/src/test/java/com/valuescreener/research/tool/
git commit -m "feat: expose research_company as an MCP tool with graceful failure handling"
```

---

### Task 7: Full application test and manual wire-level verification

**Files:**
- Modify: `company-research-agent/src/test/java/com/valuescreener/research/ResearchAgentApplicationTests.java`

**Interfaces:**
- Consumes: `CompanyResearchTool` (Task 6), the `spring.ai.mcp.server.*` config from Task 1.
- Produces: confidence that the full Spring context (MCP server autoconfiguration + annotation
  scanner + the tool bean) wires together correctly before deploying to Lambda.

- [ ] **Step 1: Write the failing test**

Replace the body of `ResearchAgentApplicationTests` with:
```java
package com.valuescreener.research;

import com.valuescreener.research.tool.CompanyResearchTool;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ResearchAgentApplicationTests {

    @Autowired
    private CompanyResearchTool companyResearchTool;

    @Test
    void contextLoadsAndRegistersTheResearchTool() {
        assertThat(companyResearchTool).isNotNull();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd company-research-agent && mvn test -Dtest=ResearchAgentApplicationTests`
Expected: at this point the class already exists from Task 6, so this should actually already
compile — if `CompanyResearchTool` isn't found in the context, that means it isn't registered as a
Spring bean; double-check the `@Component` annotation from Task 6, Step 3.

- [ ] **Step 3: Run test to verify it passes**

Run: `cd company-research-agent && mvn test -Dtest=ResearchAgentApplicationTests`
Expected: PASS.

- [ ] **Step 4: Manual wire-level check (not automated — do this once before moving on)**

This confirms the MCP Streamable HTTP transport actually serves the tool over the wire, which the
tests above don't cover (they only verify Spring wiring, not the MCP protocol layer).

```bash
export ANTHROPIC_API_KEY=sk-ant-...   # a real key, for this manual check only
cd company-research-agent
mvn spring-boot:run
```

In a second terminal, use the MCP inspector to connect and call the tool:
```bash
npx @modelcontextprotocol/inspector
```
Point it at `http://localhost:8080` with the Streamable HTTP transport, call `research_company`
with a real ticker (e.g. `ticker=AAPL`, `companyName=Apple Inc.`), and confirm you get back a
structured result with a non-empty `sources` list and `confidence: HIGH`, or a `noReliableReportFound`-driven
`LOW` result for a ticker without recent quarterly filings. Stop the server with Ctrl+C when done.

- [ ] **Step 5: Commit**

```bash
git add company-research-agent/src/test/java/com/valuescreener/research/ResearchAgentApplicationTests.java
git commit -m "test: verify research tool is registered in the Spring context"
```

---

### Task 8: Eval set (quality regression guard)

**Files:**
- Create: `company-research-agent/src/test/java/com/valuescreener/research/eval/CompanyResearchAgentEvalTest.java`

**Interfaces:**
- Consumes: `CompanyResearchAgent` (Tasks 4–5), a real `ANTHROPIC_API_KEY` from the environment.
- Produces: a manually-triggered test suite (tag `eval`, excluded from the default `mvn test` run
  configured in Task 1) that exercises the real Anthropic API against a small set of well-known
  tickers, for periodic quality review — not a hard pass/fail gate, since LLM output isn't
  deterministic enough for strict assertions on wording.

- [ ] **Step 1: Write the eval test**

`company-research-agent/src/test/java/com/valuescreener/research/eval/CompanyResearchAgentEvalTest.java`:
```java
package com.valuescreener.research.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.valuescreener.research.agent.CompanyResearchAgent;
import com.valuescreener.research.model.CompanyResearchResult;
import com.valuescreener.research.model.ConfidenceLevel;
import com.valuescreener.research.prompt.ResearchPromptBuilder;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.api.AnthropicApi;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Not run by default (see the excludedGroups=eval Surefire config in pom.xml) because it hits the
 * real Anthropic API and costs money. Run explicitly with:
 *   mvn test -Dgroups=eval -DexcludedGroups="" -Dtest=CompanyResearchAgentEvalTest
 * after a prompt change or a model version bump, to catch quality regressions early. Output is
 * printed for manual review rather than strictly asserted, because LLM wording isn't deterministic
 * enough for brittle string-matching assertions.
 */
@Tag("eval")
class CompanyResearchAgentEvalTest {

    record EvalCase(String ticker, String companyName) {
    }

    static Stream<EvalCase> knownCases() {
        return Stream.of(
                new EvalCase("AAPL", "Apple Inc."),
                new EvalCase("KO", "The Coca-Cola Company"));
    }

    @ParameterizedTest
    @MethodSource("knownCases")
    void producesAPlausibleSourcedSummaryForAWellKnownCompany(EvalCase evalCase) {
        AnthropicApi anthropicApi = AnthropicApi.builder()
                .apiKey(System.getenv("ANTHROPIC_API_KEY"))
                .build();
        CompanyResearchAgent agent = new CompanyResearchAgent(
                AnthropicChatModel.builder().anthropicApi(anthropicApi).build(),
                new ResearchPromptBuilder(),
                new ObjectMapper(),
                55);

        CompanyResearchResult result = agent.research(evalCase.ticker(), evalCase.companyName());

        assertThat(result.summary()).isNotBlank();
        assertThat(result.confidence()).isIn(List.of(ConfidenceLevel.HIGH, ConfidenceLevel.LOW));
        System.out.println("[" + evalCase.ticker() + "] confidence=" + result.confidence());
        System.out.println("  summary: " + result.summary());
        System.out.println("  valueTrapAssessment: " + result.valueTrapAssessment());
        result.sources().forEach(s -> System.out.println("  source: " + s.url() + " -> " + s.claim()));
    }
}
```

- [ ] **Step 2: Verify it's excluded from the default run**

Run: `cd company-research-agent && mvn test`
Expected: `CompanyResearchAgentEvalTest` does not appear in the Surefire test list (excluded via
`excludedGroups=eval` from Task 1's `pom.xml`).

- [ ] **Step 3: Run it explicitly once to confirm it works**

```bash
export ANTHROPIC_API_KEY=sk-ant-...
cd company-research-agent
mvn test -Dgroups=eval -DexcludedGroups="" -Dtest=CompanyResearchAgentEvalTest
```
Expected: PASS, with printed output for both eval cases — read it to sanity-check the summaries
look like real, sourced research rather than generic/hallucinated text.

- [ ] **Step 4: Commit**

```bash
git add company-research-agent/src/test/java/com/valuescreener/research/eval/
git commit -m "test: add manually-triggered eval suite for research quality"
```

---

### Task 9: Lambda deployment (serverless, no always-on cost) + budget alert

**Files:**
- Modify: `company-research-agent/pom.xml`
- Create: `company-research-agent/template.yaml`

**Interfaces:**
- Produces: a deployable Lambda artifact and a SAM template; no application code interfaces change.

- [ ] **Step 1: Add the Lambda adapter and shade plugin to `pom.xml`**

Add to `<dependencies>`:
```xml
        <dependency>
            <groupId>com.amazonaws.serverless</groupId>
            <artifactId>aws-serverless-java-container-springboot3</artifactId>
            <version>2.1.5</version>
        </dependency>
```

Before running the next step, check Maven Central for the exact current artifact id/version for
Spring Boot 3 support (search `aws-serverless-java-container` at
https://central.sonatype.com/search) — the upstream project has started adding a
`springboot4`-specific module alongside the existing one, so confirm you're pulling the Spring
Boot 3–compatible artifact matching this module's stack, and adjust the version above if needed.

Add to `<build><plugins>`:
```xml
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-shade-plugin</artifactId>
                <version>3.6.0</version>
                <executions>
                    <execution>
                        <phase>package</phase>
                        <goals>
                            <goal>shade</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
```

- [ ] **Step 2: Verify the shaded jar builds**

Run: `cd company-research-agent && mvn clean package`
Expected: `BUILD SUCCESS`, and `target/company-research-agent-0.1.0-SNAPSHOT.jar` exists and is
significantly larger than a plain Spring Boot jar (shaded/uber jar with all dependencies).

- [ ] **Step 3: Write the SAM template**

`company-research-agent/template.yaml`:
```yaml
AWSTemplateFormatVersion: '2010-09-09'
Transform: AWS::Serverless-2016-10-31
Description: Company Research Agent - standalone MCP server (Value Screener sub-project)

Parameters:
  AlertEmail:
    Type: String
    Description: >
      Email address for the monthly cost budget alert. Not committed with a real value -
      supply it at deploy time via --parameter-overrides.

Resources:
  ResearchAgentFunction:
    Type: AWS::Serverless::Function
    Properties:
      FunctionName: company-research-agent
      Runtime: java21
      Handler: com.amazonaws.serverless.proxy.spring.SpringDelegatingLambdaContainerHandler
      CodeUri: target/company-research-agent-0.1.0-SNAPSHOT.jar
      MemorySize: 1024
      Timeout: 60
      Environment:
        Variables:
          MAIN_CLASS: com.valuescreener.research.ResearchAgentApplication
          ANTHROPIC_API_KEY: '{{resolve:ssm-secure:/company-research-agent/anthropic-api-key:1}}'
      FunctionUrlConfig:
        AuthType: AWS_IAM

  MonthlyBudgetAlert:
    Type: AWS::Budgets::Budget
    Properties:
      Budget:
        BudgetName: company-research-agent-monthly
        BudgetType: COST
        TimeUnit: MONTHLY
        BudgetLimit:
          Amount: 15
          Unit: USD
      NotificationsWithSubscribers:
        - Notification:
            NotificationType: ACTUAL
            ComparisonOperator: GREATER_THAN
            Threshold: 80
            ThresholdType: PERCENTAGE
          Subscribers:
            - SubscriptionType: EMAIL
              Address: !Ref AlertEmail

Outputs:
  FunctionUrl:
    Description: Invoke URL for the MCP Streamable HTTP endpoint
    Value: !GetAtt ResearchAgentFunctionUrl.FunctionUrl
```

The `{{resolve:ssm-secure:...}}` dynamic reference is resolved by CloudFormation at deploy time —
the raw key is never written into the template, a parameter, or the stack's change history in
plaintext. The deploying IAM principal needs `ssm:GetParameter` on that parameter; the Lambda's own
execution role does not.

This is the cost-circuit-breaker success factor from the design spec (Section 7): an AWS Budget
alert at 80% of a $15/month ceiling, rather than an in-app counter — simpler to operate correctly
for a Lambda that has no persistent state of its own, and sufficient given the manual, low-frequency
trigger pattern this agent is designed for.

- [ ] **Step 4: Store the Anthropic key and deploy**

```bash
aws ssm put-parameter \
  --name /company-research-agent/anthropic-api-key \
  --type SecureString \
  --value "sk-ant-..."

cd company-research-agent
mvn clean package
sam build --template template.yaml
sam deploy --guided --parameter-overrides AlertEmail=<your-real-email>
```

Follow the guided prompts (stack name e.g. `company-research-agent`, region, confirm changeset).
Note the `FunctionUrl` output at the end — that's the endpoint the main `backend/` application will
eventually be configured to call as its MCP server URL (that integration is out of scope for this
plan; see the design spec).

- [ ] **Step 5: Verify the deployed function**

Run the same MCP inspector check as Task 7, Step 4, but pointing at the deployed `FunctionUrl`
instead of `http://localhost:8080`. Expect the same kind of structured result, with higher first-call
latency (Lambda cold start).

- [ ] **Step 6: Commit**

```bash
git add company-research-agent/pom.xml company-research-agent/template.yaml
git commit -m "feat: add serverless Lambda deployment with monthly cost budget alert"
```

---

### Task 10: Architecture documentation (repo visibility success factor)

**Files:**
- Create: `company-research-agent/README.md`

**Interfaces:**
- None — documentation only.

- [ ] **Step 1: Write the README**

`company-research-agent/README.md`:
```markdown
# Company Research Agent

Standalone, serverless MCP server that researches a company's most recent quarterly report /
investor relations content and returns a sourced, descriptive summary — used by the Value
Screener's AI Assessor to ground its moat/value-trap assessment in current information instead of
stale training knowledge.

Design rationale: see
[`../docs/superpowers/specs/2026-07-24-company-research-agent-design.md`](../docs/superpowers/specs/2026-07-24-company-research-agent-design.md).

## Architecture

- Spring Boot 3 / Java 21, deployed as an AWS Lambda behind a Function URL (serverless — no idle
  cost, see `template.yaml`).
- Exposes a single MCP tool, `research_company(ticker, companyName)`, over MCP Streamable HTTP
  (`spring-ai-starter-mcp-server-webmvc`).
- The agent loop runs inside this server, not in the caller: it calls Claude with Anthropic's
  built-in web search tool (`spring-ai-starter-model-anthropic`), then applies guardrails before
  returning a result:
  - **Value-trap assessment** — described neutrally, never as a warning or recommendation.
  - **Low-confidence flag** — set explicitly when no reliable current report exists (common for
    non-US tickers, which aren't subject to mandatory quarterly reporting since the EU dropped
    that requirement in 2013).
  - **Source-reference requirement** — every claim is paraphrased with a source link, never quoted
    verbatim (avoids `§ 51 UrhG` German quotation-right questions).
  - **Citation cross-check** — any source URL the model claims is discarded unless it matches a URL
    Claude's web search tool actually returned, guarding against fabricated sources
    (`CompanyResearchAgent.extractCitedUrls`).

This module is intentionally decoupled from the main `backend/` application — it doesn't know about
`FundamentalSnapshot`, `Suggestion`, or any other value-screener domain concept. The only contract
is the `research_company` MCP tool signature, which lets both sides be built and tested in
parallel.

## Local development

```bash
export ANTHROPIC_API_KEY=sk-ant-...
mvn spring-boot:run
```

## Testing

```bash
mvn test                                              # unit tests, no network calls
mvn test -Dgroups=eval -DexcludedGroups=""            # eval suite against the real API (costs money)
```

## Deployment

See `template.yaml`.

```bash
aws ssm put-parameter --name /company-research-agent/anthropic-api-key --type SecureString --value "sk-ant-..."
mvn clean package
sam build
sam deploy --guided --parameter-overrides AlertEmail=<your-email>
```
```

- [ ] **Step 2: Commit**

```bash
git add company-research-agent/README.md
git commit -m "docs: add company-research-agent README"
```

---

## Not covered by this plan (see design spec for why)

- Guardrail B (fact-check against `FundamentalSnapshot`) and the manual "Request AI analysis"
  button, `lastAnalyzedAt` field, and next-report-month hint — these live in the main `backend/`
  application once it integrates this MCP tool, which is a separate, later piece of work (design
  spec Sections 5–6).
- SEC EDGAR as a dedicated second tool for US tickers (design spec Section 4) — the current prompt
  relies on Anthropic's general web search tool, which is sufficient to ship an MVP; a dedicated
  EDGAR lookup is a natural follow-up improvement, not a blocker.
- Earnings-calendar-based "next report expected" hint — depends on the main `backend/`
  application's data provider, tracked as an open item in the design spec (Section 9).
