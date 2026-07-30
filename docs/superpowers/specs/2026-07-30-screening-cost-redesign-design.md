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

## 1. Purpose

Deliver the same product goal as the original design — regularly surfacing new stock ideas that are
fundamentally strong, fairly valued, and crisis-resistant — without depending on either a paid
market-wide screener endpoint or an unpredictable, expensive per-candidate AI web-search call. The
guiding cost target: AI research spend in the low single-digit euros per month, fully predictable,
never spiking on a single call or a single visitor.

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
  Suggestions, publicly readable, no trigger capability for anonymous visitors.

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
  ratio, FCF trend) where obtained, qualitative business-model/moat text, final pass/fail.
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
