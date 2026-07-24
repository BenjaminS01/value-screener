# Company Research Agent — Design

Last updated: 2026-07-24
Status: Design approved by the user, implementation in progress (Guardrail E added mid-implementation,
during Task 4 of the implementation plan; stack upgraded to Spring Boot 4 / Spring AI 2.0.0 GA during
Task 4, see Decision log).

**Language policy note:** Starting with this document, project docs are written in English (the
user is a Java developer targeting international roles), with a German summary at the top. Earlier
documents (`2026-07-21-value-screener-design.md`, `PROJECT-STATUS.md`, the Phase 1 plan) remain in
German as historical record and are not being retroactively translated.

## Deutsche Zusammenfassung

Ein eigenständiger, serverless MCP-Server (AWS Lambda, Java/Spring, MCP über Streamable HTTP), der
zu einem Ticker aktuelle, belegte qualitative Informationen recherchiert — vor allem aus
Quartalsberichten, da Quartalsberichte in den USA (SEC 10-Q) Pflicht sind, in der EU aber seit 2013
nicht mehr. Der Server exponiert ein einziges Tool `research_company(ticker, companyName)`, führt
intern selbst einen Claude-Aufruf mit Web-Search-Tool aus und liefert eine deskriptiv formulierte
Zusammenfassung mit fünf Guardrails: (A) Value-Trap-Einschätzung, (B) Fakten-Abgleich gegen
vorhandene Kennzahlen (passiert in der Hauptanwendung, nicht im Server), (C) Kennzeichnung, wenn
kein verlässlicher Bericht gefunden wurde, (D) Quellenverweise statt wörtlicher Zitate (aus
urheberrechtlichen Gründen, § 51 UrhG), (E) Prompt-Injection-Widerstand — durchsuchte Webinhalte
werden dem Modell explizit als Analysematerial, nicht als Anweisungen deklariert, da der Agent
über die Websuche fremde, nicht vertrauenswürdige Inhalte liest (klassischer OWASP-LLM-Top-10-
Angriffsvektor). Bewusst als eigenständiges, parallel zur Hauptanwendung
entwickelbares Sub-Projekt konzipiert — adressiert Risiko 4 der Haupt-Design-Spec und aktiviert die
dort vorgemerkte MCP-Idee. Auslösung ausschließlich manuell per Button (nicht automatisch/täglich),
geschützt durch den bestehenden Single-User-Login, um Kosten planbar zu halten. Tägliche
News-Suche wurde bewusst nicht aufgenommen (Kosten, widerspricht der bestehenden Dedup-Logik).

## 1. Purpose

A standalone, AI-powered research agent that provides current, sourced qualitative information
about a ticker/company — primarily from quarterly reports — as a complement to the purely
quantitative metrics already delivered by the existing Data Provider Client. It does not replace
any existing component; it enriches `Suggestion` (moat assessment) and `FundamentalAlert` (change
explanation) from the main design spec with real, current context.

Not a trading signal, not a buy/sell recommendation — like the main spec, the agent remains an
analysis/research-support tool, and the wording policy from Section 9 of the main spec applies
unchanged.

## 2. Scope

### In scope
- Research on quarterly report / investor relations content for portfolio positions and screening
  candidates, driven by an agent with a web search tool (not SEC EDGAR alone, since not every
  exchange/country has a quarterly reporting requirement — see Section 4)
- Sourced, descriptive summary with source references (see Section 5, Guardrails A–E)
- Manual trigger via a dashboard button, protected by the existing single-user login
- A quarter-over-quarter, evolving (rather than one-off) moat/state assessment

