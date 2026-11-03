# Quarterly Reports: Upload, RAG Chat and One-Click Evaluation — Design

**Status: design agreed with the user (2026-11-03). Not implemented yet — no code exists for any part
of this document.** Supersedes
[`2026-08-02-quarterly-report-upload-design.md`](2026-08-02-quarterly-report-upload-design.md) (see
Decision log for what changed and why). German summary at the end.

## 1. Purpose and scope

The app gets a private, per-stock document area for quarterly reports (PDF). On top of it:

- **Upload and delete** quarterly reports for a stock at any time (e.g. several Alphabet/GOOG 10-Qs).
- **Chat** about a stock at any time. Answers are grounded in that stock's uploaded reports (RAG) and
  cite the report and page they came from. The user writes in German; reports are typically English.
- **One-click evaluation:** a button in the chat produces a general evaluation from *all* reports
  currently on file for the selected stock, structured along the existing 18 `ResearchCriterion`
  keys — no need to phrase questions.

### Visibility

| Artifact | Visible to |
|---|---|
| Uploaded documents (PDF, extracted text, chunks) | Nobody publicly; logged-in users only via the app |
| Chat | Logged-in users |
| Evaluation results | Logged-in users |

"Logged-in user" today means the single admin credential of `backend/`'s `SecurityConfig`; Cognito
(AWS Topic 8) can widen this later without changing this design. Nothing from this feature is written
into `CompanySnapshot`/`ResearchFinding`, because `GET /api/research/snapshots/**` is planned to become
public (Portfolio Research UI design, `PROJECT-STATUS.md`).

### Out of scope (for now)

- Automatic triggering of evaluations (e.g. on upload) — evaluation is always an explicit button click.
- Cross-stock search ("which of my companies mention tariff risk?") — the index supports it, the UI
  and API don't yet.
- Feeding report data into `company-research-agent/` — that module is frozen (user decision
  2026-11-03) and stays untouched.
- Streaming chat responses, Step Functions orchestration, Cognito — listed under "later" in Section 12.

## 2. Key decisions at a glance

| Topic | Decision |
|---|---|
| Chat retrieval | **RAG** (embeddings + vector search) — chat is used "any time", so prompt caching rarely helps, and RAG keeps per-question cost flat as reports accumulate. Also an explicit learning/showcase goal. |
| Evaluation input | **Full report text, not RAG** — criteria like margin trend or value-trap assessment need figures from several sections at once; top-k chunks would miss them. |
| Vector store | **Amazon S3 Vectors** (eu-central-1) — serverless, reachable outside the VPC, no idle cost. Creating a vector bucket was confirmed to work on this Free Plan account. |
| Embeddings | **Amazon Bedrock, Titan Text Embeddings V2** (`amazon.titan-embed-text-v2:0`), 1024 dims, normalized. Model access confirmed in eu-central-1. |
| Cross-language retrieval | **Mandatory query rewrite:** German question (+ chat history) → English search query by a small, cheap Claude model before embedding. AWS documents cross-language queries on Titan V2 as "sub-optimal". |
| LLM access | **Anthropic API via the official `anthropic-java` SDK** (not LangChain4j): needed for `document` / `search_result` blocks with citations. Billed by Anthropic, not against AWS credits. |
| Models | Configurable per use (query rewrite, chat answer, evaluation). Defaults decided after a first real test; candidates are Claude Haiku 5.5 and Claude Sonnet 5 (no web search needed here, so Haiku is viable again). |
| New code | New module **`report-intelligence/`** (Quarkus, `quarkus-amazon-lambda`), one build deployed as four Lambda functions. |
| Backend role | `backend/` (Spring, ECS) stays the **only public API** and keeps its existing login. |

## 3. Constraints this design is built around

1. **No NAT gateway.** `backend/` runs in private subnets of `value-screener-vpc` without internet
   egress (deliberate, cost). It can reach neither `api.anthropic.com` nor Bedrock nor S3 Vectors.
   → All AI/embedding work runs in Lambdas **outside** the VPC.
2. **Only gateway endpoints are free.** Interface endpoints cost ~7.30 $/month per AZ each. The
   backend therefore talks to the rest of the system only through **S3** (gateway endpoint exists) and
   **DynamoDB** (gateway endpoint to be added, free).
