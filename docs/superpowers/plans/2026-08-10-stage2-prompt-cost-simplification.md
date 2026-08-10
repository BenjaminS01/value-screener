# Stage 2 Prompt/Cost Simplification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Stage 2 (`research_company`) research calls cheaper and faster by generalizing its per-criterion search-budget instruction and lowering its hard search/effort caps, so a live call actually completes instead of timing out.

**Architecture:** No new components. Three isolated edits inside the existing `company-research-agent` module: the Stage 2 prompt template (`ResearchPromptBuilder`), one constant (`CompanyResearchAgent.STAGE2_EFFORT`), and one config default (`research.agent.web-search-max-uses`, both in `application.yml` and its `@Value` fallback). Each edit gets its own test and commit.

**Tech Stack:** Java 21, Spring Boot 4.1.0 / Spring AI 2.0.0, JUnit 5 + AssertJ + Mockito (existing test stack, unchanged).

## Global Constraints

- Design authority: `docs/superpowers/specs/2026-07-24-company-research-agent-design.md`, Decision log entry "2026-08-10, reconciliation step 4 (screening-cost redesign) — Stage 2 prompt/cost simplification".
- `ConfidenceLevel` (`HIGH`/`LOW`) is explicitly **out of scope** — do not add a third value or change its assignment logic.
- Stage 1 (`quick_research_company`, `STAGE1_MAX_USES`, `STAGE1_EFFORT`) is untouched — every change in this plan targets Stage 2 only.
- No live/paid Anthropic API call is part of verifying this plan. `mvn test` (offline, mocked `ChatModel`) is the bar for every task. The actual live AAPL retry happens manually afterward, outside this plan — per this project's cost discipline, every live call attempt costs real money and must be made deliberately, one at a time.
- **Git convention for this project (important, overrides the usual plan-execution default):** neither an implementing subagent nor the controller ever runs a git command that changes repo state (`add`, `commit`, `branch`, `push`, etc.) without asking first. Read-only commands (`status`, `diff`, `log`) are fine for building a review package. After a task's review is approved, tell the user the exact commit command shown in that task's "Commit" step — the user runs it themselves. Wait for their confirmation (or check via `git log`) before starting the next task, since each task assumes a clean, already-committed baseline from the one before it.
- Before starting each task, give a short explanation of what the task does and the underlying concept (this plan is a deliberate learning vehicle for the user, who is newer to the AI/GenAI side of this codebase — not just a build to complete).

---

### Task 1: Generalize the Stage 2 search-budget instruction in the prompt

**Files:**
- Modify: `company-research-agent/src/main/java/com/valuescreener/research/prompt/ResearchPromptBuilder.java:33-57`
- Test: `company-research-agent/src/test/java/com/valuescreener/research/prompt/ResearchPromptBuilderTest.java`

**Interfaces:**
- Consumes: nothing new — `ResearchPromptBuilder.build(String ticker, String companyName, Stage1Snapshot stage1Snapshot)` signature is unchanged.
- Produces: nothing new — only the prompt *text* `build(...)` returns changes. No other task depends on the exact wording.

- [ ] **Step 1: Write the failing test**

Add to `ResearchPromptBuilderTest.java` (after `instructsManagementQualityCapitalAllocationCriterion`, before `includesStage1SnapshotValuesWhenProvided`):

```java
    @Test
    void instructsOneFocusedSearchPerCriterionWithOmitOnMiss() {
        String prompt = builder.build("AAPL", "Apple Inc.", null);

        assertThat(prompt)
                .contains("working under a limited search budget")
                .contains("at most one focused search")
                .doesNotContain("than the other criteria combined");
    }
```

The `doesNotContain` assertion guards against the old `interestCoverage`-specific escape hatch text surviving alongside the new general one.

- [ ] **Step 2: Run test to verify it fails**

Run: `cd company-research-agent && mvn test -Dtest=ResearchPromptBuilderTest#instructsOneFocusedSearchPerCriterionWithOmitOnMiss`
Expected: FAIL — prompt does not yet contain "working under a limited search budget".

- [ ] **Step 3: Update the prompt template**