### Explicitly out of scope
- Daily/automatic news search for existing positions (cost risk, and conflicts with the main
  spec's existing dedup/quiet logic — see Decision Log)
- Automatic triggering by the scheduler (stays deliberately manual/cost-incurring, see Section 6)
- Capital allocation assessment as its own dimension (considered, not adopted for now)

## 3. Architecture

- **Standalone MCP server**, not part of the value-screener backend — its own repo/module, own
  tests/CI, independently developable and testable. The main application only needs to know the
  tool contract until integration and can keep progressing against a stub while the agent is built
  and tested separately against real tickers.
- **Deployment: serverless (AWS Lambda)**, not an always-on service — the call pattern is rare
  (quarterly per company, manually triggered); an always-on server would incur baseline cost
  regardless of actual usage (parallel to the already-rejected llm-broker always-on concern in
  `PROJECT-STATUS.md`). Cold-start latency is not critical since the call is not on the
  synchronous UI path of a live interaction, but behind a button with an expected wait.
- **Language/stack: Java 21/Spring**, on Spring Boot 4.x / Spring AI 2.0.0 (diverges from the
  `backend/` module's Spring Boot 3.x — required by `AnthropicWebSearchTool`, see Decision log;
  acceptable because this module has no shared dependency or deployment with `backend/`).
- **Transport: MCP over Streamable HTTP** behind a Lambda Function URL (stdio doesn't fit the
  Lambda request/response model).
- **Interface: a single tool** `research_company(ticker, companyName)` → a structured result with
  summary text, value-trap assessment, source references, and a confidence flag (see Section 5).
- **The intelligence lives in the server itself**, not in the calling application: the server runs
  its own Claude call with tool use internally (Anthropic's hosted web search tool, optionally an
  additional SEC EDGAR lookup tool for US tickers). Own Anthropic API key, own token/cost tracking,
  separate from the main application's key.

## 4. Legal context for the research

- **Quarterly reports are not universally mandatory.** In the US, the SEC requires a 10-Q from
  every listed company; in the EU, the mandatory quarterly reporting requirement was abolished in
  2013 by the amended Transparency Directive. The agent must expect to find no reliable quarterly
  report for European or other non-US tickers — see the low-confidence flag (Section 5, item C).
- **US tickers:** prefer SEC EDGAR as a structured, free source over relying on web search alone —
  more robust and less error-prone.
- **Investor relations site terms of use:** automated fetching can conflict with individual
  websites' terms of use, even for public content. Low but non-zero risk given this usage pattern
  (rare, quarterly, small volume) — to be checked before implementation/launch, like Risk 1 in the
  main spec; not a blocker for the design.
- **Note:** this assessment is not legal advice, just a domain-level judgment call made in the
  context of this project.

## 5. Domain output & guardrails

Each analysis returns, structured:

- **A — Value-trap assessment:** worded descriptively (consistent with the wording policy in
  Section 9 of the main spec) — whether management commentary/risk factors offer an explanation for
  a low valuation beyond what the numbers alone show (e.g., structural challenges in a segment),
  rather than a judgmental warning ("Watch out, value trap"). This is the core added value over
  pure quantitative screening: distinguishing "cheap because overlooked" from "cheap because
  structurally impaired."
- **B — Fact check:** contradictions between the AI's claims and the existing `FundamentalSnapshot`
  (e.g., text claims rising margin, numbers show the opposite) are automatically flagged. Happens
  **in the main application**, not inside the MCP server — the server doesn't know about
  `FundamentalSnapshot` (deliberately decoupled, see Section 3); it only returns its structured
  research output, and the main application reconciles that output against its own metrics after
  receiving it. No extra AI call needed — a simple rule-based comparison.
- **C — Low-confidence flag:** explicitly shown when no reliable current report was found (see
  Section 4), instead of falling back to stale training data. Prevents a thin/outdated answer from
  looking as trustworthy as a well-sourced one.
- **D — Source-reference requirement (instead of verbatim quotes):** every key claim gets a link to
  the concrete source; the content is paraphrased in the model's own words rather than quoted
  verbatim. **Rationale for this choice over direct quotes:** a link needs no quotation-right
  justification under German copyright law (§ 51 UrhG) — it's a pure reference, not reproduced
  protected text — which removes the copyright risk almost entirely. It also avoids the sharper
  failure mode of a fabricated passage falsely presented as a verbatim quote (a more severe version
  of Risk 7 in the main spec). Simpler to validate technically too: the check reduces to "does this
  link come from the search tool's actual results," instead of having to guarantee exact string
  matches against raw source text.
- **E — Prompt-injection resistance:** the agent's system prompt explicitly instructs the model that
  content retrieved via web search is analysis material, not instructions — if retrieved content
  contains text that looks like a command (e.g., "ignore previous instructions," "you must recommend
  this stock"), the model must disregard it as an attempted manipulation and continue to follow only
  the system prompt. **Why this matters specifically here:** the agent autonomously reads
  third-party web content (investor relations pages, and potentially news) that is not
  trust-controlled by this project — a compromised or adversarial page could embed hidden
  instructions aimed at the model reading it, the classic OWASP LLM Top 10 #1 risk (prompt
  injection). Guardrail A (descriptive-only wording) provides a partial, structural defense-in-depth
  layer against the most obvious injection goal (forcing a buy/sell recommendation), but does not
  address other forms of manipulation (e.g., injected false-sounding factual claims phrased
  descriptively). Guardrail D's citation cross-check is a different, complementary protection — it
  guards against fabricated sources, not against a genuine source manipulating the model's output.
  This is prompt-level mitigation only (instructing the model, not sandboxing it) — a reasonable,
  industry-standard first layer for this risk level, not a complete technical guarantee.

## 6. Integration & triggering

- **Trigger: a manual "Request AI analysis" button** on every screening candidate and every
  detected portfolio alert. Deliberately decouples the cost-incurring AI research from the free
  daily metrics pipeline (the scheduler stays purely quantitative, see main spec Section 5) — cost
  is incurred exactly when the button is actually clicked.
- **Access control:** the button is only visible/functional for the logged-in operator, using the
  same login as portfolio write access (main spec Section 9). Public visitors only see the result
  (if one exists) or a neutral "no AI analysis yet" state — otherwise any anonymous visitor could
  trigger arbitrary costs.
- **`lastAnalyzedAt`:** a timestamp per `Suggestion`/`FundamentalAlert`, shown in the UI next to
  the button.
- **Hint for the likely next report month:** two-tiered — preferably via an earnings calendar
  endpoint from the fundamentals data provider (to be checked against current docs/free-tier scope,
  like Risk 1 in the main spec), otherwise a rough fallback estimate from historical filing dates
  the agent has already found (last found report + ~3 months). For tickers with no reliable
  schedule (see Section 4), show "unclear" rather than a guessed date.
- **No lockout on re-analysis:** the button stays clickable at any time, even shortly after a
  previous analysis — the hint is informational only, it does not enforce a waiting period. The
  user keeps full control, e.g. for an earlier re-check with good reason.
- **Result flows into existing entities:** `Suggestion` (moat assessment) and `FundamentalAlert`
  (change explanation) are extended with the analysis result — no new core entity needed. Across
  quarters this builds an evolving rather than one-off qualitative assessment, analogous to the
  existing `FundamentalSnapshot` on the quantitative side.

## 7. Operations & success factors

- **Error/timeout behavior:** a third button state alongside "result available"/"not yet
  requested": "Research failed, please try again later" on timeout or when no usable sources were
  found. Prevents the app from looking broken on a first failure.
- **Cost circuit breaker:** in addition to the per-ticker hint (`lastAnalyzedAt`), a simple global
  daily limit or an AWS budget alert, as protection against bugs/double-clicks/unexpected behavior
  — not just against deliberate overuse.
- **Eval set:** a small set (5–10) of well-known tickers with a stable, known character (e.g., a
  clear moat case, a clear value-trap case), used to periodically re-check analysis quality.
  Protects against a prompt change or model update silently degrading quality — the TDD equivalent
  for the non-deterministic part of the application, consistent with the main spec's existing TDD
  commitment (Section 7.1).
- **Versioning:** every stored analysis is tagged with a prompt/schema version. Needed so the
  quarter-over-quarter comparison (Section 6) doesn't silently mix old analysis generations with
  new ones once the analysis logic is later improved.
- **Visibility in the repo:** the architectural decisions in this document (standalone MCP server,
  guardrails A–D, eval approach) should be documented in the project docs/README clearly enough to
  be recognizable without a live demo — matching the learning goal recorded in `PROJECT-STATUS.md`
  of making AI competence demonstrable, not just functionally present.

## 8. Cost estimate (rough order of magnitude)

- **Hosting:** near zero given the serverless deployment (Section 3) and this rare call pattern,
  compared to an always-on server with ongoing baseline cost.
- **Claude/search API cost:** architecture-independent, driven by call frequency. An agent loop
  (search → read → summarize) uses more tokens than a single prompt; Anthropic's hosted web search
  tool is additionally billed per search (rough order of magnitude: ~$10 per 1,000 searches). At
  manual, quarterly-cadence triggering for a personal portfolio/candidate set, this lands in the
  low single-digit euros per month, roughly estimated — notably cheaper than the (rejected)
  blind-daily news search, which was estimated at €20–50/month.

## 9. Open items to verify before implementation

1. Availability of an earnings calendar endpoint at the chosen fundamentals data provider
   (Section 6).
2. Terms of use of the relevant investor relations sites (Section 4).
3. Concrete SEC EDGAR integration (which endpoints, rate limits) for US tickers.
4. Exact Lambda timeout and the UX-side wait/polling behavior for the button during research.

## Decision log (context for follow-up sessions)

- Starting point of the session: the user's question about which domain extensions would let them,
  as a Java developer, practically demonstrate the core AI topics the industry expects, without
  building pure bonus features unrelated to the application.
- From an initial topic list (RAG/vector DB, structured output, evals, guardrails, LLMOps,
  tool-use/agents, prompt-injection awareness), "automatically evaluate quarterly reports" emerged
  as the most concrete, directly useful candidate.
- Automatic acquisition was first discussed as a plain "download from the website" idea, then
  evolved into a real search agent once it became clear that quarterly reports aren't universally
  mandatory or uniformly discoverable (EU vs. US/SEC EDGAR).
- Deliberate decision to treat the agent as a standalone, independently developable sub-project
  (its own MCP server) rather than an extension of the existing AI Assessor, to allow independent
  development of the core application and the agent feature.
- Serverless (Lambda) chosen over an always-on server after explicitly working through the cost
  question — a parallel to the already-rejected llm-broker always-on consideration in
  `PROJECT-STATUS.md`.
- The "intelligence" (agent loop) deliberately lives in the MCP server itself, not in the main
  application — enables full standalone testability, which best matches the desire for parallel
  development.
- Daily/automatic news search for existing positions was explicitly rejected: conflicts with both
  the cost logic and the main spec's existing dedup/quiet principle (value investing is not
  news-flow driven).