3. **API Gateway HTTP API integration timeout is 30 s.** Chat and evaluation calls can exceed it →
   asynchronous job pattern (submit → job id → poll).
4. **AWS Free Plan** (account created 2026-09-11): ends 6 months after creation (~2027-03-11) or when
   credits run out; **118.40 $ credit left on 2026-11-03**. Only Always-Free allowances apply.
   GuardDuty is reported as not available on the Free Plan. Running ECS/RDS/interface endpoints were
   being stopped on 2026-11-03 to preserve credit — the serverless parts of this design cost ~0 when
   idle and can stay deployed.
5. **Budget:** ~20 €/month overall once on a Paid Plan (AWS Paid Plan supports a monthly spend limit
   from 20 $).

## 4. Architecture

```
Browser ─► API Gateway ─► backend/ (Spring Boot, ECS, private VPC, existing login)
                            │  RDS: report metadata, evaluations
                            │  S3 gateway endpoint          │  DynamoDB gateway endpoint (new, free)
                            ▼                               ▼
     S3 bucket (reports, private)                 DynamoDB table "report-jobs"
       incoming/ → documents/ + pages/            jobs (EVALUATION, CHAT), chat history, status; TTL
        │ S3 events                                        │ DynamoDB Stream
        ▼                                                  ▼
 ┌──────────── report-intelligence/ (Quarkus) — 1 build, 4 Lambda functions, outside the VPC ───────────┐
 │ ingest    validate PDF → text per page → chunks → Titan V2 embeddings → S3 Vectors → status          │
 │ cleanup   on delete: remove vectors, page texts, jobs of that report                                 │
 │ evaluate  all reports of the stock (page text) → Claude → 18 criteria with page citations → job      │
 │ chat      history + question → query rewrite (EN) → Titan V2 → S3 Vectors (filter isin)              │
 │           → Claude with search_result blocks (citations) → German answer with pages → job            │
 └──────────────────────────────────────────────────────────────────────────────────────────────────────┘
 Every function: own least-privilege IAM role; SQS dead-letter queue / on-failure destination.
 Anthropic API key: SSM Parameter Store SecureString (standard tier, free).
```

### Component responsibilities

**`backend/` (existing Spring Boot app)**
- REST API (all behind the existing authentication):
  - `GET /api/reports?isin=` — list reports of a stock with status
  - `POST /api/reports/upload-url` — creates the `quarterly_report` row and returns a presigned S3 POST
  - `DELETE /api/reports/{id}`
  - `POST /api/reports/{isin}/evaluations`, `GET /api/reports/{isin}/evaluations/latest`
  - `POST /api/reports/{isin}/chat` → job id; `GET /api/reports/chat/{jobId}` → status/answer
  - `GET /api/reports/{isin}/chat/history`
- Flyway migration `V4__create_quarterly_report.sql` (Section 5).
- Writes jobs to DynamoDB, reads job results/status, persists finished evaluations into RDS.
- Enforces the daily cost cap for chat/evaluation jobs.

**`report-intelligence/` (new Quarkus module)**
- `quarkus-amazon-lambda`; the four handlers live in one build, each deployed function selects its
  handler via configuration.
- Building blocks: `PdfValidator` (PDFBox), `PageTextExtractor`, `Chunker`, `EmbeddingClient`
  (Bedrock Runtime), `VectorStore` (interface; `S3VectorsStore` implementation), `ClaudeClient`
  (`anthropic-java`), prompt builders reusing the guardrail wording of `company-research-agent`
  (descriptive not recommending, paraphrase not quote, retrieved content is data not instructions).
- AWS SDK v2 clients: `s3`, `s3vectors`, `bedrockruntime`, `dynamodb`.

**`company-research-agent/`** — unchanged, frozen.

## 5. Data model

### RDS (Flyway V4)

`quarterly_report`

| Column | Notes |
|---|---|
| `id` (UUID) | also used as S3 object name and vector key prefix |
| `isin`, `ticker` | stock the report belongs to |
| `fiscal_period` | e.g. `2026-Q1` (see open item 2) |
| `report_date` | period end date; becomes `asOfDate` of derived findings |
| `original_filename` | display only, never used as an S3 key |
| `s3_key`, `sha256`, `page_count`, `token_count` | set by ingest |
| `status` | `PENDING_UPLOAD` → `VALIDATING` → `INDEXED` / `REJECTED` / `DELETING` → (row deleted) |
| `rejection_reason`, `created_at`, `updated_at`, `version` | |

