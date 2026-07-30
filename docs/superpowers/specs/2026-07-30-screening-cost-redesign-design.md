# Screening & Research Cost Redesign — Design

Last updated: 2026-07-30
Status: Design approved by the user in this brainstorming session, implementation not started.

**Relationship to earlier specs:** this document replaces the *approach* behind Sections 3–5 and
Risks 1/3 of `2026-07-21-value-screener-design.md` (the Screening Engine, the Data Provider Client,
and the quantitative half of the AI Assessor). It does not replace the Company Research Agent
itself (`2026-07-24-company-research-agent-design.md`) — that component is kept and extended
(Section 6 below), not rebuilt. Written in English per the project's docs-language policy for new
specs; the two earlier specs remain in German.

## Deutsche Zusammenfassung

Das ursprüngliche Design (2026-07-21) wollte eine Screener-API für marktweites Screening nutzen und
pro Top-Kandidat einen Claude-Aufruf mit Websuche für die Geschäftsmodell-/Burggraben-Bewertung.
Beides ist in der Praxis gescheitert: der Screener-Endpunkt des geprüften Fundamentaldaten-Anbieters
ist kostenpflichtig (nur Einzelabfragen sind frei), und ein einzelner Recherche-Call kostete real ca.
$1 (Websuche mit bis zu 7 Suchrunden bei mehrdeutigen Quartalsangaben). Dieses Dokument ersetzt beide
Komponenten durch **einen einzigen, KI-getriebenen Mechanismus ohne externen Fundamentaldaten-Anbieter**:

- **Universum:** alle über Gettex handelbaren Aktien (breit, international), dedupliziert nach
  Unternehmen, gruppiert nach Branche/Sitzland.
- **Dreistufiger Recherche-Trichter** pro Kandidat: Stufe 0 (kein Websuche, Trainingswissen, fast
  kostenlos) → Stufe 1 (ein knapp gedeckelter Suchschritt) → Stufe 2 (volle Tiefenrecherche, jetzt mit
  vier bereits dokumentierten, aber nie umgesetzten Kostenmaßnahmen: Domain-Einschränkung auf
  Primärquellen, fester Suchrunden-Deckel, zugeschnittener Prompt, festes Denkbudget).
- **Eigene wachsende Wissensdatenbank:** jedes Rechercheergebnis (Pass *und* Fail) wird dauerhaft
  gespeichert — Recherche ist eine Investition in Marktabdeckung, keine wiederkehrende Kostenlast.
- **Bewertungsprüfung** ohne teure Peer-Recherche: absolute Notbremse + Vergleich mit der eigenen
  Historie der Firma + einmalig/quartalsweise gecachte Branchen-KGV-Referenztabelle (kostenlos
  verfügbar, siehe Quellen im Entscheidungsverlauf).
