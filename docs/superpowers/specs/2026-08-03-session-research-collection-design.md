# Session-Based Research Collection & Cheaper Deep Research — Design

Last updated: 2026-08-03
Status: Design approved by the user in this session. Deliberately written as a **standalone idea**,
not yet reconciled with the existing screening/research design (see "Relationship to existing
designs" below).

## Deutsche Zusammenfassung

Idee: statt dass die App selbst automatisiert und kostenpflichtig gegen die Anthropic-API recherchiert,
sammelt der Nutzer die Grunddaten zu mehreren Gettex-Werten pro Tag **in eigenen Sitzungen mit Claude
Code** (wie dieser Unterhaltung) — nicht extrem tief, aber möglichst breit (Geschäftsmodell, groben
Burggraben, Kennzahlen-Schnappschuss), ohne dabei viele Tokens zu verbrauchen. Diese Recherche kostet
die App selbst nichts, da sie über die bestehende Claude-Code-Nutzung des Nutzers läuft, nicht über
einen von der App bezahlten API-Call. Die Ergebnisse werden über einen validierten Admin-API-Endpunkt
der App in der DB gespeichert — bewusst **nicht** per direktem SQL/MCP-Zugriff, weil ein Agent, der
gleichzeitig nicht-vertrauenswürdige Web-Inhalte verarbeitet und eine mächtige, generische
Schreib-Fähigkeit (freies SQL) besitzt, im Fall einer erfolgreichen Prompt-Injection deutlich mehr
Schaden anrichten könnte als einer, der nur einen engen, validierten Endpunkt aufrufen kann
("Excessive Agency", siehe `docs/learning/03-prompt-injection-and-llm-security.md`). Die App behält
trotzdem einen echten, per Knopfdruck auslösbaren "Tiefenrecherche"-Feature mit echtem
Anthropic-API-Call (für Übungs-/Vorzeigezwecke) — der aber deutlich günstiger wird, weil ihm vorab alle
bereits gesammelten Daten (aus der Session-Recherche und ggf. hochgeladenen Quartalsberichten) als
Kontext mitgegeben werden, sodass er nicht mehr von Null recherchieren muss.

## 1. Purpose

Reduce the AI research cost of this project to close to zero for routine data collection, while
keeping a real, in-app, API-backed "deep research" feature for its practice/showcase value — by
splitting research into two passes with very different cost profiles, and letting the cheap pass
pre-pay for the expensive one.

This directly follows from a gap noticed while re-examining the existing screening-cost design (see
`2026-07-30-screening-cost-redesign-design.md`): that design assumes the *app itself* always pays for
every research call against the Anthropic API, and spends most of its complexity (three-tier funnel,
search-round ceilings, domain restriction) trying to keep that per-call app-side cost small and
predictable. This document explores a different premise — that routine data collection doesn't have to
be an app-side API cost at all if it happens in a Claude Code session with the user instead.

## 2. Scope

### In scope
- A session-based "collection pass": the user asks Claude (in an ad-hoc Claude Code session, not an
  automated backend job) to research several Gettex-listed companies, gathering a bounded set of
  useful facts per company without deep multi-year analysis or heavy token spend.
- A validated app-side write path (Admin API endpoint) for persisting session-collected findings.
- Keeping the existing in-app, button-triggered deep research feature, re-scoped to consume
  already-collected data as context instead of researching every fact from scratch.
- A brief sketch of the resulting UI needs (category/top-candidate overview, full searchable list,
  last-updated / which-fields-updated indicator, deep-research trigger) — enough to validate the data
  model, not a full UI spec.

### Explicitly out of scope for this document
- Reconciling this idea with the existing automatic Scheduler / Universe Provider / three-tier
  Stage 0-1-2 funnel from `2026-07-30-screening-cost-redesign-design.md` — see "Relationship to
  existing designs" below.
- Exact numeric thresholds (how many companies per session, exact token/effort budget per company) —
  calibrated during implementation, not fixed here.
- The concrete Admin API endpoint contract (request/response shape, exact validation rules) —
  implementation-level detail.
- Automated coverage-rotation logic (which companies get picked each session) — left as a manual,
  conversational decision between the user and Claude for now (Section 4).

### Deliberately deferred, not part of this design iteration
- Whether the old automatic daily Scheduler is dropped entirely, kept as a lower-priority secondary
  path, or merged with this idea — a decision for the reconciliation step, not this document.
- Formal coverage-balancing (recency/sector weighting) for picking which companies to research in a
  session — the existing Knowledge-Base-driven Coverage Map idea from the older design could feed this
  later; not needed for the first version.

## 3. Core idea: two passes, two very different cost profiles

| Pass | Where it runs | Who pays for the AI call | Depth | Purpose |
|---|---|---|---|---|
| **Session Collection** | A Claude Code session between the user and Claude, started manually, a few times a week | The user's own existing Claude Code usage — not a metered call the app itself makes | Bounded: broad facts, not exhaustive, low token spend per company | Grow the knowledge base cheaply and steadily |
| **Deep Research** | The deployed app, triggered by an operator button click | The app's own Anthropic API budget (real cost, but rare/on-demand) | Full: qualitative moat/management write-up, multi-year trend confirmation | Practice/showcase value + genuinely deeper answer, made cheap by reusing Session Collection's output as context |

The two passes are not redundant: Session Collection deliberately avoids the expensive multi-year
trend work (margin trend, FCF trend, multi-year profit stability) that only Deep Research still does —
it collects the facts that are cheap and fast to gather (current-year snapshot, business description,
rough moat read), the same category of facts the older redesign's Stage 0/Stage 1 already identified
as cheap (see that document's Section 6) — but this document does not adopt that document's tier
mechanics, only reuses the same observation about which facts are cheap.

## 4. Session Collection pass

- **Trigger:** manual. The user starts a Claude Code session (like this one) and asks Claude to
  research a handful of Gettex-listed companies — either specific tickers the user names, or "pick a
  few I haven't covered yet." No automated backend job is involved in this pass.
- **What's collected, per company:** company/business description, a rough (not deeply verified) moat
  read, sector, country of headquarters, and a current-year financial snapshot (P/E, P/B, ROE,
  debt/equity, current-year margin, current-year FCF sign, current-year net income vs. prior year,
  insider/founder ownership share where easily available) — bounded by a small number of search steps
  per company, not "search until exhaustive." Exact step/token budget is an implementation-level
  calibration, not fixed here.
- **Cost:** effectively zero marginal cost to the app — the research happens inside the user's own
  Claude Code session, using the user's existing usage, not a call the app's backend makes against its
  own Anthropic API key/budget.
- **Write path — decided in this session:** results are persisted via a **validated Admin API endpoint**
  on the app's backend (the same kind of authenticated, single-user-protected write path the app
  already uses for portfolio positions), not by direct SQL/MCP access to the database. Reasoning
  (Section 8 below): this bounds the blast radius of a prompt-injection failure to "one validated
  record write" instead of "arbitrary SQL," which matters specifically because this pass involves an
  agent (Claude, in the session) reading untrusted web content in the same context where it would
  otherwise have write access. The project's separate MCP learning thread (Postgres MCP server,
  `PROJECT-STATUS.md`) stays unrelated to this write path — it can remain a read-only/dev-tooling
  exercise.