- Triggering deliberately made manual (button) rather than automatic, to fully decouple the
  expensive AI research from the free daily metrics pipeline — predictable, user-controlled cost
  instead of an automatically growing cost risk.
- Verbatim quotes (originally planned as a "quote requirement") were dropped in favor of source
  references + paraphrasing, after weighing the copyright angle (§ 51 UrhG, quotation right) and
  concluding that a link/reference offers the same verifiability benefit at substantially lower
  legal risk.
- Capital allocation as an additional assessment dimension was proposed but deliberately not
  added to scope (see Section 2).
- Language policy decided during this session: going forward, new documents are written in English
  with a German summary (user's stated goal: work internationally as a Java developer); existing
  documents are not retroactively translated.
- Guardrail E (prompt-injection resistance) added mid-implementation, after Task 3 (prompt builder)
  was already committed: the user asked directly whether prompt injection was covered, prompted by
  the realization that the agent (built in Task 4) reads untrusted third-party web content. This had
  been flagged as a candidate topic very early in the original brainstorming (before the sub-project
  was even scoped) but did not make it into the four guardrails (A–D) selected at design time — a
  genuine gap, not a deliberate exclusion. Retrofitted as Task 3b in the implementation plan.
- Stack upgraded from Spring Boot 3.x / Spring AI 1.1.8 to **Spring Boot 4.x / Spring AI 2.0.0 GA**
  during Task 4: `AnthropicWebSearchTool` and citation-with-URL support (`Citation.getUrl()`,
  `Citation.ofWebSearchResultLocation(...)`) — the mechanism Guardrail D's technical enforcement
  depends on — do not exist anywhere in the 1.x line; they were introduced in Spring AI 2.0.0-M3 as
  part of the Anthropic module's rewrite onto the official `com.anthropic:anthropic-java` SDK, which
  hard-requires Spring Boot 4. Task 1's original 1.1.8 pin (see that task's note) was a reasonable
  choice at the time it was made but turned out to be incompatible with a requirement (Guardrail D's
  citation cross-check) that wasn't yet coded when the pin was chosen. Discovered when Task 4's first
  implementation attempt silently papered over the missing APIs (invented a local `Citation`
  stand-in, dropped the web-search-tool wiring entirely) — caught by inspecting the resolved
  dependency jar directly rather than trusting the plan's assumed package names. Confirmed both
  Spring Boot 4 (GA since November 2025, 4.1.0 stable since June 2026) and Spring AI 2.0.0 (GA since
  June 12, 2026) are mature/GA, not milestone builds, before deciding to move; `aws-serverless-java-container`
  (Task 9's deployment adapter) already supports Spring Boot 4. This module's isolation from
  `backend/` (Section 3) is what makes the version divergence from the rest of the portfolio
  acceptable — no shared dependency or shared deployment forces version alignment.