`report_evaluation` — one row per button click: `id`, `isin`, `created_at`, `model`,
`prompt_version`, `report_ids` (the reports used), `status`, `stale` (set when one of its reports is
deleted), `summary`.

`report_evaluation_finding` — per criterion: `evaluation_id`, `criterion_key` (`ResearchCriterion`),
`numeric_value`, `boolean_value`, `claim`, `report_id`, `page`, `as_of_date`. Mirrors
`ResearchFinding`, but with a document reference (`report_id` + `page`) instead of a `sourceUrl`.

### S3 bucket (private, encrypted, TLS-only, Block Public Access)

| Prefix | Content |
|---|---|
| `incoming/{id}.pdf` | quarantine — written only via presigned POST, deleted after validation |
| `documents/{isin}/{id}.pdf` | validated original |
| `pages/{id}.json` | extracted text per page (input for evaluation) |

### S3 Vectors

- Vector bucket in eu-central-1, one index `report-chunks`: **1024 dimensions, cosine distance**.
- Index settings are fixed at creation → the index is created by script (not by hand) once this
  design is implemented.
- Filterable metadata: `isin`, `reportId`, `period`, `page`. Non-filterable metadata: `text` (the
  chunk text; fits the 40 KB per-vector metadata limit).
- Vector key: `{reportId}#{chunkNo}` — deterministic, so deletion needs no lookup.

### DynamoDB `report-jobs`

- Items: `JOB#{id}` (type `EVALUATION`/`CHAT`, status, input, result, error) and
  `CHAT#{isin}` history entries; TTL (e.g. 30 days) on chat items and finished jobs.
- **Provisioned capacity with small values** (stays within the Always-Free allowance; on-demand is
  reportedly not covered by it). Stream enabled (`NEW_IMAGE`) → `evaluate`/`chat` Lambdas.

## 6. Flows

**Upload**
1. UI → `POST /api/reports/upload-url` (isin, period, filename). Backend creates the row
   (`PENDING_UPLOAD`) and returns a presigned POST for `incoming/{id}.pdf` with conditions:
   `content-length-range` ≤ 20 MB, `Content-Type: application/pdf`, 5 min expiry.