In `ResearchPromptBuilder.java`, replace lines 33-57 (from the `%s` placeholder for `stage1Context` through the `interestCoverage` bullet's old wording and the closing "omitted... rather than guessed" line) with:

```java
                %s
                You are working under a limited search budget. For each criterion below, do at
                most one focused search. If you don't find a reliable, directly relevant source
                quickly, leave that criterion out of your JSON answer entirely rather than
                searching further or guessing — a partial result with fewer criteria is
                preferred over an exhaustive search.

                Research and report on exactly these criteria, each with its own source link and
                paraphrased claim:
                - marginTrend: operating/net margin over the last 5-10 years — stable, growing, or
                  declining, with the underlying figures that support the verdict.
                - freeCashFlowTrend: free cash flow over the same period — positive and growing,
                  positive but flat, or negative/declining.
                - profitStability: whether profit has avoided a strong decline over the last 5-10
                  years (the most demanding criterion here — only report it if you found genuine
                  multi-year figures, not a single good or bad year).
                - interestCoverage: EBIT divided by interest expense, from the most recent filings.
                - currentRatio: current assets divided by current liabilities, from the most recent
                  balance sheet.
                - moatAssessment: a qualitative read on the company's competitive moat and business
                  model — what protects its economics from competitors.
                - managementQuality: a qualitative read on capital allocation — buyback-versus-
                  dilution history and whether M&A activity looks disciplined or growth-for-its-own-
                  sake.
                - valueTrapAssessment: whether management commentary or risk factors offer an
                  explanation for the current valuation beyond what the numbers alone show.

                Any criterion you could not find a reliable source for should be omitted from the
                JSON entirely (its key left out) rather than guessed.
```

Only the `interestCoverage` bullet's wording and the new budget paragraph change — every other bullet (`marginTrend`, `freeCashFlowTrend`, `profitStability`, `currentRatio`, `moatAssessment`, `managementQuality`, `valueTrapAssessment`) keeps its exact existing wording, copied verbatim above so nothing is lost in the edit.

- [ ] **Step 4: Run test to verify it passes**

Run: `cd company-research-agent && mvn test -Dtest=ResearchPromptBuilderTest`
Expected: PASS, all tests in the class (including the new one and the pre-existing `requestsJsonOnlyFinalAnswerWithFullCriteriaSet`, which still checks all 8 JSON keys are mentioned and is unaffected by this wording change).

- [ ] **Step 5: Commit**

```bash
git add company-research-agent/src/main/java/com/valuescreener/research/prompt/ResearchPromptBuilder.java company-research-agent/src/test/java/com/valuescreener/research/prompt/ResearchPromptBuilderTest.java
git commit -m "feat(research-agent): generalize Stage 2 search-budget instruction to all criteria"
```

---

### Task 2: Lower Stage 2's effort constant from MEDIUM to LOW

**Files:**
- Modify: `company-research-agent/src/main/java/com/valuescreener/research/agent/CompanyResearchAgent.java:47`
- Test: `company-research-agent/src/test/java/com/valuescreener/research/agent/CompanyResearchAgentTest.java:291`

**Interfaces:**
- Consumes: `OutputConfig.Effort` (from `com.anthropic.models.messages.OutputConfig`), already imported in both files.
- Produces: nothing new — `STAGE2_EFFORT` stays a private constant of `CompanyResearchAgent`, no external caller.

- [ ] **Step 1: Update the failing assertion first (TDD via the existing test)**

In `CompanyResearchAgentTest.java`, change the assertion in `usesExplicitBoundedEffortAndDisablesThinkingForStage2` (line 291):

```java
        assertThat(options.getOutputConfig().effort()).contains(OutputConfig.Effort.LOW);
```

(was `.contains(OutputConfig.Effort.MEDIUM)`)

- [ ] **Step 2: Run test to verify it fails**

Run: `cd company-research-agent && mvn test -Dtest=CompanyResearchAgentTest#usesExplicitBoundedEffortAndDisablesThinkingForStage2`
Expected: FAIL — actual effort is still `MEDIUM` (production code unchanged so far).

- [ ] **Step 3: Update the constant**

In `CompanyResearchAgent.java`, line 47:

```java
    private static final OutputConfig.Effort STAGE2_EFFORT = OutputConfig.Effort.LOW;
```

(was `OutputConfig.Effort.MEDIUM`)

- [ ] **Step 4: Run test to verify it passes**

Run: `cd company-research-agent && mvn test -Dtest=CompanyResearchAgentTest`
Expected: PASS, whole class green (this constant is only read by Stage 2's `research()` path, so no Stage 1 test is affected).

- [ ] **Step 5: Commit**

```bash
git add company-research-agent/src/main/java/com/valuescreener/research/agent/CompanyResearchAgent.java company-research-agent/src/test/java/com/valuescreener/research/agent/CompanyResearchAgentTest.java
git commit -m "feat(research-agent): lower Stage 2 effort from MEDIUM to LOW"
```

---

### Task 3: Lower Stage 2's web-search round cap from 5 to 3

**Files:**
- Modify: `company-research-agent/src/main/resources/application.yml:31`
- Modify: `company-research-agent/src/main/java/com/valuescreener/research/agent/CompanyResearchAgent.java:64`
- Test: `company-research-agent/src/test/java/com/valuescreener/research/agent/CompanyResearchAgentTest.java:65,270`

**Interfaces:**
- Consumes: nothing new.
- Produces: nothing new — `webSearchMaxUses` stays a private `long` field of `CompanyResearchAgent`, set once via constructor/`@Value`.

**Note on TDD sequencing for this task:** unlike Task 2's `STAGE2_EFFORT` (a production-internal constant the test cannot see), `webSearchMaxUses` is a constructor parameter the test passes in directly — so updating the test fixture to `3` makes its own assertion pass immediately, with no red/failing step possible for that specific number. The `application.yml`/`@Value` default change (Step 2 below) is a separate, real production change that no unit test exercises at all (tests bypass Spring property injection by constructing `CompanyResearchAgent` directly) — it's only ever exercised by actually running the app, which is the manual live-retry step after this plan. Do not claim a red/green cycle for this task that didn't happen.

- [ ] **Step 1: Update the test fixture and assertion together**

In `CompanyResearchAgentTest.java`:

1. Line 65, inside `newAgent(...)` (the shared test helper used by most Stage 2 tests): change the `webSearchMaxUses` argument from `5` to `3`:

```java
    private static CompanyResearchAgent newAgent(ChatModel chatModel, long timeoutSeconds) {
        return new CompanyResearchAgent(chatModel, new ResearchPromptBuilder(), new QuickResearchPromptBuilder(),
                new ObjectMapper(), timeoutSeconds, "claude-sonnet-5", 3, TEST_ALLOWED_DOMAINS);
    }
```

2. Line 270, in `configuresStage2WebSearchWithBoundedUsesAndAllowedDomains`: change the expected value to match:

```java
        assertThat(options.getWebSearchTool().getMaxUses()).isEqualTo(3L);
```

Leave the separate `lenientAgent` construction in `ignoresUnknownJsonFieldsInModelResponse` (line 224, currently passing `5`) as-is — that test only asserts on `confidence()`, never on `getMaxUses()`, so its `webSearchMaxUses` value is inert; changing it would be a no-op edit with no test value.

Run: `cd company-research-agent && mvn test -Dtest=CompanyResearchAgentTest#configuresStage2WebSearchWithBoundedUsesAndAllowedDomains`
Expected: PASS immediately — see the sequencing note above, this edit doesn't touch production code yet.

- [ ] **Step 2: Update `application.yml` and the `@Value` fallback default**

In `application.yml`, replace the `web-search-max-uses` line and its comment (around line 31) with:

```yaml
    # Bounds worst-case cost/latency per research call: without this, the model can
    # issue an unlimited number of web_search calls. A local timeout does NOT stop
    # the underlying (billed) Anthropic request either -- see the Decision log.
    # Lowered 5 -> 3 (2026-08-10): a live Stage 2 call at 5 timed out twice (120s, then
    # 240s) with no recoverable result -- see the Decision log entry "Stage 2 prompt/cost
    # simplification" in the design spec. Accepted tradeoff: less recall on multi-round
    # disambiguation cases (one earlier simulation needed 7 rounds for AAPL's fiscal-quarter
    # labeling) in favor of calls that actually complete.
    web-search-max-uses: 3
```

In `CompanyResearchAgent.java`, line 64, change the `@Value` default fallback to match:

```java
                                 @Value("${research.agent.web-search-max-uses:3}") long webSearchMaxUses,
```

(was `${research.agent.web-search-max-uses:5}`)

- [ ] **Step 3: Run the full suite to confirm nothing broke**

Run: `cd company-research-agent && mvn test`
Expected: PASS, full suite green (this is the last edit in the plan — run the whole module's tests, not just this class, as the final check).

- [ ] **Step 4: Commit**

```bash
git add company-research-agent/src/main/resources/application.yml company-research-agent/src/main/java/com/valuescreener/research/agent/CompanyResearchAgent.java company-research-agent/src/test/java/com/valuescreener/research/agent/CompanyResearchAgentTest.java
git commit -m "feat(research-agent): lower Stage 2 web-search round cap from 5 to 3"
```

---

## After This Plan

Not part of this plan's own verification (per Global Constraints), but the next real-world step once all three tasks are committed: restart the server (`mvn spring-boot:run`, `ANTHROPIC_API_KEY` inherited from the shell, never touched/echoed by the assistant) and re-run the single live `research_company`/AAPL call that has twice timed out, to confirm this change actually fixes it. Capture the result and the `Research usage for AAPL: ...` log line, and update `project_company_research_agent_status.md` (assistant memory) and the design spec's Decision log with the outcome.
