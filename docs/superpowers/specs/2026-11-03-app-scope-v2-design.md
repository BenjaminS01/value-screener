# Value Screener v2 — Product Scope, Roles and UI — Design

**Status: scope agreed with the user (2026-11-03). Not implemented yet.** This document is the
**top-level source of truth** for what the application is and does. It consolidates and updates the
earlier specs; where they conflict with this document, this document wins (see Section 11). Technical
details of the quarterly-report pipeline stay in
[`2026-11-03-quarterly-report-rag-design.md`](2026-11-03-quarterly-report-rag-design.md), adjusted to
this document. German summary at the end.

> **Update 2026-11-04:** the backend part of phase 1, the local-only research ingest with push to live,
> and stock groups are refined in
> [`2026-11-04-v2-phase1-backend-research-groups-design.md`](2026-11-04-v2-phase1-backend-research-groups-design.md).
> Notably: `POST /api/research/snapshots` is replaced by token endpoints (local ingest, live import);
> the free-text `sector` is replaced by a maintained group list.

## 1. Goal

The application serves two purposes, in this order:

1. **Showcase project** — a portfolio piece for job applications/freelancing (Java, AWS, AI/RAG,
   security-conscious design), and the practical vehicle for the AWS Developer certification.
2. **Personal stock-analysis tool** for the owner: keep the own portfolio, look up researched
   fundamentals, read quarterly reports and ask an AI about them.

Hard constraint, unchanged: low running cost (~20 €/month overall, AWS Free Plan credit until
~2027-03-11). Still a pure analysis tool — no trading, no broker connection, no recommendations.

**What changed compared to the original vision (2026-07-21 / 2026-07-30):** the app is no longer a
publicly readable site with automatic daily screening. It becomes a **login-only** app with two areas,
**Portfolio** and **Research**. Automatic screening, change alerts and the scheduler are dropped from
the scope (Section 10).

## 2. Access model

- **The whole application requires login.** Without login only the following is reachable: the login
  page (with a short description of the project, since visitors evaluating the showcase need to know
  what they are looking at), **Impressum**, **Datenschutzerklärung** and the disclaimer. Impressum
  must stay reachable without login (§ 5 DDG).
- **Accounts** (decided 2026-11-03, revised the same day):
  - **ADMIN** — exactly one account, the owner's; only the owner knows its password.
  - **USER** — **a handful of individual accounts** (e.g. up to ~10), one per person. All have the
    same role and the same rights. The owner creates them, sets username and initial password and
    hands them over personally. No self-registration.
  - Each USER account has its **own daily limits** and its **own chat history**; users don't see each
    other's questions.
