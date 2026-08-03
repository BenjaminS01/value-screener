# Quarterly Report Upload for Deep Research — Design

**Status: brainstormed and approved (2026-08-02). Explicitly queued — not to be implemented yet.**
See "Sequencing" at the bottom for why, and what must happen first.

## Purpose

Let the app's operator(s) upload a company's quarterly report (PDF) for a Gettex candidate already
tracked by the screener, then trigger an optional "deep research with document" action for that
candidate. If a document exists for a candidate, the existing automatic Stage 0–2 research funnel
(see `2026-07-30-screening-cost-redesign-design.md`) can also use it as additional context, the same
way Stage 1's numeric snapshot is already passed into the Stage 2 prompt today.

This does not replace the funnel's existing web-search-based research — it's an optional, higher-
quality input source for candidates where a primary filing has actually been uploaded.

## Trust model

Upload is restricted to the app's own operator(s) — in practice the user themselves and possibly one
trusted developer, not the general public. This is a deliberately small, known group who understand
what they're uploading. Because of this, no content-moderation, source-verification, or anti-abuse
guardrail is needed for v1 (unlike, say, an open crowd-sourced upload model, which was considered and
explicitly rejected during brainstorming — see Decision log below).

## Output policy

Unchanged from the existing product philosophy: the resulting evaluation shown to (public) users is
always public, regardless of which internal mechanism produced it — automatic web-search research or
an operator-uploaded-document deep dive. The document itself, and which mechanism was used, is an
internal implementation detail; it does not need to be surfaced, consistent with the existing
"descriptive, not internal-tier-exposing" wording policy already used for the public rejected-
candidates list.

## Architecture decision: full-text context, no vector database (v1)

**Approach chosen:** extract the PDF's full text (e.g. via Apache PDFBox) and pass it directly as
prompt context into the existing Company Research Agent — the same pattern already used for the
Stage 1 → Stage 2 snapshot handoff. No chunking, no embeddings, no vector database.

**Why:** a single quarterly report is typically ~15,000–30,000 tokens of extracted text, comfortably
within Claude's context window. A vector-database/RAG pipeline (chunking, embeddings, retrieval) only
pays for itself once the corpus is too large to fit in context directly — that is not the situation
here: uploads are operator-only and the volume per candidate is expected to be low and experimental
(the operator explicitly said they'd want to try uploading varying numbers of documents, not that they
plan a large multi-year archive from the start).

**Safeguard:** a hard per-candidate token budget cap on total uploaded-document text (e.g. ~100k
tokens) is enforced at upload time. Exceeding it triggers a rejection/warning at upload time, rather
than silently blowing prompt cost or exceeding the model's context window.

**Deliberately not built now (future upgrade path, not a redesign):** if usage ever grows past what
full-text stuffing can handle economically, a vector store (Spring AI's `VectorStore` abstraction,
backed by pgvector on the Postgres instance `backend/` already runs — no new database technology
needed) is the natural next step. This is an isolated future addition, not something v1 needs to
design around.

**Rejected alternative (Approach B in brainstorming):** building the vector-database pipeline from day
one. Rejected as premature complexity for a low, experimental, trusted-operator-only upload volume —
would add chunking strategy, an embedding pipeline, and retrieval-quality tuning for a scaling problem
that does not currently exist.

## Open / explicitly deferred (to be resolved when this work resumes)

- Exact interface: whether the uploaded document becomes a new optional parameter on the existing
  `research_company` MCP tool, or a new dedicated tool. Not decided — deliberately left open since it
  depends on how the Company Research Agent's prompt contract looks once reconciliation step 4
  (below) has actually been validated against a real API call.
- Upload endpoint and file storage: `backend/` currently has no file/blob storage of any kind. This
  needs its own small design pass (storage backend, size limits, PDF validation) when work resumes.
- PDF text extraction dependency (e.g. Apache PDFBox) is not yet added to any module.
- Exact prompt wording for how the extracted document text is framed for the model (e.g. distinguishing
  "this is the primary filing, prefer it over general knowledge" from Stage 1's numeric snapshot
  framing).

None of the above exists in code yet. This document is a design record for later, not an
implementation plan.

## Sequencing — why this is queued