2. Browser uploads directly to S3 (bucket CORS allows only the app's own origin).
3. S3 event → `ingest`: validate (Section 9). On failure: delete object, status `REJECTED` + reason.
4. On success: move to `documents/`, write `pages/{id}.json`, chunk, embed (Titan V2), `PutVectors`
   (≤ 500 per call), status `INDEXED` with page/token counts.
5. Status reaches RDS via a status item in DynamoDB that the backend picks up when listing.

**Delete**
1. UI → `DELETE /api/reports/{id}`. Backend sets `DELETING`, deletes `documents/...pdf`.
2. S3 `ObjectRemoved` event → `cleanup`: delete vectors `{id}#0..n` (≤ 500 per call), `pages/{id}.json`,
   related jobs.
3. Backend removes the row once cleanup reports done; evaluations that used this report get
   `stale = true`.

**Evaluation (button in chat)**
1. UI → `POST /api/reports/{isin}/evaluations`. Backend checks the daily cap, writes an `EVALUATION`
   job (with the ids of all `INDEXED` reports of the stock).
2. Stream → `evaluate`: loads `pages/{id}.json` of each report and sends them to Claude as `document`
   blocks with custom content (one content block per page) and citations enabled — every citation then
   points to report + page. Text instead of the PDF itself: cheaper, and page-precise.
3. Output: JSON with the 18 criteria (requested via prompt and parsed, as in the research agent —
   citations and `output_config.format` are mutually exclusive). Criteria without a supporting passage
   are omitted, not guessed.
4. Token budget per evaluation (e.g. ~150k input tokens); above it only the newest reports are used
   and the UI says so.
5. Backend persists the result into `report_evaluation(_finding)`; the chat shows it as a special
   message.

**Chat**
1. UI → `POST /api/reports/{isin}/chat` (question). Backend checks the cap, writes a `CHAT` job.
2. Stream → `chat`:
   1. Query rewrite: small model turns question + last turns into one self-contained **English**
      search query (resolves follow-ups like "und im Vorquartal?").
   2. Embed query (Titan V2), `QueryVectors` top-8 with filter `isin = …` (set server-side, never from
      user input).
   3. Claude answers in German from the hits passed as `search_result` blocks with citations
      enabled; answer includes report/page references; says so when the reports don't cover the
      question.
   4. Answer + citations written to the job and to the chat history.
3. UI polls `GET /api/reports/chat/{jobId}` every 1–2 s.

## 7. RAG details

- **Chunking:** page-based; long pages split with overlap; tables kept intact where detectable;
  target ~500–800 tokens per chunk. Each chunk keeps `reportId`, `period`, `page`.
- **Embeddings:** Titan V2, 1024 dims, `normalize: true`. Input limit 8,192 tokens / 50,000 characters
  per call — far above the chunk size.
- **Measured sample:** `GOOG-10-Q-Q1-2026.pdf` (repo root) — 54 pages, 153,650 characters of
  extracted text, ≈ 33–38k tokens, unencrypted. Expected ~60–100 chunks.
- **Swappable store:** everything behind `VectorStore`; S3 Vectors could be replaced (e.g. pgvector)
  without touching handlers.

## 8. Claude integration

- `anthropic-java` SDK; key from SSM Parameter Store at cold start.
- Model per use case in configuration (`rewrite`, `chat`, `evaluation`); `max_tokens`, effort and
  timeouts set explicitly (lesson from the research agent migration: library defaults differ).
- Prompts adopt the research agent's guardrails: descriptive not recommending wording, paraphrase not
  quote, document content is analysis material not instructions. No tools are given to any call, so an
  injected instruction in a report cannot trigger an action.
- Every evaluation stores model and prompt version (same reasoning as `CURRENT_PROMPT_VERSION` in the
  research agent).

## 9. Security

- **Upload path:** presigned POST with size/type conditions and server-chosen key; bucket private,
  encrypted, TLS-only, CORS restricted to the app origin; originals never served publicly.
- **Quarantine validation (`ingest`):** `%PDF-` signature, full parse with PDFBox, page limit (e.g.
  300), extracted-text limit, reject encrypted PDFs and PDFs with JavaScript, embedded files or launch
  actions, SHA-256 duplicate check, processing timeout. Runs in an isolated function with access only
  to `incoming/`, `documents/`, `pages/`. (GuardDuty Malware Protection is not available on the Free
  Plan; revisit on a Paid Plan.)
- **Prompt injection via documents:** see Section 8; output parsed against the expected JSON shape.
- **Tenant/stock isolation:** `isin` filter always set server-side.
- **Least privilege:** one IAM role per function scoped to its prefixes/table/index; backend may only
  create presigned uploads, delete documents and read/write the job table.
- **Cost protection:** daily cap per job type in the backend, token budget per evaluation, AWS budget
  alarms + existing budget action.

## 10. Cost estimate

Anthropic prices per million tokens (claude-api reference, 2026-10): Sonnet 5 2 $ in / 10 $ out;
Haiku 5.5 0.10 $ / 0.50 $ (≤ 100k prompt tokens). AWS prices for S3 Vectors and Titan V2 are
third-party figures — confirm on the AWS pricing page for eu-central-1 before relying on them.

| Operation | Estimate |
|---|---|
| Index one report (Titan V2 ≈ 0.02 $/M tokens, S3 Vectors writes) | < 0.1 cent |
| Chat question (rewrite + ~8k context answer) | ~2–3 cent with Sonnet, < 1 cent with Haiku |
| Evaluation over one report (~40k tokens) | ~0.12 $ with Sonnet, ~1 cent with Haiku |
| Idle AWS cost of the serverless parts | ~0 $ (Lambda, DynamoDB, SQS within Always Free; S3/S3 Vectors cents) |

For comparison (why RAG for chat): without RAG a "cold" chat question over 4 reports (~160k tokens)
would cost ~0.33 $ with Sonnet, and grows with every added report.

## 11. AWS Free Plan eligibility (checked 2026-11-03)

| Service | Status |
|---|---|
| Lambda, SQS, DynamoDB (+ Streams read by Lambda), Step Functions, CloudWatch | Always Free allowances |
| S3 (Standard), API Gateway | eligible, small amounts from credit |
| **S3 Vectors** | **eligible — vector bucket creation confirmed by the user** |
| **Bedrock Titan Text Embeddings V2** | **eligible — model access confirmed by the user** |
| GuardDuty | reportedly Paid Plan only → not used |
| Secrets Manager | eligible but 0.40 $/secret/month → SSM Parameter Store instead |

## 12. Implementation phases

| Phase | Content | AWS learning topics |
|---|---|---|
| 0 | `report-intelligence/` skeleton (Quarkus, handler structure), `PdfValidator`, `PageTextExtractor`, `Chunker`; unit tests with the GOOG sample, fully local | Quarkus, Lambda handler model |
| 1 | Backend: V4 tables, upload URL, list, delete. AWS: bucket + CORS, S3 events, `ingest` (validation only), DLQ | S3 presigned POST, events, IAM, SQS |
| 2 | Indexing + `cleanup`: Titan V2, S3 Vectors index (scripted), delete end-to-end | Bedrock, S3 Vectors |
| 3 | Evaluation button: DynamoDB table + gateway endpoint + stream, `evaluate`, frontend view | DynamoDB Streams, event source mappings |
| 4 | Chat: rewrite, retrieval, cited answer, history, polling UI | RAG |
| Later | Streaming answers, Cognito, Step Functions for ingest, eval set for answer quality, cross-stock search | |

Following the project's AWS convention, AWS resources are created by the user (console/CLI) after the
concept is explained; git operations stay with the user. Local development against real AWS services
uses temporary credentials from the existing `Admin` role (`sts assume-role` with MFA) — the IAM user
deliberately has no access key.

## 13. Open items

1. **Backend hosting after the Free Plan:** upload UI and chat need `backend/`. Locally it runs with
   Docker Postgres; a public demo needs ECS/RDS again (and a Paid Plan decision before ~2027-03-11).
2. **Fiscal period:** entered by the user at upload, or extracted by `ingest` and confirmed by the user.
3. **Model defaults** for rewrite/chat/evaluation — decide after a first real test (not run yet, by
   user decision).
4. **Exact chunk size/overlap and top-k** — tune with a few real questions against the GOOG sample.
5. **Quarkus multi-handler packaging** (one build, handler chosen per function) — verify during
   Phase 0.

## Decision log

- 2026-11-03: Feature re-planned from the queued 2026-08-02 spec. User requirements: upload/delete
  reports per stock at any time, chat about a stock any time, one-click general evaluation mapped to
  the existing data model, AWS for learning/showcase, hardened upload.
- 2026-11-03: Documents not public; evaluation visible to logged-in users. Consequence: separate
  tables instead of `CompanySnapshot`/`ResearchFinding` (whose read endpoint is planned to go public).
- 2026-11-03: RAG adopted for chat (reverses the 2026-08-02 "no vector DB in v1" decision). Reason:
  "chat any time" means prompt caching rarely hits; RAG keeps cost per question flat as reports
  accumulate; explicit learning/showcase goal. Evaluation deliberately stays full-context.
- 2026-11-03: Hybrid confirmed by the user: chat via RAG, evaluation via button over all current
  reports of the selected stock.
- 2026-11-03: Not dependent on the research agent's unproven live call anymore (the 2026-08-02
  sequencing blocker): this feature uses no web search and its own prompts.
