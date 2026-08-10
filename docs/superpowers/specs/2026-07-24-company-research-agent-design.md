# Company Research Agent — Design

Last updated: 2026-08-02
Status: Design approved by the user, implementation in progress (Guardrail E added mid-implementation,
during Task 4 of the implementation plan; stack upgraded to Spring Boot 4 / Spring AI 2.0.0 GA during
Task 4, see Decision log). **Extended 2026-07-31** (reconciliation step 2 of
`2026-07-30-screening-cost-redesign-design.md`): scope, interface, output contract, triggering, and
cost model updated for the full Section 6 criteria set from that spec and the new Stage 1 tool. Two
prior scope exclusions in this document (automatic/scheduler triggering; capital allocation as a
criterion) are reversed by this update — see Decision log. **Completed 2026-08-02** (reconciliation
step 3): `ResearchPromptBuilder`, `CompanyResearchResult`, `CompanyResearchAgent`, and
`CompanyResearchTool` now implement the full Section 5 contract described here, across the two-tool
architecture (`research_company` / `quick_research_company`). `CURRENT_PROMPT_VERSION` is now
`"research-v2"`. The live end-to-end cost check (reconciliation step 4) remains outstanding.

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

**Ergänzung (2026-07-31, Abgleich mit dem Screening-Cost-Redesign):** dieser Server ist jetzt nicht
mehr nur der qualitative Zusatz zu einem separaten Data Provider Client (der im Redesign komplett
entfällt), sondern die einzige Quelle für sowohl quantitative Kennzahlen als auch qualitative
Einschätzung. Statt eines Tools gibt es jetzt **zwei**: `research_company` (Stufe 2, voller
Kriterienkatalog) und neu `quick_research_company` (Stufe 1, nur die numerische Momentaufnahme,
`maxUses(1)`), die denselben internen Mechanismus teilen. **Zwei bisherige Scope-Ausschlüsse dieses
Dokuments werden hiermit aufgehoben**, weil sie dem Redesign widersprechen: automatische/tägliche
Auslösung durch den Scheduler ist jetzt Kernmechanismus (statt ausschließlich manuell), und
Management-Qualität/Kapitalallokation ist jetzt ein aufgenommenes Kriterium (statt "considered, not
adopted"). Details siehe Decision log unten und die verlinkte Redesign-Spec.

## 1. Purpose

A standalone, AI-powered research agent that provides current, sourced information about a
ticker/company — both quantitative (P/E, P/B, ROE, debt/equity, margins, free cash flow, interest
coverage) and qualitative (moat, management quality/capital allocation, value-trap assessment) —
primarily from quarterly reports and standard key-statistics pages.

**Scope widened 2026-07-31** (per `2026-07-30-screening-cost-redesign-design.md`): this agent was
originally meant to complement the purely quantitative metrics delivered by a separate Data Provider
Client. That component was dropped entirely from the overall design (paid screener endpoint, no free
market-wide data path) — there is no other component left computing fundamentals. Split into a
bounded Stage 1 snapshot tool and a full Stage 2 research tool (Section 3), this agent is now the
single source of both the numeric and the qualitative side. It enriches `ResearchRecord` (the
redesign spec's knowledge-base entry, Section 9 there), which `Suggestion` and `FundamentalAlert`
are views over.

Not a trading signal, not a buy/sell recommendation — like the main spec, the agent remains an
analysis/research-support tool, and the wording policy from Section 9 of the main spec applies
unchanged.

## 2. Scope

### In scope
- Research on quarterly report / investor relations content for portfolio positions and screening
  candidates, driven by an agent with a web search tool (not SEC EDGAR alone, since not every
  exchange/country has a quarterly reporting requirement — see Section 4)
- Sourced, descriptive summary with source references (see Section 5, Guardrails A–E)
- **The full Section 6 criteria set from `2026-07-30-screening-cost-redesign-design.md`** (added
  2026-07-31): quantitative (P/E, P/B, ROE, debt/equity, current ratio, margin trend, free cash
  flow, interest coverage, insider/founder ownership) and qualitative (moat, management
  quality/capital allocation, value-trap assessment) — not just a free-text summary (see Section 5).
- **A second tool, `quick_research_company`** (added 2026-07-31), for the bounded Stage 1 numeric
  snapshot — see Section 3.
- **Automatic daily triggering via the Scheduler** (added 2026-07-31, reverses this document's
  original exclusion — see Decision log), in addition to the existing manual button trigger.
- **Management quality / capital allocation as a qualitative Stage 2 criterion** (added 2026-07-31,
  reverses this document's original exclusion — see Decision log), alongside a supporting
  insider/founder-ownership snapshot value at Stage 1.
- **Per-criterion source reference and value-trap/low-confidence text, persisted** (added
  2026-07-31) — both already produced per the existing guardrails, now explicitly required as
  stored output rather than transient text, to support the redesign's dashboard drill-down.
- Manual trigger via a dashboard button, protected by the existing single-user login
- A quarter-over-quarter, evolving (rather than one-off) moat/state assessment

### Explicitly out of scope
- Daily/automatic news search for existing positions (cost risk, and conflicts with the main
  spec's existing dedup/quiet logic — see Decision Log)
- Realized-return outcome tracking and periodic staleness re-checks on already-passed Suggestions
  (both considered and explicitly not adopted in the redesign spec's product-strategy review — see
  that spec's Decision log, 2026-07-30)
- Live/cheap price enrichment of already-researched companies without new AI cost ("Approach B" in
  the redesign spec) — deliberately deferred there, not part of this agent's scope either

**Two exclusions reversed 2026-07-31** (see Decision log): this document previously excluded
*automatic triggering by the scheduler* and *capital allocation assessment* — both are now
required, adopted parts of scope above, because the redesign spec that supersedes the Screening
Engine/Data Provider Client (`2026-07-30-screening-cost-redesign-design.md`) makes automatic daily
triggering the core mechanism and explicitly adopted management quality/capital allocation as a
criterion. Left in place here only as a record of what changed and why — do not treat either as
still excluded.

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
- **Interface: two MCP tools sharing one internal agent mechanism** (decided 2026-07-31 — see the
  redesign spec's Section 7 "Stage 1 mechanism" note and this document's Decision log):
  - `research_company(ticker, companyName, stage1Snapshot)` — Stage 2, full deep research. Returns
    a structured result with the complete Section 6 criteria set (quantitative + qualitative),
    per-criterion source references, a value-trap assessment, and a confidence flag (see Section
    5). The new `stage1Snapshot` parameter carries Stage 1's already-computed valuation figures
    into the prompt as given context (cost/quality measure 5, Section 5).
  - `quick_research_company(ticker, companyName)` — Stage 1, one bounded search step
    (`AnthropicWebSearchTool.maxUses(1)`). Returns just the numeric snapshot fields from Section 5
    (current P/E, P/B, ROE, debt/equity, insider ownership, plus the current-year-only reject-filter
    values), no qualitative write-up.

  Both tools call into the same underlying agent mechanism (web search options, citation
  cross-check, timeout handling, usage logging) with independently scoped prompts and
  independently typed results — not one tool with a mode flag. Chosen because Selection Logic in
  the main backend picks the stage as deterministic code, not an LLM choosing between
  self-describing tools.
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
- **Search domain restriction (added 2026-07-31, cost/quality measure 1 from the redesign spec):**
  `AnthropicWebSearchTool.allowedDomains(...)` is set to primary filings and established financial
  data sources (e.g. SEC EDGAR, official investor-relations domains, major financial data sites)
  as a first restriction layer, ahead of open web search. Directly targets the
  fiscal-quarter-disambiguation cost driver found in the AAPL simulation (Decision log, 2026-07-26
  entry — 7 search rounds spent distinguishing analyst preview articles from the actually-filed
  10-Q) and reduces the untrusted-content surface Guardrail E has to defend against. The exact
  domain list is an implementation detail, to be calibrated once real search volume exists
  (reconciliation step 3/4).

## 5. Domain output & guardrails

**Rewritten 2026-07-31** (reconciliation step 2) to carry the full Section 6 criteria set from
`2026-07-30-screening-cost-redesign-design.md`, split across the two tools from Section 3. Guardrails
A, C, D, E are unchanged in principle from the original design; Guardrail B is redefined (see below)
because the `FundamentalSnapshot`/Data Provider Client it originally cross-checked against no longer
exists.

### 5.1 Stage 1 output — `quick_research_company`

One bounded search step, numeric only, each value carrying its own source reference:

| Field | Purpose |
|---|---|
| Current P/E, P/B | Valuation snapshot (Section 6 valuation check, signal 2) |
| Company's own ~5-year average P/E/P/B, if directly published by the source | Own-historical-range valuation signal (Section 6, signal 2) |
| Current ROE, debt/equity | Quality/safety snapshot |
| Current ratio, if available on the same source | Safety snapshot; falls through to Stage 2 if not found here |
| Current-year net margin (single point) | Early reject filter for Stage 2's margin-trend criterion |
| Current-year FCF sign (positive/negative) | Early reject filter for Stage 2's FCF-trend criterion |
| Current-year net income vs. prior year (single point) | Early reject filter for Stage 2's profit-stability criterion |
| Insider/founder ownership share | Cheap proxy supporting Stage 2's management-quality read |

If no reliable current snapshot page can be found at all, the result carries a `noReliableDataFound`
flag (parallel to Stage 2's `noReliableReportFound`, Guardrail C) instead of guessed values.

### 5.2 Stage 2 output — `research_company`

Full deep research, structured per the complete Section 6 criteria set, each criterion carrying its
own source reference:

| Field | Notes |
|---|---|
| Margin trend (multi-year) | Narrative + stable/growing/declining verdict |
| Free cash flow trend (multi-year) | Narrative + positive-and-growing verdict |
| Profit stability (no strong decline over 5–10 years) | Narrative + verdict; the most expensive criterion to verify |
| Interest coverage (EBIT / interest expense) | **Best-effort, cost-unconfirmed** (redesign spec Section 11, risk 7) — not known to sit on a standard page; if obtaining it would blow the search-round ceiling, return it as unavailable rather than spending extra rounds |
| Current ratio | Only if not already obtained at Stage 1 |
| Moat / business-model assessment | Qualitative, unchanged from the original design's core purpose |
| Management quality / capital allocation | **New 2026-07-31** — buyback-vs-dilution history, M&A discipline; produced in the same research pass as the moat assessment, no extra search round |
| Value-trap assessment (Guardrail A) | Now informed by the `stage1Snapshot` context (Section 3) instead of being unable to reference an actual valuation multiple |

Numeric thresholds for pass/fail against these criteria are **not** computed by this agent — same
principle as the original Guardrail B design (fact-checking logic stays in the main application, not
the MCP server, Section 3). The agent returns sourced facts and qualitative judgment; the backend's
Selection Logic applies the thresholds (redesign spec Section 2, "calibrated during implementation").

### 5.3 Guardrails

- **A — Value-trap assessment:** worded descriptively (consistent with the wording policy in
  Section 9 of the main spec) — whether management commentary/risk factors offer an explanation for
  a low valuation beyond what the numbers alone show (e.g., structural challenges in a segment),
  rather than a judgmental warning ("Watch out, value trap"). This is the core added value over
  pure quantitative screening: distinguishing "cheap because overlooked" from "cheap because
  structurally impaired." **Updated 2026-07-31:** now grounded in an actual valuation multiple, via
  the `stage1Snapshot` context (Section 3) — previously this assessment could not reference the
  company's own P/E/P/B at all (see the 2026-07-30 decision log entry that first identified this
  gap).
- **B — Consistency check (redefined 2026-07-31):** originally, contradictions between the AI's
  claims and an existing `FundamentalSnapshot` (from the now-removed Data Provider Client) were
  cross-checked in the main application. That data source no longer exists. Redefined instead as a
  **Stage 1 vs. Stage 2 consistency check**: Stage 1's snapshot values are passed into the Stage 2
  prompt as context (Section 3); if Stage 2's own research produces figures that materially diverge
  from what it was given (e.g. a different current P/E than the Stage 1 snapshot), that divergence
  is flagged rather than silently overwritten. Still happens in the main application as a simple
  rule-based comparison after both calls return — no extra AI call needed, same principle as the
  original guardrail.
- **C — Low-confidence flag:** explicitly shown when no reliable current report/snapshot was found
  (see Section 4), instead of falling back to stale training data. Prevents a thin/outdated answer
  from looking as trustworthy as a well-sourced one. Applies to both tools (Section 5.1's
  `noReliableDataFound`, Section 5.2's `noReliableReportFound`).
- **D — Source-reference requirement (instead of verbatim quotes):** every key claim/criterion gets
  a link to the concrete source; the content is paraphrased in the model's own words rather than
  quoted verbatim. **Rationale for this choice over direct quotes:** a link needs no quotation-right
  justification under German copyright law (§ 51 UrhG) — it's a pure reference, not reproduced
  protected text — which removes the copyright risk almost entirely. It also avoids the sharper
  failure mode of a fabricated passage falsely presented as a verbatim quote (a more severe version
  of Risk 7 in the main spec). Simpler to validate technically too: the check reduces to "does this
  link come from the search tool's actual results," instead of having to guarantee exact string
  matches against raw source text. **Updated 2026-07-31:** now required per-criterion (not just
  per-analysis), and explicitly persisted rather than transient (redesign spec Section 9,
  `ResearchRecord`) so the dashboard can show where each figure came from.
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
  industry-standard first layer for this risk level, not a complete technical guarantee. Applies
  equally to Stage 1's shorter search (Section 5.1).

### 5.4 Cost/quality measures (mandatory, added 2026-07-31)

Already identified in this document's own Decision log (the "cost simulation" entry below) but never
implemented; the redesign spec's Decision log makes them **mandatory parts of this spec, not
optional follow-ups**, since Section 10 there depends on all of them:

1. **Domain-restricted search** (Section 4) — primary sources first via `allowedDomains(...)`.
2. **Fixed, tight search-round ceiling** instead of "search until satisfied" — reconfirmed
   2026-07-31 as the single most load-bearing of these measures, since Anthropic re-bills
   search-result content as input tokens on every subsequent conversation turn, so cost compounds
   with round count rather than growing linearly (redesign spec Section 11, risk 8).
3. **Criteria-scoped prompt** — Section 5.1/5.2's field lists above, not an open-ended "tell me
   everything about this company."
4. **Explicit, bounded effort/thinking budget** (`AnthropicChatOptions.effort(...)`,
   `.thinkingAdaptive()`/`.thinkingDisabled()`) instead of the implicit high-effort default.
5. **Stage 1's valuation figures passed into the Stage 2 prompt as context** (Section 3's
   `stage1Snapshot` parameter) — the mechanism behind Guardrail A's and B's 2026-07-31 updates
   above.

## 6. Integration & triggering

**Superseded 2026-07-31** by the redesign spec's own Sections 5, 7, and 8 (three-tier funnel,
Selection Logic's four trigger modes, shared budget cap, full data-flow diagram) — those are now the
authoritative source for trigger topology. This section is updated to match rather than duplicated in
full; see the redesign spec for the complete picture.

- **Trigger, widened 2026-07-31:** previously manual-only ("Request AI analysis" button); now four
  paths all feed the same funnel (redesign spec Section 7 "Selection Logic"): (a) automatic daily
  Scheduler draw, (b) operator-supplied sector/country filtered random draw, (c) operator-supplied
  specific ticker, (d) operator-triggered Watchlist Re-check (Stage 1 only, never invokes
  `research_company`). The manual button from the original design maps onto paths (b)/(c); path (a)
  is new and is the redesign's core mechanism, not an edge case.
- **Access control, unchanged in principle:** manual trigger paths ((b)/(c)/(d)) remain
  operator-only, protected by the existing single-user login (main spec Section 9) — a public
  visitor never triggers new research. The automatic path (a) runs server-side on a schedule, not
  behind any user action, so this guardrail doesn't apply to it the same way; its cost exposure is
  instead bounded by the shared daily/monthly Stage 2 cap (redesign spec Section 10).
- **`lastAnalyzedAt` → per-criterion tier-of-origin + as-of date (widened 2026-07-31):** a single
  record-level timestamp is no longer sufficient once a `ResearchRecord` can mix criteria confirmed
  at different tiers and different times (redesign spec Section 9) — each criterion now carries its
  own tier-of-origin and as-of date, shown on the dashboard drill-down (redesign spec Section 7).
- **Hint for the likely next report month:** unchanged from the original design — a rough fallback
  estimate from historical filing dates the agent has already found (last found report + ~3 months),
  "unclear" shown rather than a guessed date when no reliable schedule exists (Section 4). Still
  relevant for manual re-research of portfolio positions (redesign spec Section 8, "Portfolio
  monitoring").
- **No lockout on re-analysis, for manual paths:** manual triggers stay available at any time, not
  gated by a waiting period. The automatic daily path is separately rate-limited by design (1–2
  Stage 2 executions/day, redesign spec Section 10) — not a per-ticker lockout, a shared daily
  budget.
- **Result flows into `ResearchRecord` (updated 2026-07-31):** the original design's "extends
  `Suggestion`/`FundamentalAlert` directly" is superseded by the redesign spec's data model
  (Section 9 there) — every research outcome, pass or fail, at any tier, is appended to
  `ResearchRecord`; `Suggestion` and `RejectedCandidate` are views over it, not separately written
  entities. Portfolio-position re-research (this document's original intent) reuses the same
  `ResearchRecord`/Stage 2 mechanism directly, without Stage 0/1 gating (redesign spec Section 8).

## 7. Operations & success factors

- **Error/timeout behavior:** a third button state alongside "result available"/"not yet
  requested": "Research failed, please try again later" on timeout or when no usable sources were
  found. Prevents the app from looking broken on a first failure.
- **Cost circuit breaker, superseded 2026-07-31:** this document's original "simple global daily
  limit" is now the redesign spec's authoritative **shared Stage 2 daily/monthly budget cap**
  (Section 10 there), covering the automatic Scheduler run and both manual new-candidate trigger
  paths combined — not a separate limit to maintain here. An AWS budget alert as a secondary
  bugs/unexpected-behavior backstop is still worth keeping regardless of the app-level cap.
- **Eval set, widened 2026-07-31:** a small set (5–10) of well-known tickers with a stable, known
  character (e.g., a clear moat case, a clear value-trap case), used to periodically re-check
  analysis quality — now needs to cover **both tools**: Stage 1's numeric-snapshot accuracy and
  Stage 2's full criteria set, not just the original qualitative summary. Protects against a prompt
  change or model update silently degrading quality — the TDD equivalent for the non-deterministic
  part of the application, consistent with the main spec's existing TDD commitment (Section 7.1).
- **Versioning:** every stored analysis is tagged with a prompt/schema version. Needed so the
  quarter-over-quarter comparison (Section 6) doesn't silently mix old analysis generations with
  new ones once the analysis logic is later improved. **Needs a bump 2026-07-31** — the current
  `CURRENT_PROMPT_VERSION = "research-v1"` reflects the narrow original scope; once
  `ResearchPromptBuilder`/`CompanyResearchResult` are rewritten against Section 5 above
  (reconciliation step 3), this must become `research-v2` (or similar) so any pre-rewrite records
  are never mixed with post-rewrite ones — there are none in production yet, so this is a
  forward-looking note, not a migration.
- **Visibility in the repo:** the architectural decisions in this document (standalone MCP server,
  guardrails A–D, eval approach) should be documented in the project docs/README clearly enough to
  be recognizable without a live demo — matching the learning goal recorded in `PROJECT-STATUS.md`
  of making AI competence demonstrable, not just functionally present.

## 8. Cost estimate (rough order of magnitude)

**Superseded 2026-07-31** by the redesign spec's Section 10 (authoritative cost control) and Section
11 risk 8 (the target is still unvalidated by a real successful call). This section's original
estimate assumed manual, quarterly-cadence triggering only — call frequency is now dominated by the
automatic daily path instead, so the estimate below is kept only as historical context, not as the
current target.

- **Hosting:** near zero given the serverless deployment (Section 3), regardless of trigger
  frequency, compared to an always-on server with ongoing baseline cost.
- **Claude/search API cost, original (manual-only) estimate:** an agent loop (search → read →
  summarize) uses more tokens than a single prompt; Anthropic's hosted web search tool is
  additionally billed per search (~$0.01/search, confirmed 2026-07-31 — trivial on its own). At
  manual, quarterly-cadence triggering for a personal portfolio/candidate set, this was estimated to
  land in the low single-digit euros per month — notably cheaper than the (rejected) blind-daily
  news search (€20–50/month estimate).
- **Current target (redesign spec Section 10):** 1–2 Stage 2 executions/day (automatic + manual
  combined, shared cap), still aiming for low single-digit euros/month overall. **Not yet
  empirically confirmed** — the only real call to date landed at the worst-case end (~$1, and it
  errored) of this document's own Decision log cost simulation. The real cost driver, confirmed
  2026-07-31: search-result content is re-billed as input tokens on every subsequent conversation
  turn, so cost compounds with search-round count rather than scaling linearly with it — making the
  search-round ceiling (Section 5.4, measure 2) the most load-bearing lever. Reconciliation step 4
  (redesign spec Decision log) is the actual empirical test of this target.

## 9. Open items to verify before implementation

1. ~~Availability of an earnings calendar endpoint at the chosen fundamentals data provider~~ —
   **moot 2026-07-31**, the fundamentals data provider this referred to no longer exists (Section
   1). The report-month hint (Section 6) falls back to the historical-filing-date estimate only.
2. Terms of use of the relevant investor relations sites (Section 4).
3. Concrete SEC EDGAR integration (which endpoints, rate limits) for US tickers.
4. Exact Lambda timeout and the UX-side wait/polling behavior for the button during research (still
   relevant for the manual trigger paths; the automatic Scheduler path has no UX wait to design for).
5. **Added 2026-07-31:** whether interest coverage (Section 5.2) can actually be obtained within the
   search-round ceiling without becoming a per-candidate cost outlier — must be confirmed empirically
   during reconciliation step 3/4, not assumed (redesign spec Section 11, risk 7).
6. ~~The concrete `allowedDomains(...)` list for cost/quality measure 1 (Section 4)~~ — **decided and
   wired 2026-08-02** (reconciliation step 3): `sec.gov, www.sec.gov, stockanalysis.com,
   marketscreener.com, finance.yahoo.com, morningstar.com, reuters.com, wsj.com, macrotrends.net,
   boerse-frankfurt.de, finanzen.net, globenewswire.com, prnewswire.com, businesswire.com` (the last
   three added during task review, for company IR/press-release content backing moat/management
   quality/value-trap criteria). Still calibrated further once real search volume exists.
7. **Added 2026-07-31:** empirical confirmation of the Section 10/8 cost target via reconciliation
   step 4's live call — the explicit ~$0.05–0.10/call checkpoint from the redesign spec's Decision
   log.

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
- Chat model pinned to `claude-sonnet-5` (`spring.ai.anthropic.chat.model` in `application.yml`)
  during Task 7's manual wire-level check: without an explicit model, Spring AI 2.0.0 defaults to
  `claude-haiku-4-5-20251001`, and the live call failed with a 400 from Anthropic — Haiku 4.5 does
  not support Programmatic Tool Calling, which the hosted `web_search` tool now requires by default
  (part of Anthropic's "improved web search with dynamic filtering"). Confirmed via Anthropic's own
  model-compatibility table that only Sonnet/Opus/Fable-tier models (not Haiku) support it. Checked
  whether Spring AI 2.0.0's `AnthropicWebSearchTool` builder could instead override this directly
  (set `allowed_callers: ["direct"]` on the tool, as Anthropic's error message itself suggests) —
  it can't; the builder only exposes `allowedDomains`/`blockedDomains`/`maxUses`/`userLocation`, no
  `allowedCallers` escape hatch — so the model choice was the only available lever. Chose Sonnet 5
  over Opus 5 for the cost/quality balance appropriate to a bounded, single-ticker research-and-
  summarize task (user's explicit choice when asked). This is a genuine spec gap, not a Task
  4/5/6 implementation defect — the plan never pinned a chat model at all before this.
- The `application.yml` property alone turned out to be insufficient: decompiling
  `AnthropicChatModel.createRequest(...)` showed that when `Prompt.getOptions()` is already an
  `AnthropicChatOptions` instance (true here, since `CompanyResearchAgent` always builds one to
  attach the web search tool), it's used as-is with **no merge** against the property-configured
  default options — so a `model` left unset on the per-request options falls through to the
  Anthropic SDK's own hardcoded default (Haiku 4.5), regardless of `application.yml`. Real fix:
  `CompanyResearchAgent` now takes the model as a constructor parameter
  (`@Value("${spring.ai.anthropic.chat.model:claude-sonnet-5}")`) and sets it explicitly via
  `.model(com.anthropic.models.messages.Model.of(model))` on the per-request builder — a genuine
  constructor-signature change to already-committed Task 4/5 code, not just a config tweak.
  Discovered live during Task 7's manual check (first two attempts after the YAML-only fix still
  failed with the identical Haiku error). Also found and fixed a second, unrelated layering issue
  while diagnosing this: `src/test/resources/application.yml` (a minimal test-only config with a
  dummy API key, from Task 1) shadows `src/main/resources/application.yml` entirely on the test
  classpath, so the new `@Value` needed its own default (`:claude-sonnet-5`) — matching the existing
  pattern already used for `timeoutSeconds` — rather than duplicating the model into the test config.
- **Cost/latency guardrail added after a real Sonnet 5 call took >55s and cost more than $1**:
  `AnthropicWebSearchTool.builder()` was previously called with no `maxUses`, so the model could
  issue an unbounded number of web searches per single research call. Added
  `research.agent.web-search-max-uses` (default 5) and wired it into the per-request
  `AnthropicWebSearchTool.builder().maxUses(...)`. Separately, decompiling the OkHttp-based call
  path confirmed `future.cancel(true)` in `callWithTimeout(...)` does NOT actually abort the
  in-flight request: `chatModel.call(...)` blocks on a plain OkHttp socket read, which does not
  react to `Thread.interrupt()` (OkHttp calls are only cancellable via the `okhttp3.Call` object
  itself, which Spring AI's `ChatModel` abstraction does not expose). So a "timeout" on our side
  only stops us from waiting — the real Anthropic request, and its cost, keeps running server-side
  regardless. This is now logged as a warning on timeout and documented in code; `maxUses` is the
  actual cost bound, not the timeout. A full fix (true cancellation) would require bypassing Spring
  AI's `ChatModel` abstraction to talk to the raw `com.anthropic` client directly — judged out of
  scope for this task given the size of that change relative to the risk.
- **Follow-up session: cost simulation and candidate cost/quality improvements, without any live
  API call.** Prompted by a report from a separate session that a single research call had cost
  "about $1". Re-derived the request `CompanyResearchAgent` actually builds (model, prompt text,
  web-search tool config) purely from the source and `application.yml`, without touching the real
  `ANTHROPIC_API_KEY` (explicit user constraint). Findings:
  - The fixed instructional part of `ResearchPromptBuilder`'s prompt is small (~460 tokens,
    ~1.8K characters for a representative ticker) — not the cost driver.
  - The documented ">$1" incident above (the `maxUses` guardrail entry) predates that guardrail:
    it happened with *unbounded* web searches. With today's default `maxUses(5)`, a single call is
    modeled as landing roughly in the $0.08–$0.25 range for typical cases, with $0.80–$1.20 only
    plausible in a worst case of heavy adaptive thinking plus large accumulated search-result
    content in a single request — still possible, just less likely now than when the >$1 sample was
    taken.
  - Decompiled the resolved `spring-ai-anthropic-2.0.0.jar`
    (`AnthropicChatOptions.Builder`, `AnthropicCacheOptions`, `AnthropicWebSearchTool.Builder`) to
    check which cost/quality levers Spring AI 2.0.0 actually exposes beyond what
    `CompanyResearchAgent` currently uses (`model`, `webSearchTool.maxUses`). Confirmed available
    but unused: `.effort(OutputConfig.Effort)`, `.thinkingAdaptive()` / `.thinkingDisabled()`,
    `.cacheOptions(AnthropicCacheOptions)`, and `AnthropicWebSearchTool.Builder.allowedDomains(...)`.
  - Candidate improvements identified, in priority order, **none implemented yet at the time** — the
    user asked to record these for a later session rather than act on them then. Item 1 was
    implemented in the following session (see next entry).
    1. Log `usage` from `response.getMetadata()` (input/output/cache tokens) per research call.
       Currently there is no per-call cost visibility beyond what's manually checked in the
       Anthropic console; this is a prerequisite for evaluating any of the levers below with real
       data instead of estimates.
    2. Set `effort` explicitly (e.g. `OutputConfig.Effort.MEDIUM`) instead of relying on the
       implicit default (`high`, with adaptive thinking on by default for `claude-sonnet-5`) —
       likely the largest unused lever on the thinking-token side, independent of search count.
       Trade-off: needs an empirical quality comparison before adopting.
    3. Set `allowedDomains(...)` on `AnthropicWebSearchTool.builder()` to trusted financial/
       regulatory sources (e.g. SEC EDGAR, investor-relations domains, major financial news).
       Plausibly reduces both wasted search rounds on low-quality pages and the untrusted-content
       surface that Guardrail E (prompt-injection resistance) has to defend against.
    4. Restructure `ResearchPromptBuilder` so the static instruction block precedes the
       interpolated ticker/company name (currently interpolated first, which would defeat any
       future prompt caching). Not useful today — the fixed prompt is below Sonnet 5's 1024-token
       cache minimum — but prepares for caching once the instruction block grows.
    5. Revisit the deferred true-cancellation fix from the guardrail entry above (bypass Spring
       AI's `ChatModel` to use the raw `com.anthropic` client with a real cancellable
       `okhttp3.Call`). `maxUses` is currently the only real cost ceiling; a client-side timeout
       still does not stop server-side billing.
    6. Re-tune `research.agent.web-search-max-uses` (currently 5) empirically, once (1) provides
       real per-call token data to tune against.
- **Implemented candidate improvement 1: per-call usage logging.** `CompanyResearchAgent.research()`
  now logs `response.getMetadata().getUsage()` (promptTokens, completionTokens, totalTokens,
  cacheReadInputTokens, cacheWriteInputTokens) right after `callWithTimeout(...)` returns, before
  parsing. Verified via `javap` on `AnthropicChatModel` that Spring AI already aggregates usage
  across an internal multi-turn tool-use loop via `UsageCalculator.getCumulativeUsage(...)` before
  returning the `ChatResponse` — so a single log line after one `chatModel.call(prompt)` already
  reflects the *whole* research call's token cost (all web_search rounds included), not just the
  final turn. **Confirmed limitation while implementing this**: Anthropic's own `Usage` object has
  no separate thinking-token count — `com.anthropic...Usage.outputTokens()` maps 1:1 onto Spring
  AI's `completionTokens`, bundling thinking and the final answer into one number. This log line can
  only give an indirect signal (a `completionTokens` count far above the ~400-600 tokens the final
  JSON answer alone needs implies heavy thinking); isolating actual thinking-token spend would
  require additionally inspecting the raw `ThinkingBlock` content length, not attempted here.
  Usage is `null` on a client-side timeout (no response is ever returned), so `logUsage(...)` warns
  instead of logging in that case rather than throwing — covered by
  `warnsInsteadOfFailingWhenUsageMetadataIsMissing` in `CompanyResearchAgentTest`, alongside
  `logsTokenUsageAfterASuccessfulCall` which asserts on the log content via a Logback
  `ListAppender` attached in `@BeforeEach`/detached in `@AfterEach`.
- **Manual simulation of a real research call for AAPL, and a resulting scope question on
  `valueTrapAssessment`.** To sanity-check the prompt and the cost/thinking analysis above without
  a live API call, the controller manually executed `ResearchPromptBuilder`'s exact prompt text for
  ticker AAPL / Apple Inc., substituting its own web search for the Anthropic-hosted `web_search`
  tool. Two findings:
  - **Search-round count exceeded the configured guardrail.** Disambiguating "most recent quarterly
    report" took 7 search rounds, not the ≤5 the app enforces via `research.agent.web-search-max-uses`
    — analyst "Q3 2026 preview" articles (Apple's non-calendar fiscal year) had to be distinguished
    from the actually-filed Q2 FY2026 10-Q. In the real app this ambiguity would have hit the
    `maxUses(5)` ceiling first, plausibly forcing an answer off the wrong/stale report rather than
    costing more — a quality risk, not just a cost one. (The simulation's own token/dollar figures
    are not valid evidence either way: the controller's web search tool pre-digests results into a
    summary, unlike Anthropic's hosted `web_search` tool which returns raw snippets the model itself
    must read and synthesize each round, and no extended-thinking tokens were actually incurred — so
    no cost number from this exercise was carried over as an estimate.)
  - **`valueTrapAssessment` asks for something the prompt never sources.** The prompt asks the model
    to assess whether "the valuation appears explained by fundamentals," but never instructs it to
    look up the company's actual valuation multiple (P/E, sector comparison) — the simulation needed
    an ad hoc extra search for that, outside the prompt's own instructions. Checked against
    `docs/superpowers/specs/2026-07-21-value-screener-design.md`: this is consistent with that spec's
    intent, not an oversight to patch by adding a search instruction — the backend's planned Data
    Provider Client / `FundamentalSnapshot` (Phase 2, not yet built) is meant to own valuation
    numbers, with Guardrail B cross-checking the agent's qualitative claims against it *after* the
    call, in the backend. Proposed resolution, in two parts:
    1. **No code change needed for the search-count/ambiguity finding directly** — it's evidence for
       candidate improvement 6 above (re-tune `web-search-max-uses` once real usage data exists) and
       for candidate improvement 3 (`allowedDomains(...)` to steer straight at primary filings like
       SEC EDGAR instead of analyst preview articles).
    2. **For `valueTrapAssessment`**: keep the field qualitative-only (describe what management/risk
       disclosures say, without the model judging whether that justifies the valuation) rather than
       having the agent search for or guess at a valuation multiple. Once `FundamentalSnapshot` exists,
       extend `research_company(ticker, companyName)` with a third parameter carrying the backend's
       already-computed valuation figure, interpolated into the prompt as given context — not
       re-derived by the model — so there is one source of truth for the number and one fewer search
       round per call. Not implemented; recorded here for whenever Phase 2's Data Provider Client
       lands.
- **2026-07-31, reconciliation step 2 (screening-cost redesign):** with the Stage 1 mechanism decided
  (redesign spec, 2026-07-31) and this document identified as the one piece of already-committed
  design needing an actual rewrite (not just an extension) to match the redesign, updated this
  document's scope, interface, output contract, triggering, and cost sections. Two genuine
  contradictions were found and resolved while doing so, beyond the already-known criteria-catalogue
  gap:
  - **Automatic triggering.** This document's original Section 2 explicitly excluded scheduler-driven
    automatic triggering ("stays deliberately manual/cost-incurring"), decided when the agent was a
    rare, quarterly-cadence, button-only feature. The redesign spec makes automatic daily triggering
    (1–2 Stage 2 executions/day) the *core* mechanism of the whole three-tier funnel. Resolved by
    reversing the exclusion (Section 2) and rewriting Section 6 around the redesign's four trigger
    modes, treating the redesign spec as authoritative for trigger topology.
  - **Capital allocation.** This document's original Section 2 listed capital allocation as
    "considered, not adopted for now." The redesign spec's 2026-07-31 product-strategy review
    explicitly adopted management quality/capital allocation as a Stage 2 criterion, with
    insider/founder ownership as its supporting Stage 1 signal. Resolved by reversing the exclusion
    and adding both to Section 5.2/5.1.

  Both were judged genuine oversights from the original 2026-07-24 design being decided a week before
  the redesign, not deliberate re-litigations — recorded here (rather than silently overwritten) so a
  future reader can see the exclusion existed and why it was reversed, consistent with how the
  redesign spec's own Decision log treats reversed positions elsewhere.

  **Guardrail B redefinition, confirmed with the user before writing:** rather than leaving Guardrail
  B pointing at the now-nonexistent `FundamentalSnapshot`, or dropping it outright, the user chose
  (of three options offered) to redefine it as a Stage-1-vs-Stage-2 consistency check — Stage 2 is
  given Stage 1's snapshot values as prompt context (cost/quality measure 5), and if Stage 2's own
  research diverges materially from what it was given, that divergence is flagged. Kept as a
  rule-based main-application check, matching the original guardrail's own architecture principle
  (Section 3: the MCP server doesn't own cross-checking logic).

  **Not done in this pass** (reconciliation step 3, still pending): the actual code
  (`ResearchPromptBuilder`, `CompanyResearchResult`, `CompanyResearchTool`) has not been touched —
  this was a design-document update only. `promptVersion` still reads `"research-v1"` in code even
  though this spec now describes a materially different output contract; see Section 7's Versioning
  note above.

- **2026-08-02, reconciliation step 3 (screening-cost redesign):** rewrote the actual module code to
  match the design updated in step 2. `CompanyResearchResult` now carries the full Section 5 criterion
  set (`marginTrend`, `freeCashFlowTrend`, `profitStability`, `interestCoverage`, `currentRatio`,
  `moatAssessment`, `managementQuality`, `valueTrapAssessment`); `CURRENT_PROMPT_VERSION` bumped to
  `"research-v2"`. Added the Stage 1 side of the two-tool architecture decided in the Stage 1
  mechanism decision (2026-07-31): `QuickResearchPromptBuilder`, `QuickResearchResult`,
  `Stage1Snapshot`, wired into a second MCP tool (`quick_research_company`) alongside the existing
  `research_company`.
  - **Per-criterion, not all-or-nothing, source verification** (user decision, 2026-08-01): if an
    individual criterion's cited URL isn't found in the model's actual `Citation` list, that one
    criterion becomes `null` rather than collapsing the whole result to `LOW` confidence — a single
    unverifiable claim no longer discards seven verified ones.
  - **Cost/quality measures 1–5** (Section 5.4/9) all wired: `allowedDomains` restricts search to a
    14-domain allowlist (open item 6 above), `webSearchMaxUses` bounds search rounds per stage
    (Stage 1: 1, Stage 2: default 5, both `@Value`-overridable except Stage 1's), explicit bounded
    `effort` (Stage 1 `LOW`, Stage 2 `MEDIUM`) with `thinkingDisabled()` replaces unbounded reasoning
    budget, and Stage 1's `Stage1Snapshot` is passed into the Stage 2 prompt as context (also the basis
    for Guardrail B's redefinition from step 2).
  - **Execution note:** Maven compiles the whole module for every `mvn test` run, so the plan's
    original 7 fine-grained TDD tasks could not each reach an independent green build — Tasks 1–6 were
    bundled into one review/commit checkpoint (Task 7 stayed separate) after this was discovered mid-
    execution; approved by the user. Full detail in the SDD workspace ledger (not retained after the
    branch merges).
  - **Not done in this step:** the live end-to-end cost check (reconciliation step 4, open item 7
    above) — `CURRENT_PROMPT_VERSION`/`allowedDomains` are now real, but the ~$0.05–0.10/call target is
    still unconfirmed against an actual API call.

- **2026-08-10, reconciliation step 4 (screening-cost redesign) — Stage 2 prompt/cost simplification,
  approved design, not yet implemented.** Two live `research_company(AAPL)` attempts (2026-08-08,
  2026-08-10) both timed out with no usable result — the first at the then-current 120s local timeout,
  the second still at a since-raised 240s. Root cause confirmed by reading `callWithTimeout`: on a
  local timeout, `future.cancel(true)` only stops *this process* from waiting — the underlying OkHttp
  call blocks on a plain socket read that ignores `Thread.interrupt()`, so the Anthropic request keeps
  running and billing regardless, and `logUsage(...)` (which runs only after a successful return) is
  never reached — no usage figures are recoverable from a timed-out attempt. Raising the local timeout
  further doesn't address why the call takes so long in the first place: Stage 2 asks for all 8
  Section 5 criteria in one call, at `webSearchMaxUses=5`/`effort=MEDIUM`, calibrated only off Stage 1's
  much lighter 1-search/`LOW` profile.

  Rather than keep raising the timeout (each failed attempt is a real, unrecoverable cost with zero
  result), decided to make Stage 2 itself cheaper and faster, at an accepted quality cost:
  1. **Prompt (`ResearchPromptBuilder`):** generalize the existing `interestCoverage`-only escape hatch
     ("if it would take more searching than the other criteria combined, leave it out") into one
     upfront budget instruction covering all 8 criteria — at most one focused search per criterion,
     omit (`null`) rather than chase it further. The old `interestCoverage`-specific wording is removed
     as redundant; `profitStability`'s "only report with genuine multi-year figures" wording is
     unrelated (a quality bar, not a search-budget instruction) and stays.
  2. **Hard caps, not just prompt wording:** the `interestCoverage` escape hatch already existed and the
     call still timed out, so an advisory-only fix was judged insufficient — `webSearchMaxUses`
     (`application.yml`, plus its `@Value` fallback default) drops from 5 to 3, and `STAGE2_EFFORT`
     (`CompanyResearchAgent`) drops from `MEDIUM` to `LOW`, matching Stage 1. Stage 1 itself
     (`STAGE1_MAX_USES`, its own `LOW` effort) is untouched.
  3. **Accepted, deliberately unaddressed consequence:** more `research_company` results will have
     several of the 8 criteria come back `null`. `ConfidenceLevel` (currently only `HIGH`/`LOW`, `HIGH`
     awarded as soon as ≥1 of 8 criteria resolves) is left exactly as-is — confirmed via grep that it
     has no consumer yet outside this module, so any "is 2-of-8 actually good enough" threshold would
     be a guess at a not-yet-designed Selection Logic component's needs. Each criterion field on
     `CompanyResearchResult` already exposes its own presence/absence, so no information is lost for
     whenever that consumer is designed — same deferred-until-the-real-consumer-exists pattern already
     used for `valueTrapAssessment`'s missing valuation figure above.

  Lower `webSearchMaxUses` reduces recall on multi-round disambiguation cases (the earlier AAPL manual
  simulation needed 7 rounds for fiscal-quarter labeling, decision log entry above) — accepted
  intentionally here in favor of a call that actually completes.

  Not implemented yet. Recorded via `docs/superpowers/specs/2026-07-24-company-research-agent-design.md`
  rather than a new dated spec file, matching this document's existing convention of recording
  incremental refinements to this same component as Decision log entries instead of one spec file per
  change.