This feature layers on top of the Company Research Agent's Stage 2 mechanism
(`research_company`), whose core promise — a real, successful, live end-to-end research call against
the Anthropic API — has **never yet been confirmed to work** (see
`2026-07-24-company-research-agent-design.md` Decision log and `PROJECT-STATUS.md`). The
screening-cost-redesign reconciliation is currently at step 4 (first live call, ~$0.05–0.10/call
checkpoint) of its 5-step sequence (see `2026-07-30-screening-cost-redesign-design.md`).

Building document-upload support against a research contract that hasn't been proven live yet would
risk having to rework it once step 4 surfaces real issues. **Do not start implementation on this
feature until reconciliation step 4 succeeds** (and ideally step 5's broader implementation plan is at
least scoped). Nothing in this document requires touching any file currently involved in the live-call
testing work — it's purely a forward-looking record.

## Decision log

- 2026-08-02: Idea raised — allow quarterly report uploads to simplify/ground deep research, backed
  by a vector database, feeding both an optional per-candidate deep-dive and the automatic funnel.
- 2026-08-02: Scope clarified — candidates are the existing Gettex universe; upload happens per
  candidate; the operator triggers an explicit "deep research with document" action; the automatic
  funnel may also use uploaded documents for a candidate when present.
- 2026-08-02: Trust model clarified — "logged-in user" means the operator(s) themselves, a very small
  trusted group, not the general public. No moderation/anti-abuse guardrail needed for v1 as a result.
- 2026-08-02: Output policy clarified — the public evaluation is always public regardless of the
  internal research mechanism that produced it (documents vs. web search).
- 2026-08-02: Three architecture options weighed (full-text context / vector-DB-from-day-one / staged
  full-text-now-with-a-cap). Staged approach (Approach C) chosen: full-text context now, explicit
  token-budget cap, vector-DB upgrade path left open but not built, since the described usage pattern
  (operator-only, experimental, low document count) doesn't justify RAG infrastructure yet.
- 2026-08-02: Explicitly queued behind screening-cost-redesign reconciliation step 4 (first live
  Company Research Agent call) — this feature depends on that same agent's prompt contract, which is
  still unvalidated against a real API call.

---

## Deutsche Zusammenfassung

**Idee:** Operator (nur der Nutzer selbst, evtl. ein weiterer vertrauter Entwickler — keine offene
Nutzergruppe) kann zu einem bestehenden Gettex-Kandidaten Quartalsberichte (PDF) hochladen und darüber
eine optionale "Tiefenrecherche mit Quartalsbericht" auslösen. Die automatische Stage-0–2-Recherche
kann vorhandene Dokumente ebenfalls als Zusatzkontext nutzen. Die öffentliche Auswertung bleibt in
jedem Fall öffentlich, unabhängig vom internen Recherche-Mechanismus (Websuche vs. Dokument).

**Architekturentscheidung:** Kein Vector-Store/RAG in Version 1. PDF-Volltext (z. B. via Apache
PDFBox) wird direkt als Prompt-Kontext an den bestehenden Company Research Agent gegeben (gleiches
Muster wie die bereits bestehende Stage-1→Stage-2-Kontextübergabe) — ein einzelner Quartalsbericht
passt locker in Claudes Kontextfenster. Eine harte Token-Budget-Grenze pro Kandidat schützt vor
unerwartet hohen Kosten. Eine Vector-DB (pgvector auf der bereits vorhandenen Postgres-Instanz von
`backend/`) bleibt ein bewusst offen gelassener, aber nicht gebauter Ausbauschritt für den Fall, dass
die Upload-Menge später tatsächlich wächst — aktuell nicht gerechtfertigt, da Uploads auf eine kleine,
vertraute Betreibergruppe beschränkt sind und die erwartete Menge laut Nutzeraussage eher experimentell
als groß ist.

**Status:** Bewusst zurückgestellt. Diese Funktion baut auf dem Company Research Agent auf, dessen
echter Live-Call (Schritt 4 der laufenden Redesign-Reconciliation) noch nie erfolgreich bestätigt
wurde. Erst nach erfolgreichem Schritt 4 weiterverfolgen — siehe Abschnitt "Sequencing" oben.
