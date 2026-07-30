# Learning Packages: Company Research Agent Build

**Deutsche Kurzfassung:** Diese Sammlung fasst die technisch wertvollsten Lernmomente aus der
Umsetzung des Company Research Agent (Tasks 1–7, Juli 2026) zusammen — für Junior-/Mid-Level-Java-
Entwickler geschrieben, mit Anspruch über die Basics hinaus. Jedes Dokument steht für sich, behandelt
ein konkretes Konzept anhand eines echten Bugs/einer echten Design-Entscheidung aus diesem Projekt,
und endet mit übertragbaren Merksätzen für eigenen Code. Quelle: die (git-ignorierten, daher hier
bewusst destilliert festgehaltenen) SDD-Task-Protokolle unter `.superpowers/sdd/` im
`feature/company-research-agent`-Worktree.

---

These packages distill the most technically valuable lessons from building the Company Research
Agent (Tasks 1–7, July 2026) — written for junior/mid-level Java developers, but aimed past the
basics into real intermediate/advanced territory. Each document stands alone, is anchored in one
real bug or design decision from this project, and closes with takeaways meant to transfer to your
own code, not just this codebase.

The raw source material (subagent task briefs, reports, and code-review diffs) lived in
`.superpowers/sdd/` inside the `feature/company-research-agent` git worktree — deliberately
git-ignored scratch space for the Subagent-Driven Development process itself. These packages exist
because that raw material isn't meant to be permanent documentation, but the *concepts* it surfaced
are worth keeping.

## Reading order

There's no strict dependency between these, but they roughly follow the order the concepts appeared
during implementation:

1. [Immutable Domain Modeling with Java Records](01-immutable-domain-modeling-with-records.md) —
   records, compact constructors, defensive copying, when to reach for an enum.
2. [TDD and Trusting Your Tests](02-tdd-and-trusting-your-tests.md) — a real bug that made it from a
   written plan into a passing test suite, and what it teaches about "the test is the contract."
3. [Prompt Injection and LLM Security](03-prompt-injection-and-llm-security.md) — the OWASP LLM
   Top 10 category most tutorials skip, and why an AI agent with web search is exposed to it.
4. [Verifying APIs with `javap` Instead of Guessing](04-verifying-apis-with-javap-instead-of-guessing.md) —
   decompiling real jars to check API claims, instead of trusting memory or documentation that may be
   for a different library version.
5. [Don't Trust the LLM: Citation Cross-Checking](05-dont-trust-the-llm-citation-cross-checking.md) —
   verifying a model's self-reported sources against real tool-call metadata.
6. [Jackson 2 vs. Jackson 3, and Spring Boot 4 Autoconfiguration](06-jackson-2-vs-3-and-spring-boot-4-autoconfiguration.md) —
   a real "it compiles but the bean doesn't exist" bug from a major framework version bump.
7. [Timeouts and Virtual Threads](07-timeouts-and-virtual-threads.md) — bounding a blocking call with
   `CompletableFuture` + Java 21 virtual threads.
8. [Building an MCP Tool with Spring AI](08-building-an-mcp-tool-with-spring-ai.md) — what MCP is,
   `@McpTool`/`@McpToolParam`, and graceful error handling at a protocol boundary.
9. [Config Precedence Gotchas: the Haiku/Sonnet Saga](09-config-precedence-gotchas.md) — a
   three-attempt bug hunt into why a YAML property didn't take effect, ending in decompiling Spring
   AI's own request-building code.
10. [Real-World AI Cost Control](10-real-world-ai-cost-control.md) — bounding tool usage, and why a
    client-side timeout doesn't actually cancel a real, billed API call.

## How these were produced

Written directly from the project's own SDD progress ledger and task reports after the underlying
implementation (Tasks 1–7) was already complete, tested, and merged into `main` — these are a
retrospective distillation, not a running log. If Task 8 (eval set) or Tasks 9–10 (Lambda deployment,
architecture documentation) surface similarly transferable lessons later, add new numbered files here
following the same format: one concept, one real anchor from this codebase, concrete code, and a
short takeaways list.