- **Selection of which companies to cover:** conversational, not automated, for the first version — the
  user names tickers or asks Claude to suggest under-covered ones by looking at what's already stored.
  A more systematic rotation (recency/sector balance, as sketched in the older redesign) is a possible
  later refinement, not required to start.
- **Freshness tracking:** each stored record carries an as-of date, and — per the earlier UI
  requirement — which specific fields were captured/refreshed in that pass, so the dashboard can show
  "last updated: <date>, fields updated: <list>" per company rather than a single opaque timestamp.

## 5. Deep Research pass (stays, made cheaper)

- **Trigger:** stays exactly as previously intended — an operator-only button in the deployed app's UI,
  calling the app's own Company Research Agent / Anthropic API integration for real. This is kept
  deliberately, even though it costs real money per call, because it's part of the project's stated
  purpose as an AI/AWS practice and showcase vehicle (`PROJECT-STATUS.md`, "Idee") — a fully
  session-only research path would leave the deployed app with no live AI feature at all to
  demonstrate.
- **Cost reduction mechanism:** before the app calls the Anthropic API, it assembles everything already
  known about the company — the Session Collection record (Section 4) and, where present, uploaded
  quarterly report text (`2026-08-02-quarterly-report-upload-design.md`) — and passes it into the
  research prompt as **given context**, not as something the model needs to independently
  search/re-derive. The model's job narrows to: verify/deepen the already-known facts where warranted,
  fill in what's genuinely missing (chiefly the multi-year trend data Session Collection never
  attempted), and produce the qualitative write-up. This targets the same cost driver already
  identified elsewhere in this project's research (`2026-07-30-screening-cost-redesign-design.md`,
  Section 11 risk 8): web-search cost compounds with the number of search rounds a call needs, so a
  call that starts already knowing most of the cheap facts needs fewer rounds than one starting from
  nothing.
- **Relationship to quarterly report upload:** the previously-approved, currently-deferred quarterly
  report upload idea gains a second justification here. It was originally scoped purely as a quality/
  convenience improvement (better context for the qualitative read); it's now also a **cost lever** for
  the same reason — uploaded PDF text is free context that would otherwise cost search rounds to
  approximate.

## 6. Data model (sketch)