- **Auslösung:** täglicher automatischer Zufalls-Tageslauf (1–2 Firmen, Rotation nach "zuletzt
  geprüft", über Branchen gestreut) **plus** zwei manuelle, nur dem eingeloggten Betreiber
  vorbehaltene Wege (gezielte Firma aus der Gettex-Liste; gefilterte Zufallswahl nach Branche/Sitzland)
  — alle drei zusammen zählen gegen **ein gemeinsames** Tages-/Monatsbudget, damit der Kostendeckel
  real bleibt. Öffentliche Besucher lösen nie neue Kosten aus, sie sehen nur bereits fertige
  Ergebnisse.
- **Bewusst zurückgestellt:** eine spätere, kostenlose Kurs-Anreicherung bereits recherchierter Firmen
  (um "wurde inzwischen günstig" ohne neue KI-Kosten zu erkennen) — erst bei Bedarf, wenn die reale
  Trefferquote der Zufallsrotation sich als zu niedrig erweist.
- **Ergänzung (2026-07-30, Produktstrategie-Review):** Zweck geschärft — die App soll keinen fertigen
  Anlage-Verdikt liefern, sondern einen Hinweis, der die eigene, tiefere Recherche des Nutzers wert
  ist (Abschnitt 1). Daraus folgt: kein Tracking realisierter Rendite und keine periodische
  Frische-Neuprüfung (beides erwogen, bewusst nicht umgesetzt — zu aufwändig für den eigentlichen
  Zweck). Stattdessen zeigt das Dashboard pro Kriterium, auf welcher Stufe (0/1/2) und wann es geprüft
  wurde (Abschnitt 7/9) — nutzt nur bereits vorhandene Daten, keine neuen Kosten.
- **Weitere Ergänzung (gleiches Review):** Marktkapitalisierungs-Untergrenze für den Universe Provider
  wieder aufgenommen (aus dem verworfenen Data-Provider-Entwurf übernommen, sonst geht Stage-2-Budget
  an nicht recherchierbare Mikro-Caps verloren) und eine offene Lücke im Company-Research-Agent-Prompt
  geschlossen: die Bewertungsfrage (`valueTrapAssessment`) wartete auf eine Kennzahl aus dem inzwischen
  entfallenen Data Provider Client — diese Abhängigkeit wäre nie aufgelöst worden. Stattdessen liefert
  jetzt Stage 1s ohnehin berechnetes KGV/KBV genau diese Zahl in den Stage-2-Prompt.

## 1. Purpose

Deliver the same product goal as the original design — regularly surfacing new stock ideas that are
fundamentally strong, fairly valued, and crisis-resistant — without depending on either a paid
market-wide screener endpoint or an unpredictable, expensive per-candidate AI web-search call. The
guiding cost target: AI research spend in the low single-digit euros per month, fully predictable,
never spiking on a single call or a single visitor.

**What "surfacing" means (clarified 2026-07-30):** the goal is not for the app to deliver a finished
verdict the user acts on directly — it's a lead-generation tool. A Suggestion only needs to be
promising enough to justify the user's own, deeper manual research before any real decision; it does
not need to be a fully self-validated pick. This directly shapes Section 6/7/9 below: the app's job is
to be honest about *how confidently* each criterion was established, not to hide that behind a binary
pass/fail.

## 2. Scope

### In scope
- A broad, international universe of Gettex-tradable equities as the search space (not limited to a
  single index), deduplicated by company/ISIN rather than by exchange listing
- A three-tier cost funnel per research candidate (Section 5)
- A self-built, permanently growing knowledge base of research outcomes (pass and fail alike)
- One daily automatic batch run, plus two operator-only manual trigger paths, all sharing one budget
  cap (Section 7–8)
- A free, periodically-cached sector P/E benchmark table for cheap relative-valuation context
  (Section 6)
- Reuse of the same Stage 2 mechanism for existing portfolio positions (small, fixed volume — not a
  cost concern)

### Explicitly out of scope (unchanged from the original design)
Trading/order execution, broker integration, multi-user accounts, CSV/statement import, email
notifications, a free-form chat interface.

### Deliberately deferred, not part of this design iteration
- Live/cheap price enrichment of already-researched companies to detect "became cheap since last
  check" without new AI cost (called "Approach B" in the decision log) — revisit only if the real
  hit rate under this design turns out too low.
- The concrete technical mechanics of acquiring the Gettex universe list and the sector benchmark
  table (web search/scrape vs. another method) — an implementation-level task, not a design decision.
- Exact numeric thresholds for Stage 0/1 pass/fail — calibrated during implementation, not fixed
  here.

## 3. Why the original design had to change

Two of the "open risks" flagged in Section 8 of the 2026-07-21 design materialized during
implementation, not just as theoretical risk:

- **Risk 1 (screener access):** the evaluated fundamentals provider does not expose its market-wide
  screener endpoint on the free tier — only single-ticker lookups are free. A market-wide daily
  screen as originally designed is not available without a paid plan.
- **Risk 3 (AI cost):** a real Company Research Agent call (Claude + hosted web search) was reported
  costing approximately $1. A static, offline cost analysis in the Company Research Agent spec's
  decision log (2026-07-26) estimated $0.06 (low) to $0.85 (worst case) per call — driven by
  unpredictable multi-round web search. A manual AAPL simulation needed 7 search rounds just to
  disambiguate analyst fiscal-quarter commentary from the actually filed 10-Q.

Net effect: neither half of the original pipeline ("get a broad candidate list" and "AI-assess the
top candidates") could be relied on to be both broad and cheap under the original architecture.

## 4. Core idea

Drop the idea of a separate, purpose-built numeric fundamentals provider entirely. Use AI research
— with web search used only where and to the extent it earns its cost — as the single source of
both quantitative facts and qualitative judgment, and treat every research outcome (not just
successful ones) as a permanent addition to a self-owned knowledge base. Market coverage grows over
time as an asset instead of being re-purchased on every check.

## 5. Three-tier research funnel

Applied to every candidate, regardless of whether it was selected by the automatic daily rotation or
by an operator's manual trigger (Section 7):

| Tier | What it does | Cost | Outcomes |
|---|---|---|---|
| **Stage 0** | LLM judgment using only its trained knowledge, no tool calls, against a small set of hard disqualifying criteria | near-zero | *clearly bad* → reject, no further cost; *looks promising* → advance; *uncertain / company not well known to the model* → **also advance** (never rejected for cost reasons alone — a lesser-known company being unknown to the model is not evidence it's a bad company) |
| **Stage 1** | One tightly bounded web search step (current quote + 1–2 headline figures from a reliable source) to ground/validate Stage 0's read | low | confirms reject, or confirms enough potential to justify the expensive tier |
| **Stage 2** | Full deep research via the existing Company Research Agent — now tightened per Section 6 | the deliberately budgeted, capped cost | full structured fundamentals + qualitative moat/business-model write-up + final pass/fail against the complete criteria set (Section 6) |

Every outcome at every tier — not only Stage-2 passes — is written to the knowledge base with a
timestamp, so the rotation logic (Section 7) always knows what was checked, when, and how far it
got.

## 6. Criteria — what's checked, at which tier, and how it's sourced cheaply

| Criterion | Cheap source | Effort | Tier |
|---|---|---|---|
| Understandable business model | Basic "what does this company do" description | very low | Stage 0 |
| Recognizable moat (rough read) | Often inferable from general/trained knowledge for known companies | low | Stage 0 (rough), deepened in Stage 2 |
| Current P/E, P/B | Standard key-statistics figure on almost any finance page | low, one search hit | Stage 1 |
| Current ROE, debt/equity | Same standard key-statistics page as above | low | Stage 1 |
| Current ratio | Sometimes on the same page, sometimes needs a balance-sheet detail | medium | Stage 1 if available, else Stage 2 |
| Margin trend (stable/growing) | Needs multi-year history | high | Stage 2 |
| Free cash flow positive & growing | Cash-flow-statement-specific, multi-period | high | Stage 2 |
| No strong profit decline over 5–10 years | Multi-year history — the most expensive criterion to verify | highest | Stage 2 |

**Valuation check (industry-relative, but without live peer research):** the original design computed
an industry median for free because it was a side effect of the paid screener's market-wide result
set. Without that, a true live peer comparison would require researching 2–3 comparable companies per
candidate — exactly the kind of extra cost this redesign avoids. Instead, valuation is judged by
combining three signals, only one of which costs anything per candidate:

1. **Absolute floor (Stage 0, free):** negative or extreme P/E (e.g. > 50) is rejected outright,
   regardless of everything else.
2. **Own historical range (Stage 1, one search hit):** current P/E/P/B vs. the company's own 5-year
   average — commonly published directly on standard finance pages, no extra computation needed.
3. **Cached sector benchmark (Stage 1, effectively free per candidate):** current P/E vs. a small
   (~10–20 sector) reference table of average sector P/E ratios. Multiple providers publish this table
   for free (confirmed via research: Siblis Research, GuruFocus, FullRatio, Basis Report). The table
   is fetched and cached **once, refreshed only periodically (e.g. quarterly)** — not researched per
   candidate — then every candidate's own P/E (already fetched in step 2) is compared against it via a
   simple lookup.

A candidate passes the valuation check only if it clears the absolute floor, sits at or below its own
historical average, and isn't materially above its sector's cached benchmark.

## 7. Architecture / components

- **Universe Provider** — acquires and periodically refreshes the list of Gettex-tradable equities,
  deduplicated by company/ISIN (a single company often has multiple Gettex listings), grouped by
  sector and country of headquarters. Acquisition mechanics are an implementation detail (Section 2).
  Carries a **market-cap floor to exclude micro-caps** (revived from the superseded data-provider-
  client draft's own Section 2, where it was adopted for risk reasons and never actually dropped, just
  never re-stated here) — Gettex lists many thinly traded, data-sparse secondary listings, and every
  candidate the Selection Logic draws that turns out un-researchable in Stage 1/2 for lack of
  available information is wasted budget against the shared cap (Section 8/10). No upper cutoff —
  large, well-known opportunities should still surface.
- **Sector Benchmark Cache** — small reference table of sector-average P/E ratios, refreshed
  periodically (e.g. quarterly) from free public sources, used for the valuation check in Section 6.
- **Selection Logic** — the entry point into the funnel. Normally draws candidates weighted toward
  "longest since last checked" and stratified across sectors (the automatic daily path), but accepts
  three trigger modes:
  1. automatic daily draw (unfiltered, weighted-random)
  2. operator-supplied filters (sector and/or country of headquarters) for an on-demand filtered
     random draw
  3. operator-supplied specific ticker (must be in the Universe Provider's Gettex list)

  All three modes feed the *same* three-tier funnel (Section 5) — there is no separate code path for
  manual vs. automatic candidates.
- **Company Research Agent** (existing component, extended) — Stage 0 and Stage 1 are new,
  lightweight steps added ahead of the existing agent. Stage 2 itself (the existing agent) is
  tightened with four cost measures already identified but not yet implemented in its own spec's
  decision log:
  1. Web search restricted to primary sources first (e.g. SEC EDGAR, official investor-relations
     pages) instead of open web search — directly targets the fiscal-quarter-disambiguation cost
     driver found in the AAPL simulation.
  2. A fixed, tight ceiling on search rounds instead of "search until satisfied."
  3. A prompt scoped narrowly to the specific criteria in Section 6, not an open-ended "tell me
     everything about this company."
  4. An explicit, bounded thinking/effort budget instead of unconstrained reasoning.
  5. **Stage 1's valuation figures (current P/E, P/B, Section 6) passed into the Stage 2 prompt as
     given context**, resolving a previously-open item from the agent's own spec: its
     `valueTrapAssessment` was left deliberately unable to judge "is the valuation explained by
     fundamentals" because the prompt was never given an actual multiple, with the fix explicitly
     deferred until a Data Provider Client / `FundamentalSnapshot` existed to supply one (see that
     spec's decision log, 2026-07-26). That component is now permanently out of scope under this
     redesign (Section 4) — the dependency would otherwise never resolve. Stage 1 already computes the
     same figure for free, immediately before Stage 2 runs, and is now the fix's actual source instead.
- **Knowledge Base** — persists every research outcome from every tier (pass and fail), with
  timestamp and tier reached. Powers the recency-weighted rotation and, over time, lets the real hit
  rate be measured (Section 11).
- **Scheduler** — triggers one automatic daily batch via the Selection Logic. Per the user's explicit
  budget/coverage-speed trade-off (Section 11), the daily figure is deliberately small — on the order
  of 1–2 Stage-2 executions per day — favoring cost discipline over how fast the universe gets
  covered.
- **Manual trigger paths** — both operator-only, protected by the existing single-user login: (a) pick
  a specific Gettex ticker, (b) request a filtered random draw (sector/country). Public visitors never
  trigger new research of any kind — they only ever read already-completed results.
- **Dashboard** — unchanged concept from the original design: shows Stage-2 "pass" outcomes as
  Suggestions, publicly readable, no trigger capability for anonymous visitors. Per criterion
  (Section 6), also surfaces which tier established it and the as-of date (e.g. "margin trend:
  confirmed, Stage 2, 2026-07-30" vs. "moat: rough read, Stage 0, 2026-06-01") — sourced directly from
  the provenance already captured per criterion in `ResearchRecord` (Section 9), no new research or
  cost. This turns the dashboard from a binary pass/fail into a map of where a candidate is
  well-verified vs. where it's still an untested read, which is exactly what the user needs to judge
  whether it's worth spending their own research time on (Section 1).

## 8. Data flow

```
Trigger (any of the three):
  (a) Scheduler (daily, automatic, unfiltered weighted-random)
  (b) Operator: filtered random draw (sector/country)
  (c) Operator: specific Gettex ticker
        │
        ▼
  Selection Logic picks one candidate (or, for (c), uses the given one)
        │
        ▼
  Stage 0 (no search) ──clearly bad──> reject, log in Knowledge Base, stop
        │ promising / uncertain
        ▼
  Stage 1 (bounded search) ──confirmed bad──> reject, log in Knowledge Base, stop
        │ confirmed potential
        ▼
  Stage 2 (full research, tightened per Section 7)
        │
        ▼
  Result (pass or fail) logged in Knowledge Base
        │ pass only
        ▼
  Suggestion visible on public Dashboard
```

All three trigger modes draw against **one shared daily/monthly budget** for Stage 2 (Section 9) —
manual use on a given day reduces what's left for the automatic run that day, not an addition to it.

Portfolio monitoring (existing positions) reuses the same Stage 2 mechanism directly (no Stage 0/1
gating needed — the candidate set is small and fixed), run at a low, infrequent cadence (e.g. monthly
per position) since the position count is small enough not to be a cost concern.

## 9. Data model

- **UniverseEntry** — ticker(s), ISIN, company name, sector, country of headquarters, listing
  venue(s) on Gettex.
- **SectorBenchmark** — sector label, cached average P/E, date last refreshed.
- **ResearchRecord** (the knowledge base entry) — ticker/ISIN, date, tier reached, per-tier
  outcome/reason, extracted structured fundamentals (P/E, P/B, ROE, margins, debt/equity, current
  ratio, FCF trend) where obtained, qualitative business-model/moat text, final pass/fail. Each
  per-criterion value (Section 6) carries its own **tier-of-origin and as-of date**, not just the
  record-level tier reached — e.g. a Stage-2 record can still have a moat read that was only ever
  confirmed at Stage 0, and the dashboard (Section 7) needs that per-criterion granularity, not the
  record's overall tier, to show it.
- **Suggestion** — a view over `ResearchRecord` entries where the final outcome is "pass"; drives the
  public dashboard.
- **PortfolioPosition** — unchanged from the 2026-07-21 design.
- **FundamentalAlert** — same concept as the original design, but now sourced from a fresh
  `ResearchRecord` on a held position rather than a snapshot diff against provider data.

## 10. Cost control

- One shared daily (and derived monthly) cap on **Stage 2** executions — the only tier with
  meaningful per-call cost — covering the automatic scheduler run and both manual trigger paths
  combined. Set to roughly 1–2 Stage-2 executions per day, chosen to keep total AI research spend in
  the low single-digit euros per month.
- Stage 0/1 have no hard cap (their cost is negligible) but their volume should still be logged, to
  support future tuning of the funnel's thresholds.
- The four Stage-2 cost measures in Section 7 (domain-restricted search, search-round ceiling,
  criteria-scoped prompt, explicit effort budget) are mandatory parts of this design, not optional
  polish — they were already identified as needed in the existing Company Research Agent spec's
  decision log but never implemented.

## 11. Open risks / deliberately accepted trade-offs

1. **Coverage speed is deliberately sacrificed for budget discipline.** At 1–2 Stage-2 executions per
   day, full rotation through a universe of thousands of Gettex-tradable companies takes a very long
   time. The user explicitly accepted this trade-off ("Tempo ist mir egal") rather than raising the
   budget or shrinking the universe.
2. **Unvalidated hit rate.** Whether random rotation across a huge universe regularly surfaces good
   candidates, without any pre-filter beyond Stage 0/1, is an accepted bet rather than a proven fact.
   The Knowledge Base (Section 9) is what will make the real hit rate measurable once the system runs.
3. **No live revaluation of past Suggestions.** A company shown once as a "pass" is not automatically
   re-checked when its price moves; it's only re-evaluated if the rotation happens to draw it again.
   The "Approach B" price-enrichment idea (Section 2, deferred) exists as the fallback if this proves
   too stale in practice.
4. **Stage 0 reliability.** A no-search LLM check can be wrong or stale, especially for lesser-known
   companies. Mitigated, not eliminated, by never auto-rejecting "uncertain" verdicts.
5. **Universe and sector-benchmark acquisition are still open implementation questions.** Both are
   "fetch a public list periodically" problems, not yet solved technically — deliberately left to the
   implementation plan rather than fixed in this design.
6. **Valuation comparison is now approximate**, using a cached sector-median lookup instead of a true
   live peer computation from the same screening run (as the original design had, for free, as a side
   effect of the paid screener). This is an accepted precision-for-cost trade-off.

## Decision log

- 2026-07-30: Replaced the paid market-wide screener + separate AI Assessor pair with a single
  AI-research-driven pipeline, after confirming in this session that the screener endpoint is
  paid-tier only and that per-candidate AI research cost is the other real cost driver (not just a
  theoretical risk).
- Considered and rejected building a conventional quantitative pre-filter (external fundamentals DB)
  ahead of AI research — no free/cheap source for it exists given the paywalled screener; a
  from-scratch numeric database was judged not worth the added complexity for this project's scale.
- Considered pure random rotation with no pre-filter at all (user's initial proposal, defensible given
  a fixed budget and a very large universe) — refined instead into the three-tier funnel (Stage 0/1)
  once it became clear a *free, AI-only* pre-filter was achievable without any external data source.
- Rejected true live industry-peer valuation research per candidate (too costly); adopted the
  three-signal valuation check (absolute floor + own history + cached sector benchmark table) instead,
  after confirming free sector-P/E benchmark sources exist (Siblis Research, GuruFocus, FullRatio,
  Basis Report).
- Decided the daily automatic run and both operator-only manual trigger paths (specific ticker;
  filtered random draw by sector/country) share one budget cap, so manual use cannot silently blow
  past the cost target.
- Explicitly deferred: live/cheap price-refresh enrichment of already-researched companies (until the
  real hit rate is known to need it).
- **2026-07-30, discovered after this design was drafted:** a parallel, uncommitted draft
  (`docs/superpowers/specs/superseded/2026-07-29-data-provider-client-design.md`) had researched
  and partially designed a genuinely free numeric-fundamentals path — SEC EDGAR bulk data for US
  fundamentals + a free-tier commercial provider used *only* for daily price, with KGV/KBV/market cap
  computed in-app, USA-only for now (Germany/Switzerland confirmed to have no free bulk source at
  all). This directly undercuts this design's founding premise that no free numeric source exists.
  Considered and explicitly not adopted: the user decided this document's AI-only approach stands
  as the current direction, specifically to keep one coherent design rather than merging two
  differently-shaped solutions to the same problem. The prior draft's per-country provider research
  (Section 4 of that document: SEC EDGAR, EDINET, Companies House/UK-SEF, `filings.xbrl.org`, EODHD/
  FMP/Finnhub/Twelve Data cost and ToS findings) remains valid, reusable research if a future session
  revisits combining a free quantitative pre-filter with this design's AI research funnel — it was not
  wrong, it simply arrived a day earlier than this session's own conclusion and the two were not
  reconciled before this design was finalized.
- **2026-07-30, product-strategy review:** evaluated three candidate gaps against the actual product
  goal (clarified in this same review, Section 1: a lead worth the user's own research time, not a
  self-validated final verdict) — realized-return outcome tracking, staleness re-checks on already-
  passed Suggestions, and per-criterion source confidence. Realized-return tracking was considered and
  explicitly **not adopted now**: it only earns its cost if the app itself has to be trusted as the
  decision-maker, which it deliberately isn't, so it's deferred to "later, if curious how the funnel
  performs" rather than a design blocker. A full periodic staleness re-check pipeline (reusing Stage 1,
  discussed and initially favored earlier in the same review) was also **not adopted**, in favor of
  the far cheaper fix of simply displaying each Suggestion's existing `ResearchRecord` date — since the
  user reviews every lead manually before acting, a visible age is enough, no new research cost or
  pipeline needed. **Adopted:** per-criterion tier-of-origin and as-of-date, surfaced on the dashboard
  (Section 7) from data the model already captures (Section 9) — this was judged the one gap that
  actually serves "is this worth my research time," at zero additional cost. Considered rejecting a
  live global price-data API (e.g. EODHD, per the superseded data-provider-client draft's own findings
  that no free option exists internationally) as the mechanism for any of this — correctly avoided,
  since none of the adopted fixes need one; reusing Stage 1's AI web search or plain existing
  timestamps was sufficient in both cases considered.
- **2026-07-30, product-strategy review, continued:** two further gaps identified, both adopted, both
  zero-cost. (1) The Universe Provider had silently dropped the market-cap floor the superseded
  data-provider-client draft had adopted for risk reasons — re-added, since an un-researchable
  micro-cap draw wastes the same scarce Stage-2 budget this whole redesign exists to protect. (2) The
  Company Research Agent's `valueTrapAssessment` gap (its spec's own decision log, 2026-07-26: prompt
  never sources an actual valuation multiple, fix explicitly deferred until a Data Provider Client /
  `FundamentalSnapshot` supplies one) was left pointing at a dependency that this redesign permanently
  removes (Section 4) — it would never have resolved. Fixed by wiring Stage 1's already-computed P/E/
  P/B (Section 6) into the Stage 2 prompt as context instead, added as cost/quality measure 5 in
  Section 7.