- 2026-11-03: AI work in Lambdas outside the VPC because the backend has no internet egress (no NAT);
  backend ↔ Lambda communication only via S3 and DynamoDB (free gateway endpoints).
- 2026-11-03: Free Plan eligibility researched; Supabase (vector buckets / pgvector) considered after
  the user found it and rejected: Free projects pause after ~1 week of inactivity, leaves AWS
  (learning goal), private document text at a further provider. User confirmed S3 vector bucket
  creation and Titan V2 access on the Free Plan account → S3 Vectors + Titan V2.
- 2026-11-03: Titan V2 docs call cross-language queries "sub-optimal" → query rewrite into English
  made mandatory.
- 2026-11-03: `anthropic-java` SDK chosen over LangChain4j for citations (`document`/`search_result`).
- 2026-11-03: Quarkus chosen for the new module (learning/showcase); the research agent's Quarkus
  migration was reverted by the user and that module is frozen.
- 2026-11-03: Credit at 118.40 $; user stops ECS/RDS/interface endpoints to preserve it. No live API
  test for now.

## Sources (checked 2026-11-03)

- [AWS Docs: S3 Vectors — limitations and restrictions](https://docs.aws.amazon.com/AmazonS3/latest/userguide/s3-vectors-limitations.html)
- [AWS Docs: S3 Vectors — regions, endpoints, quotas](https://docs.aws.amazon.com/AmazonS3/latest/userguide/s3-vectors-regions-quotas.html)
- [AWS Docs: Amazon Titan Text Embeddings models](https://docs.aws.amazon.com/bedrock/latest/userguide/titan-embedding-models.html)
- [AWS Docs: Explore AWS services with AWS Free Tier](https://docs.aws.amazon.com/awsaccountbilling/latest/aboutv2/free-tier.html)
- [AWS Free Tier FAQs](https://aws.amazon.com/free/free-tier-faqs/)
- [AWS Docs: DynamoDB Streams usage and cost](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/CostOptimization_StreamsUsage.html)
- [AWS Builder: Free Tier $200 credits (ineligible services incl. GuardDuty)](https://builder.aws.com/content/2zmBcwokU8Y0C1zacGmXsRDpXP6/aws-free-tier-unlock-dollar200-in-free-credits-for-new-users)
- [Supabase FAQ: free project pausing](https://supabase.com/kb/faqs/platform/free-project-pausing)

---

## Deutsche Zusammenfassung

**Ziel:** Ein privater Dokumentenbereich pro Aktie für Quartalsberichte (PDF). Berichte lassen sich
jederzeit hochladen und löschen. Dazu ein Chat pro Aktie, der jederzeit Fragen auf Basis der
hochgeladenen Berichte beantwortet (RAG, mit Bericht- und Seitenangabe), und ein Button „Auswertung“,
der aus allen aktuell vorliegenden Berichten der Aktie eine allgemeine Auswertung nach den
bestehenden 18 `ResearchCriterion`-Kriterien erzeugt. Dokumente sind nicht öffentlich; Chat und
Auswertung sehen nur eingeloggte Nutzer. Deshalb eigene Tabellen statt `CompanySnapshot`.

**Architektur:** Das Backend (Spring, ECS, privates VPC ohne NAT) bleibt die einzige API mit dem
bestehenden Login und spricht nur über S3 und DynamoDB (kostenlose Gateway-Endpoints) mit dem Rest.
Die KI-Arbeit erledigt ein neues Quarkus-Modul `report-intelligence/` als vier Lambdas außerhalb des
VPC: `ingest` (PDF prüfen, Text pro Seite, Chunks, Titan-V2-Embeddings, S3 Vectors), `cleanup`
(beim Löschen alles entfernen), `evaluate` (ganzer Berichtstext → Claude → 18 Kriterien mit
Seitenzitaten) und `chat` (deutsche Frage → englische Suchanfrage → Vektorsuche → Claude mit Zitaten).
Auswertung und Chat laufen asynchron über DynamoDB Streams, weil API Gateway nach 30 s abbricht.

**Entscheidungen:** RAG für den Chat (Kosten pro Frage bleiben konstant, Lern- und Vorzeigewert),
Volltext für die Auswertung (Trends brauchen den ganzen Bericht). S3 Vectors und Titan V2 sind im
Free-Plan-Konto nutzbar (vom Nutzer bestätigt); GuardDuty nicht. Supabase wurde geprüft und
verworfen. Claude über das offizielle `anthropic-java`-SDK wegen der Zitate; Modelle konfigurierbar,
Festlegung nach einem ersten Test. Der Research Agent bleibt unverändert.

**Kosten:** Indexieren < 0,1 Cent pro Bericht, Chat-Frage ca. 2–3 Cent (Sonnet) bzw. < 1 Cent (Haiku),
Auswertung pro Bericht ca. 0,12 $ (Sonnet) bzw. ca. 1 Cent (Haiku), AWS im Ruhezustand ca. 0 $.
Restguthaben am 2026-11-03: 118,40 $; ECS, RDS und Interface-Endpoints werden vorerst gestoppt.

**Umsetzung in Phasen:** 0 Modul-Gerüst lokal, 1 Upload/Liste/Löschen mit Prüfung, 2 Indexierung,
3 Auswertungs-Button, 4 Chat; später Streaming, Cognito, Step Functions. Offene Punkte: Backend-Hosting
nach dem Free Plan, Erfassung der Geschäftsperiode, Modellwahl, Chunk-Größe und Top-k, Quarkus-
Paketierung mit mehreren Handlern.