- **CompanySnapshot** — the record a Session Collection pass writes/updates: ticker, ISIN, company
  name, sector, country, business description, rough moat note, current-year key stats (P/E, P/B, ROE,
  debt/equity, margin, FCF sign, net income vs. prior year, insider ownership share where available),
  per-field as-of date, source reference per field where obtained via search.
- **DeepResearchResult** — the record a Deep Research pass produces/extends: full qualitative write-up
  (moat, management quality/capital allocation), multi-year trend confirmations (margin trend, FCF
  trend, multi-year profit stability), any value-trap/low-confidence caveats, per-field as-of date and
  source reference, linked to the `CompanySnapshot` it was seeded from.

This mirrors, in spirit, the older redesign's `ResearchRecord` (per-criterion tier-of-origin and
as-of-date) — that's not a coincidence, it's the same underlying good idea (never hide *how* a fact was
established), arrived at independently here. Formal reconciliation of the two data models is left to
the reconciliation step (Section 7).

## 7. Relationship to existing designs

This document is intentionally written **independent of** the existing
`2026-07-30-screening-cost-redesign-design.md` (automatic Scheduler, Universe Provider, three-tier
Stage 0/1/2 funnel), at the user's explicit request, so the idea can be evaluated on its own terms
first. It clearly overlaps with that document — most notably, it questions whether an automated daily
Scheduler running Stage 2 is still needed at all if routine collection happens in Claude Code sessions
instead. **Reconciling the two is a deliberately separate, future step**, not resolved here. Until
that reconciliation happens, treat this document as a candidate replacement for that design's
Scheduler/Universe-Provider/funnel machinery, not as an addition sitting alongside it unmodified.

## 8. Security: why the write path is an API, not direct SQL

Established elsewhere in this project (`docs/learning/03-prompt-injection-and-llm-security.md`,
Guardrail E): any LLM step that reads content it didn't write itself — here, Session Collection's web
search — is a potential indirect prompt-injection surface. That document's own defense (a "treat
retrieved content as data, not instructions" framing) is explicitly documented as a *prompt-level*
mitigation, not a guaranteed one ("genuinely easy to miss," "primary defense" rather than "complete
defense").

Given that, the design question is: what's the worst case if the framing defense fails during a
Session Collection pass? This is the OWASP LLM Top 10 "Excessive Agency" category — the risk isn't
just that the model might be fooled, it's what it's *able to do* if fooled.
- **Direct SQL/MCP write access** would mean a successful injection could run arbitrary SQL — read or
  corrupt unrelated tables (portfolio positions, other research records), limited only by the
  database user's grants.
- **A validated Admin API endpoint** bounds the same failure to "one write of one validated record
  through one narrow, typed contract" — no arbitrary SQL, no reach into unrelated tables.

Decision: the Admin API endpoint is the write path for Session Collection results. The project's
existing, separate MCP-learning Postgres server stays unrelated to this — it can remain a read-only or
dev-tooling exercise without being repurposed into a production write path.

## Open questions / deferred to implementation

- Exact Admin API contract (endpoint shape, request/response fields, validation rules).
- Exact Session Collection effort/token budget per company.
- Whether/when to build automated coverage-rotation for Session Collection candidate selection.
- Full reconciliation with `2026-07-30-screening-cost-redesign-design.md`'s Scheduler/Universe
  Provider/three-tier funnel (Section 7 above).
- UI details beyond the sketch in Section 6 (category groupings, exact dashboard layout).

## Decision log

- 2026-08-03: Core idea proposed by the user — move routine data collection out of the app's own
  metered API usage entirely, into ad-hoc Claude Code sessions, storing results in a DB the app reads
  from. Adopted because it removes essentially all of the app-side per-call cost risk that the older
  redesign's three-tier funnel exists to manage, for the collection pass specifically.
- 2026-08-03: Deep Research ("Tiefenrecherche") button kept, not replaced by session-based research
  entirely — reasoning: the deployed app needs at least one real, live AI feature to serve its stated
  practice/showcase purpose; a purely session-based research model would leave it with none.
- 2026-08-03: Deep Research made cheaper by feeding it all already-known data (Session Collection
  output + uploaded quarterly reports) as prompt context, reducing how many facts it must
  independently search for — reuses the already-documented "search rounds are the dominant cost
  driver" finding from the older redesign rather than re-deriving it.
- 2026-08-03: Write path for Session Collection results decided as a validated Admin API endpoint,
  not direct SQL/MCP database access — reasoning: bounds the blast radius of a prompt-injection
  failure (Excessive Agency, OWASP LLM Top 10) to a narrow, validated write instead of arbitrary SQL.
  The project's separate MCP-learning Postgres server remains unrelated to this write path.
- 2026-08-03: This document deliberately scoped as independent from
  `2026-07-30-screening-cost-redesign-design.md`, at the user's request — reconciliation of the two
  (in particular, the fate of that document's automatic Scheduler) is explicitly deferred, not decided
  here.