- Accepted trade-off: a leaked USER password lets a stranger in, but only with USER rights (read
  only, chat within that account's daily limits). Remedy: the owner changes or removes just that one
  account (Section 8, item 1).
- Every permission check happens in `backend/`. Lambdas never see users or roles; they only process
  jobs the backend has already authorized.

### Permission matrix

| Feature | ADMIN | USER |
|---|---|---|
| Portfolio: list of positions (company, ticker, sector) | ✅ | ✅ |
| Portfolio: quantity, entry price, purchase date | ✅ view and edit | ❌ never sent to the browser |
| Portfolio: add, change, sell, delete positions | ✅ | ❌ |
| Research: select a stock, view web-research data | ✅ | ✅ |
| Research: write web-research data (`POST /api/research/snapshots`) | ✅ | ❌ |
| Quarterly reports: list and read | ✅ | ✅ |
| Quarterly reports: upload, change metadata, delete | ✅ | ❌ — upload button greyed out, tooltip *"Uploading reports is only available to the admin."* |
| AI chat incl. "Evaluate quarterly reports" button | ✅ (daily limit) | ✅ (daily limit) |
| Deep-analysis button (dummy) | ✅ | ✅ (daily limit) |

UI texts stay English (decision 2026-08-20); legal pages stay German.

**Hiding is not protection:** the greyed-out button is only UX. The backend rejects every write call
from a USER with `403`, and the response DTOs for USERs structurally do not contain quantity/price
fields (same principle as today's `PublicPortfolioPositionView` and its regression test).

## 3. UI structure

```
Login page (public): short project description · login form · Impressum · Datenschutz
After login:
  Header: Value Screener | Portfolio | Research | user name + role | Logout
  Footer: disclaimer · Impressum · Datenschutz
```

**Login page for now: functional only** — username field, password field, login button, error
message, links to Impressum/Datenschutz. Its design and any project description for showcase visitors
come **at the end** (last phase). The current `LandingPage` stays in the code until then but is not
reachable from the navigation.

### 3.1 Portfolio

- Table of positions: company name, ticker, sector. Click on a position → jumps to that stock in
  Research (if research data exists; otherwise "Not yet researched").
- **ADMIN additionally:** columns quantity, average entry price, purchase date; actions buy
  (= add/top up), sell (partial/full), correct a position (fix typos or wrong values), delete.
- **USER:** read-only list, no extra columns, no action buttons.

### 3.2 Research

One page, stock-centric:

```
┌ Stock selector: all stocks captured in the system (CompanySnapshot), searchable ─────────────┐
│  badge "Owner holds this position" if the ISIN is in the portfolio (conflict-of-interest note)│
├───────────────────────────────────────────────────────────────────────────────────────────────┤
│ A. Company overview     name, ticker, ISIN, sector, country, business description           │
│ B. Fundamentals         numeric criteria as key-figure tiles (P/E, P/B, 5y avg P/E and P/B,  │
│                         ROE, debt/equity, current ratio, net margin, FCF positive, net       │
│                         income grew, insider ownership) — value, as-of date, source link     │
│ C. Qualitative findings margin trend, FCF trend, profit stability, interest coverage, moat,  │
│                         management quality, value-trap assessment — text, date, source link  │
│ D. Quarterly reports    list: period, file name, upload date, status, page count; open PDF   │
│                         ADMIN: Upload / change period / delete — USER: greyed-out Upload     │
│ E. AI chat              "Summarize quarterly reports" button + free questions about the stock │
│                         remaining daily quota shown, answers with report/page citations       │
│ F. Deep analysis        "Run deep analysis" button → dummy Lambda (Section 6)                 │
└───────────────────────────────────────────────────────────────────────────────────────────────┘
```

- **Which stocks are selectable:** every stock with a `CompanySnapshot` (i.e. researched via the
  `research-company` skill). Quarterly reports can only be uploaded for such a stock, so reports,
  research data and chat always hang off the same ISIN. Grouping B/C follows the existing split of
  `ResearchCriterion` into "Stage-1-style" (numeric) and "Stage-2-style" (qualitative).
- Criteria without a finding are shown as "not researched" instead of being hidden, so gaps are
  visible.
- The existing `ResearchLibraryPage` and `PositionResearch` components become this page; the
  per-position expandable research in the portfolio is replaced by the link to Research.

## 4. Web-research data

- Source stays the **`research-company` Claude Code skill** (manual research inside a Claude Code
  session, flat-fee subscription, no metered API cost) → result checked by the owner → uploaded by
  the owner via `POST /api/research/snapshots` (now **ADMIN only**). Data model unchanged:
  `CompanySnapshot` + `ResearchFinding` per `ResearchCriterion` with claim, value, source URL and
  as-of date (see `2026-08-10-manual-research-persistence-design.md`).
- `GET /api/research/snapshots/**` is **no longer public** — it moves behind login like everything
  else (reverses the 2026-08-20 decision, which assumed a public app).
- Consequence for the RAG design: the reason it kept report results out of `CompanySnapshot`
  ("snapshots endpoint will become public") no longer applies. Separate tables are still kept,
  because report results carry a document reference (report + page) instead of a URL and can go
  stale when a report is deleted.

## 5. Quarterly reports and AI chat

Technical design: [`2026-11-03-quarterly-report-rag-design.md`](2026-11-03-quarterly-report-rag-design.md)
(upload via presigned POST, validation, Titan V2 embeddings, S3 Vectors, Lambdas outside the VPC,
asynchronous jobs via DynamoDB). Changes from this document:

- **Read access for USERs:** list of reports and opening the PDF (short-lived presigned GET URL,
  e.g. 5 min, generated by the backend after the role check). Upload, metadata change and delete
  are ADMIN only.
- **"Change" a report** means: correct metadata (fiscal period, report date) via `PATCH`; replacing
  the file itself is delete + upload (keeps the pipeline simple, no re-index logic).
- **"Evaluate quarterly reports" button** (renamed 2026-11-04 from "summary button"; called
  "evaluation" in the RAG design): it evaluates *all* reports currently on file for the selected
  stock. The output is a short overall picture, then the development across the quarters, then the
  findings mapped onto the `ResearchCriterion` keys with report/page citations. It uses the full
  report text, not RAG (reasoning unchanged).
  - **Stored and reused** (2026-11-04): the evaluation is saved per stock and shown to everyone when
    the stock is opened.
  - If no report was added, deleted or changed since then, a click starts no new LLM call and uses no
    daily quota. It shows "No new quarterly reports — evaluation already available."
  - Only the ADMIN can force a new evaluation, e.g. after a model or prompt improvement.
  - Free chat questions stay possible at any time.
- **Free questions** ("general questions about the stock"): answered from the reports via RAG, with
  citations. If the sources don't cover the question, the answer says so instead of filling the gap
  from the model's training knowledge — keeps the answers checkable, which matters because results
  feed into real investment decisions.
- **Chat per user:** history is stored per account and stock (`CHAT#{account}#{isin}`); every user sees
  only their own history (the backend sets the account from the session, never from the request).
  Users can start a fresh conversation (clears only the context for the next answers, not the stored
  history).

### 5.1 Chat limits (cost protection)

All limits are enforced by the backend **before** a job is created; values are configuration.
Limits count **per user account**; nobody shares a pool. A global cap on top bounds the total cost
regardless of how many accounts exist. Starting values (to be adjusted after the first real test):

| Limit | Start value |
|---|---|
| Chat questions per USER per day | 10 |
| Report evaluations per USER per day (only runs that actually call the LLM) | 2 |
| Deep-analysis dummy clicks per USER per day | 5 |
| Chat questions / report evaluations, ADMIN per day | 50 / 10 |
| Global cap across all accounts per day (all job types) | 100 |

Additional safety layers: max question length (e.g. 1,000 characters), max output tokens per call,
token budget per summary, a **monthly spend limit in the Anthropic Console** as the hard backstop,
AWS Budgets alarms. With Haiku 5.5 a chat question costs < 1 cent, so even a fully used global cap
stays at ~1 $/day; with Sonnet the global cap would need to be lower. The UI shows the remaining quota
and a clear message when a limit is reached.

### 5.2 Extension to validate: web-research data in the chat

Idea: the chat also uses the stock's `ResearchFinding`s, not only the reports.

**Why it is cheap to try:** 18 criteria with short claims are ~1–2k tokens — they need no RAG and can
be passed in full with every chat call as an additional `search_result` source (title = criterion,
source = URL, with as-of date), so citations distinguish "Report Q1 2026, p. 12" from "web research,
2026-08-16, source URL".

**Why it needs validation before it is switched on:**
- Findings are AI-generated and can be outdated or wrong; reports are primary sources. When both give
  a figure, the answer must prefer the report and name the difference.
- The data comes from web content → it stays "data, not instructions" in the prompt (existing
  guardrail), and the backend already validates it at upload.
- The snapshot lives in RDS, the chat Lambda runs outside the VPC without RDS access → the backend
  copies the findings into the chat job (DynamoDB item limit 400 KB is far above ~10 KB).

**Validation plan:** behind a feature flag (`chat.include-research-findings`, default `false`); about
10 test questions against the GOOG sample, each answered with and without findings; checked for
correct source attribution, conflicts handled correctly, extra cost per question. Decision afterwards
is recorded in the decision log.

## 6. Deep-analysis button (dummy)

The real company research agent (`company-research-agent/`) is **frozen** and not developed further
for now. The button exists to show the wiring:

1. Any logged-in user clicks "Run deep analysis" for the selected stock.
2. Backend checks login and the daily limit, writes a job `DEEP_ANALYSIS_DEMO` (isin, account,
   timestamp) into the DynamoDB job table.
3. DynamoDB Stream → small Lambda `deep-analysis-demo` (no LLM call, no cost) writes the result into
   the job: *"Lambda `deep-analysis-demo` was invoked at 2026-11-03T14:05:12Z for ISIN … (request id …).
   The deep analysis itself is not available yet — this demonstrates the asynchronous pipeline that
   will run it."*
4. UI polls the job and shows the message with the server timestamp (proof that a real Lambda ran,
   not a string from the frontend bundle).

**Changed compared to the AWS design (Topic 5 part B):** the stub was planned as a *public* button
behind a direct API-Gateway→Lambda route. Since the app is now login-only and the login lives in the
backend, the trigger goes through the backend and the same job table as chat and summary. Benefits:
the existing login protects it without a second auth mechanism, and it becomes the **first, simplest
use of the job pattern** that chat and summary build on later. The EventBridge daily heartbeat from
Topic 5 is not required by this scope; it stays an optional learning exercise.

## 7. Architecture (updated overview)

```
Browser ─► API Gateway ─► backend/ (Spring Boot, ECS, private VPC, no NAT)
             login, roles, limits, portfolio, research snapshots, report metadata (RDS)
             │ S3 gateway endpoint                    │ DynamoDB gateway endpoint
             ▼                                        ▼
       S3: reports (private)                  DynamoDB "jobs": CHAT, SUMMARY, DEEP_ANALYSIS_DEMO,
             │ S3 events                      chat history (usage counters: RDS)
             ▼                                        │ Stream
 ┌ report-intelligence/ (Quarkus, Lambdas outside the VPC) ────────────────────────────────────┐
 │ ingest · cleanup · summarize · chat  (→ Titan V2, S3 Vectors, Anthropic API)                 │
 └──────────────────────────────────────────────────────────────────────────────────────────────┘
   deep-analysis-demo Lambda (tiny, no LLM) — triggered by the same stream, filtered by job type
```

Event-source mappings filter by job type, so each Lambda only receives its own jobs. Everything else
(security of the upload path, IAM per function, DLQs, SSM for the API key) as in the RAG design.

## 8. Backend changes

1. **Fixed account list in configuration, no user table:** extend today's
   `InMemoryUserDetailsManager` from one user to a list: the ADMIN (`app.admin.*`, as today) plus a
   list of USER accounts (`app.users[n].username`, `app.users[n].password-hash`), all with role
   `USER`. In AWS the list comes from SSM Parameter Store SecureString (free), never from the repo.
   No registration, no user management UI, no Cognito — nothing to run, nothing to pay; for a handful
   of accounts that change rarely this is simpler than a table plus admin screens.
   **Adding, removing or resetting an account:** generate a BCrypt hash with the existing
   `AdminPasswordHashGenerator`, edit the parameter, redeploy. Sessions are in memory, so the restart
   logs everybody out once — acceptable at this scale. Limit counters and chat history are keyed by
   username, so a username must never be reused for a different person (remove the old history first).
   If the list ever grows or changes often, switch to a `app_user` table (later, not planned).
2. **Login mechanism: server-side session instead of HTTP Basic.** `POST /api/auth/login`,
   `POST /api/auth/logout`, `GET /api/auth/me` (account + role, drives the UI). Session cookie
   `HttpOnly`, `Secure`, `SameSite=Strict`, idle timeout (e.g. 2 h); new session id after login
   (session fixation protection, Spring default); CSRF protection re-enabled (cookie-to-header token for
   the SPA). In-memory sessions are enough for one ECS task; a deploy logs users out, which is
   acceptable. Cost: 0. Reason over Basic Auth: today the password sits in browser memory and is sent
   with every request; with a session it is sent once.
3. **Brute-force protection** (design risk 6):
   - Login attempts limited per IP (e.g. 5 per minute) with growing delays after failures — in the
     backend (small in-memory counter) plus API Gateway throttling on `/api/auth/login`.
   - Per account a short temporary lockout (e.g. 15 min after 10 failures). A stranger who knows a
     username could lock out that one person for 15 minutes — acceptable for a handful of friendly
     users; the per-IP limit makes it slow.
   - Long random passwords (ADMIN ≥ 20 characters, USER ≥ 16), generic error message ("Invalid
     username or password"), failed and successful logins logged (CloudWatch) without the password.
   - Only over HTTPS (API Gateway provides TLS).
   - Optional, later: TOTP second factor for the ADMIN account only (free, no AWS service needed).
4. **`SecurityConfig`:** remove the `permitAll` rules for `/api/portfolio/public` and
   `/api/research/snapshots/**`; allow only the login endpoint and the legal pages without login;
   ADMIN role required on all write endpoints (portfolio, research snapshots, reports).
5. **Portfolio API:** `GET /api/portfolio` returns the role-dependent view (ADMIN: full,
   USER: `PortfolioPositionView` without quantity/price/date); new `PUT /api/portfolio/{isin}`
   (correction) and `DELETE /api/portfolio/{isin}` for ADMIN; buy/sell stay.
6. **Usage limits in RDS** (decided 2026-11-03): Flyway table `usage_counter` (account, job type,
   day, count; primary key over the first three). Check and increment in one statement
   (`INSERT … ON CONFLICT … DO UPDATE SET count = count + 1 … WHERE count < :limit`, zero rows = limit
   reached), so two parallel requests cannot both slip past the limit. The global cap is a row with
   the reserved account name `*` in the same table, checked in the same transaction.

### 8.1 Why this is secure enough and cheap

| Threat | Countermeasure | Cost |
|---|---|---|
| Guessing passwords | long random passwords, per-IP rate limit, growing delays, API Gateway throttling | 0 |
| A USER password passed on or leaked | USER is read-only plus limited chat; owner resets or removes just that account and redeploys | 0 |
| A USER account used to run up AI cost | daily limits per account plus global cap, Anthropic monthly spend limit | 0 |
| One user reading another user's chat | account taken from the session, never from request parameters; test per endpoint | 0 |
| Privilege escalation USER → ADMIN | role check on every write endpoint in the backend, tests per endpoint for `403` | 0 |
| Session theft | `HttpOnly` + `Secure` + `SameSite=Strict` cookie, HTTPS only, idle timeout | 0 |
| Password leak from the repo | only BCrypt hashes, only in SSM/env vars, never in git | 0 |

Deliberately not used: Cognito (more setup than a handful of fixed accounts need), WAF (~5–8 $/month).

## 9. Compliance (update of 2026-07-21 design, Section 9)

- The app is no longer publicly readable, but logged-in USERs still see positions and AI texts →
  descriptive, non-recommending wording, disclaimer and the "owner holds this position" note stay.
- Individual accounts are assigned to persons, so usernames, chat questions per user and login logs
  are **personal data** → a **Datenschutzerklärung** is mandatory (GDPR), reachable without login.
  Keep it small: no e-mail or real name required (usernames may be pseudonyms), chat history with
  TTL (e.g. 30 days), delete a user's history when the account is removed. The UI advises against
  entering personal data in the chat.
- Not legal advice; check the wording of Impressum/Datenschutz before handing out the first account.

## 10. Out of scope / dropped

| Item | Status |
|---|---|
| Automatic daily screening, three-tier research funnel, universe provider, sector benchmarks (2026-07-30 redesign) | dropped from scope; spec kept as history |
| Change detection/alerts for portfolio positions, suggestions list (2026-07-21 design) | dropped from scope |
| Real deep research via `company-research-agent/` (incl. Lambda Web Adapter deployment) | frozen, only the dummy (Section 6) |
| EventBridge heartbeat (Topic 5 part B) | optional, not required |
| Public read-only access, public landing page as default view | replaced by login-only (Section 2) |
| MCP client backend → agent, llm-broker | not pursued |
| Cross-stock chat, streaming answers, self-registration | later |

## 11. Effect on existing documents

| Document | What is superseded |
|---|---|
| `2026-07-21-value-screener-design.md` | Purpose/scope (screening, alerts), Section 9 "read view without login" |
| `2026-07-30-screening-cost-redesign-design.md` | whole scope (funnel/scheduler no longer planned) |
| `2026-09-20-aws-deployment-design.md`, Section 7 | public stub button via direct Lambda route → via backend + job table; real agent Lambda frozen |
| `PROJECT-STATUS.md`, "Portfolio Research UI" (2026-08-20) | public snapshot endpoint, landing page as start view, research per portfolio position |
| `2026-11-03-quarterly-report-rag-design.md` | visibility/roles, "evaluation" → summary, chat history and limits per account (updated in place) |

## 12. Implementation phases

| Phase | Content | Needs AWS? |
|---|---|---|
| 1 | **Login-only + roles:** account list (ADMIN + USER accounts) from configuration, session login, brute-force protection, `SecurityConfig`, role-dependent portfolio API incl. correct/delete, frontend login gate, header Portfolio/Research, legal pages public | no |
| 2 | **Research page:** stock selector, overview/fundamentals/qualitative sections, portfolio badge, report list and chat as placeholders, greyed-out upload with tooltip | no |
| 3 | **Job table + deep-analysis dummy:** DynamoDB table, gateway endpoint, stream, `deep-analysis-demo` Lambda, polling UI, usage limits — first AWS building block | yes (≈ 0 $) |
| 4 | **Quarterly reports:** `report-intelligence/` skeleton, upload/list/read/delete, validation, indexing (RAG design phases 0–2) | yes |
| 5 | **"Evaluate quarterly reports" button**, stored and reused (RAG design phase 3) | yes + Anthropic |
| 6 | **Chat with limits** (RAG design phase 4) | yes + Anthropic |
| 7 | **Validate web-research data in the chat** (Section 5.2) | Anthropic |
| 8 | **Login page design** and project description for showcase visitors; until then the login page is functional only (Section 3) | no |

Phases 1–2 are fully local and cost nothing, so they can start while ECS/RDS are stopped. Working
mode unchanged: concept explanation before each task, TDD, the user creates AWS resources and does
all git operations.

## 13. Open items

Resolved 2026-11-03: accounts (one ADMIN plus a handful of individual USER accounts as a list in
configuration, no Cognito, no user table), access for others (owner creates the accounts), login mechanism (session cookie, Section 8), usage counters
(RDS, Section 8 item 6), login page (functional only for now, design in phase 8).

1. **Hosting after the Free Plan** — the login-only app needs the backend and a database running for
   external users; decision needed before ~2027-03-11. **Guideline from the user:** parts may be moved
   off AWS if that **clearly** reduces cost **and** does not make the setup complicated. To be
   evaluated as a short cost comparison before the decision, with three criteria: monthly cost, extra
   complexity (one more provider, accounts, networking, secrets), loss of AWS learning value. The
   biggest cost items today are the always-on parts (ECS Fargate task, RDS instance, interface
   endpoints); the serverless parts (Lambda, DynamoDB, S3, S3 Vectors) cost ~0 when idle and stay on
   AWS.
2. **Model defaults and final limit values** — decided later, after the first real test.

## Decision log

- 2026-11-03: Goal reworded: showcase first, personal stock-analysis tool second.
- 2026-11-03: Whole app behind login; roles ADMIN (owner) and USER. USERs see positions without
  quantities/prices, can read reports and research data, can use chat and the deep-analysis dummy
  with daily limits; only ADMIN uploads/changes/deletes reports and edits the portfolio.
- 2026-11-03: UI reduced to two areas, Portfolio and Research; Research is stock-centric (selector →
  web-research data, reports, chat, deep-analysis button).
- 2026-11-03: Research agent stays frozen; deep-analysis button is a dummy Lambda showing that it was
  invoked. Routed through backend + job table instead of a public API-Gateway route (consequence of
  login-only).
- 2026-11-03: Chat: summary of all reports of a stock + free questions; using web-research data in
  the chat is an extension that must be validated first (feature flag).
- 2026-11-03: Screening funnel, scheduler, alerts and public read access dropped from scope.
- 2026-11-03: Login must be secure but cheap. Exactly two accounts: ADMIN (only the owner knows the
  password) and one shared guest account (same username/password for all, set by the owner, handed to
  a handful of people). Accepted because guests can do little harm (read-only plus limited chat).
  Consequences: no user table, no Cognito, no registration; limits and chat history per account
  (guests share both); session cookie instead of Basic Auth; per-IP rate limiting instead of a guest
  account lockout.
- 2026-11-03 (revised the same day by the user): **no shared guest account.** Instead a handful of
  individual USER accounts, all with the same role, created by the owner. Each has its own daily
  limits and its own chat history. Still no user table and no Cognito: the accounts are a list in
  configuration (SSM). Consequences: the per-session guest limit is dropped; a short per-account
  lockout is now acceptable (it only affects one person); usernames are personal data → privacy
  policy covers them.
- 2026-11-03: Login page is functional only (username, password, login); its design comes at the end
  (phase 8). Usage counters live in RDS. Hosting: parts may leave AWS if that clearly cuts cost
  without making things complicated — evaluated before the Free Plan ends. Model choice and final
  limits decided later.
- 2026-11-04: The summary button becomes "Evaluate quarterly reports". Its result is stored per stock
  and reused. With an unchanged report set a click shows "No new quarterly reports — evaluation
  already available.", with no LLM call and no quota used. Only the ADMIN can force a new
  evaluation. The chat stays available at any time. Details in the RAG design, Section 6.

---

## Deutsche Zusammenfassung

**Ziel:** Die Anwendung ist in erster Linie ein Vorzeigeprojekt (Bewerbung, Freelancing,
AWS-Zertifizierung) und daneben dein eigenes Werkzeug zur Aktienanalyse. Weiterhin keine
Handelsfunktion und keine Empfehlungen, Kostendeckel ca. 20 €/Monat.

**Zugang:** Die ganze Anwendung ist nur nach Login nutzbar. Ohne Login erreichbar sind nur die
Login-Seite, Impressum, Datenschutzerklärung und Disclaimer. Die Login-Seite hat vorerst nur
Nutzername, Passwort und Login-Button; gestaltet wird sie ganz am Ende (Phase 8). Es gibt
einen ADMIN-Account (dein Account, nur du kennst das Passwort) und eine Handvoll eigener
USER-Accounts, einen pro Person. Alle USER haben dieselbe Rolle; du legst Nutzername und Passwort fest
und gibst sie weiter. ADMIN sieht und bearbeitet alle Positionsdaten und lädt Quartalsberichte hoch,
ändert und löscht sie. USER sehen nur, welche Positionen du hältst (ohne Stückzahl, Preis oder Datum),
lesen Berichte und Recherchedaten und nutzen Chat sowie Tiefenanalyse-Button. Für USER ist der
Upload-Button ausgegraut, mit Tooltip „nur für Admin“. Das Backend sperrt alle Schreibzugriffe
zusätzlich mit 403.

**Login (sicher und günstig):** keine Nutzertabelle, kein Cognito. Alle Accounts stehen als Liste mit
BCrypt-Hash in der Konfiguration (in AWS im kostenlosen SSM Parameter Store). Account anlegen,
löschen oder Passwort zurücksetzen: Hash erzeugen, Parameter ändern, neu deployen. Login per
Session-Cookie statt Basic Auth. Schutz gegen Passwort-Raten: Begrenzung pro IP, wachsende Wartezeit
und eine kurze Sperre pro Account. Kosten: 0.

**Eigene Limits und eigener Chatverlauf:** Jeder Account hat eigene Tageslimits und einen eigenen
Chatverlauf. Kein Nutzer sieht die Fragen eines anderen; das Backend nimmt den Account immer aus der
Session, nie aus der Anfrage. Die Limit-Zähler liegen in RDS und werden in einem einzigen SQL-Befehl
geprüft und hochgezählt, damit parallele Anfragen das Limit nicht umgehen. Weil die Accounts Personen
zugeordnet sind, sind Nutzernamen und Chatfragen personenbezogene Daten; die Datenschutzerklärung muss
das abdecken.

**Oberfläche:** Zwei Bereiche. Unter **Portfolio** steht die Positionsliste, für den Admin mit
Stückzahl, Preis und Bearbeiten. Unter **Research** wählst du eine erfasste Aktie aus und siehst dann
Unternehmensüberblick, Kennzahlen und qualitative Einschätzungen aus der Websuche (jeweils mit Quelle
und Datum), die Liste der Quartalsberichte (Admin: hochladen, ändern, löschen), den KI-Chat und den
Tiefenanalyse-Button.

**Chat:** Der Button „Zusammenfassen“ fasst alle vorliegenden Quartalsberichte der Aktie zusammen.
Freie Fragen beantwortet der Chat per RAG mit Bericht- und Seitenangabe. Steht etwas nicht in den
Quellen, sagt er das, statt zu raten. Limits pro Account und Tag sowie ein globales Tageslimit
schützen vor Kosten (Startwerte pro USER: 10 Fragen, 2 Zusammenfassungen und 5 Tiefenanalyse-Klicks
pro Tag; global 100). Dazu kommen ein
Ausgabenlimit in der Anthropic Console und AWS-Budget-Alarme. Ob der Chat auch die Websuche-Daten
nutzen soll, wird erst validiert: hinter einem Feature-Flag, mit ca. 10 Testfragen jeweils mit und
ohne diese Daten.

**Tiefenanalyse:** Der Research Agent bleibt eingefroren. Der Button erzeugt über das Backend einen
Job in DynamoDB. Eine kleine Lambda ohne KI-Aufruf antwortet mit Zeitstempel und dem Hinweis, dass
die Funktion noch nicht fertig ist. Damit ist das zugleich der erste, einfachste Einsatz des
Job-Musters, auf dem Chat und Zusammenfassung später aufbauen.

**Gestrichen:** automatisches Screening, Recherche-Trichter, Scheduler, Änderungs-Alarme, öffentliche
Leseansicht, echte Tiefenrecherche.

**Phasen:** 1 Login mit Account-Liste und Rollen, 2 Research-Seite (beide lokal, ohne AWS-Kosten), 3 Job-Tabelle und
Tiefenanalyse-Dummy, 4 Quartalsberichte, 5 Zusammenfassung, 6 Chat mit Limits, 7 Validierung der
Websuche-Daten im Chat, 8 Gestaltung der Login-Seite.

**Offene Punkte:** Hosting nach dem Free Plan: Teile dürfen aus AWS ausgelagert werden, wenn das
deutlich Kosten spart und nicht kompliziert wird. Das wird vor Ende des Free Plans per kurzem
Kostenvergleich geprüft (Kosten, Zusatzaufwand, Verlust an AWS-Lernwert). Kandidaten sind die dauerhaft
laufenden Teile (ECS, RDS, Interface-Endpoints), die serverlosen Teile bleiben bei AWS. Modellwahl und
endgültige Limits entscheiden wir später.
