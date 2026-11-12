# v2 Phase 2b — Data Age, Investment Thesis, Research History — Design

Date: 2026-11-12. Branch: `feature/v2-phase2-frontend` (same branch as 2a; merged to `main` after 2b).
Parent spec: [`2026-11-03-app-scope-v2-design.md`](2026-11-03-app-scope-v2-design.md) Section 4a.
Builds on: phase 1 backend ([`2026-11-04-v2-phase1-backend-research-groups-design.md`](2026-11-04-v2-phase1-backend-research-groups-design.md))
and phase 2a frontend ([`2026-11-08-v2-phase2a-frontend-design.md`](2026-11-08-v2-phase2a-frontend-design.md)).

## 1. Intent

The owner looks at research data to make decisions about real positions. Three things are missing:

- **Data age.** Old figures look just as current as new ones. The app should show how old the research
  is, and flag what is outdated.
- **Investment thesis.** The owner wants to write down, per stock, why it is interesting, what he
  expects, and what would make him sell or drop it, plus a manual status (watching / rejected).
- **Research history.** When a re-research replaces a value, the old value is lost today. The owner
  wants to see how values changed (for example P/E over several research runs) and what was there
  before a change.

Phase 2 is complete when 2a (done) and 2b are both done.

## 2. Changes against the scope spec (user decisions 2026-11-12)

1. **Stale threshold default is 9 months** (scope spec said 6): `research.stale-after: P9M`.
2. **USERs can read the investment thesis and the status** (scope spec said ADMIN only). Only the
   ADMIN edits. The thesis always carries the fixed note **"Persönliche Einschätzung des Betreibers,
   keine Anlageempfehlung."** The later "Thesis check" (scope phase 5) follows the same rule.
   Reason: the users are a small, hand-picked circle; the note keeps the app's non-recommending
   stance. Accepted risk, discussed with the user: free text may reveal details that the USER
   portfolio view hides (for example prices); the owner takes care when writing.
3. The rest of Section 4a stays as written.

The builder updates the scope spec Section 4a, its decision log, and the README visibility table
(`README.md`, "Investment-These und Thesis check") to match.

## 3. Goals

1. Data age: every finding is flagged `stale` when its `asOfDate` is older than the threshold; every
   stock shows "Zuletzt recherchiert <date>" and, when that date is stale, "Neu-Recherche erwägen".
2. Investment thesis per ISIN: three text fields, manual status WATCHING / REJECTED with reason,
   `updated_at`; read by all roles, written by ADMIN only.
3. Two ways to write it: an "These bearbeiten" form on the research detail page, and an optional
   "Warum kaufst du?" field in the portfolio buy form that is appended to the thesis with a date.
4. Research history: whenever an upsert (local ingest or live sync) changes a finding, the previous
   values are kept in an append-only table. Group changes are kept the same way.
5. Research detail page: per criterion, older values can be expanded; group changes are listed.

## 4. Non-goals

- Exporting or importing history between systems. Export/import still transfers only the current
  state (phase 1 decision); each system builds its own history from the changes it sees.
- Editing or deleting history entries. No API for that.
- Charts of values over time. A list is enough for now.
- Notifications ("this stock is outdated") outside the research pages.
- Using the thesis in report evaluation (scope phase 5).

## 5. Anti-goals

- History must never lose or rewrite an entry. Rows are only inserted.
- The "Holding" state is never stored; it is derived from the portfolio, so it can never contradict
  the real holdings.
- A purchase reason never overwrites existing thesis text.
- A USER can never change a thesis (backend enforces it, not only the UI).

## 6. Data age

- Configuration `research.stale-after` (ISO-8601 period), default `P9M`, in `application.yml`.
- "Today" comes from the existing `Clock` bean (`security/ClockConfig.java`), so tests can fix it.
- A finding is **stale** when `asOfDate` is before `today minus stale-after`. A finding without
  `asOfDate` is treated as stale. *(Claude's call: unknown age should not look current.)*
- `FindingView` gets `stale: boolean`.
- `CompanySnapshotView` gets `lastResearchedAt: LocalDate | null` (the newest `asOfDate` of its
  findings) and `stale: boolean` (true when `lastResearchedAt` is null or before the threshold).
- Frontend:
  - Research detail header: `Zuletzt recherchiert <dd.mm.yyyy>`, plus ` – Neu-Recherche erwägen`
    when the snapshot is stale. Without any finding: `Noch keine Befunde`.
  - Each stale finding shows the marker `veraltet` next to its date.
  - Research overview rows show `veraltet` for stale snapshots. *(Claude's call: cheap and helps
    pick what to re-research.)*

## 7. Investment thesis

### 7.1 Data

New table `investment_thesis` (Flyway migration), one row per ISIN:

| Column | Type | Notes |
|---|---|---|
| `isin` | varchar(12), primary key | normalized (trim + uppercase), same ISIN regex as elsewhere |
| `why_interesting` | text, nullable | max 4000 characters |
| `expected_development` | text, nullable | max 4000 |
| `exit_criteria` | text, nullable | "what would make me sell or drop it", max 4000 |
| `status` | varchar, nullable | `WATCHING` or `REJECTED` |
| `status_reason` | varchar(500), nullable | |
| `updated_at` | timestamptz, not null | set on every write |
| `version` | bigint | optimistic locking |

- No foreign key to `company_snapshot`: a thesis can exist for a stock that is not researched yet
  (for example written in the buy form). The research page shows it once a snapshot exists.
- Length limits: `why_interesting` may grow through appended purchase reasons; when an append would
  exceed 4000 characters the request fails with 422 `Thesis text too long` and nothing is saved
  (buy included, same transaction). *(Claude's call.)*

### 7.2 API

| Request | Who | Behaviour |
|---|---|---|
| `GET /api/research/theses/{isin}` | all logged-in roles | 200 thesis view; 404 `{"error":"Thesis not found"}` if none |
| `PUT /api/research/theses/{isin}` | ADMIN | creates or replaces all fields; body carries `version` (null when creating); stale version → 409 `{"error":"Conflict, please reload"}` |

Thesis view: `isin, whyInteresting, expectedDevelopment, exitCriteria, status, statusReason,
updatedAt, version, holding`. `holding` is derived from the portfolio at read time.

- Validation: ISIN regex; text limits; `status` must be `WATCHING`, `REJECTED` or null;
  `statusReason` only allowed when `status` is set (else 400).
- **Holding rule:** when the ISIN is in the portfolio, the status does not apply. The backend still
  stores a status sent with the PUT (it becomes relevant again after a full sale); the view returns
  `holding: true` and the frontend shows `Halte ich` instead of the status. *(Claude's call: no
  rejection, no data loss on sell.)*
- Security: GET is covered by "GET /api/** authenticated"; PUT by "anything else ADMIN" — add tests
  per role as for the portfolio (USER → 403, anonymous → 401).

### 7.3 Purchase reason in the buy form

- `BuyPositionRequest` gets an optional `purchaseReason` (max 2000 characters, blank = absent).
- When present, in the same transaction as the buy: the thesis for that ISIN is created if missing,
  and the text is appended to `why_interesting` as a new paragraph
  `[<yyyy-mm-dd>] Kauf: <text>` (date from the `Clock`). Existing text is never changed.
- Frontend buy form: optional textarea `Warum kaufst du? (optional, wird an die Investment-These
  angehängt)`.

### 7.4 Frontend (research detail)

- New section **"Investment-These"** directly after "A. Überblick" (visible to all roles):
  - fixed note `Persönliche Einschätzung des Betreibers, keine Anlageempfehlung.`
  - the three texts with headings `Warum interessant`, `Erwartete Entwicklung`,
    `Wann verkaufen oder verwerfen`, line breaks preserved, rendered as text (never as HTML);
  - status line: `Halte ich` when holding, else `Beobachten` / `Abgelehnt` with reason, or
    `Kein Status`; `Aktualisiert am <date>`;
  - no thesis: `Noch keine Investment-These`.
- ADMIN only: button `These bearbeiten` → inline form with the three textareas, a status select
  (`Kein Status`, `Beobachten`, `Abgelehnt`; disabled with hint `Status gilt nicht, solange die
  Position gehalten wird` when holding) and the reason field; `Speichern` / `Abbrechen`. 409 →
  `Die These wurde inzwischen geändert, bitte neu laden` and reload.

## 8. Research history

### 8.1 Data (append-only)

New table `research_finding_history`:

| Column | Notes |
|---|---|
| `id` | identity |
| `company_snapshot_id` | FK to `company_snapshot` |
| `criterion_key` | |
| `numeric_value`, `boolean_value`, `claim`, `source_url`, `as_of_date` | the **previous** values |
| `replaced_at` | timestamptz, from the `Clock` |
| `origin` | `INGEST` (local research) or `SYNC` (live import) |

New table `stock_group_history`:

| Column | Notes |
|---|---|
| `id` | identity |
| `company_snapshot_id` | FK |
| `previous_group_key`, `previous_group_source` | nullable (no group before) |
| `new_group_key`, `new_group_source` | |
| `changed_at` | timestamptz |
| `origin` | `INGEST`, `SYNC` or `MANUAL` |

*(Claude's call: the group history also keeps the new value, so one row is readable on its own.)*

### 8.2 When rows are written

- During `CompanySnapshotService.upsert`, for each incoming finding that **replaces an existing
  finding with different content** (any of numeric value, boolean value, claim, source URL, as-of
  date differs): insert one finding-history row with the old values before updating. Identical
  re-sends and brand-new criteria write nothing.
- Whenever a snapshot's group actually changes (AI proposal accepted, sync with MANUAL, or the ADMIN's
  manual assignment): insert one group-history row. Ignored AI proposals (MANUAL wins) write nothing.
- `origin`: the ingest controller passes `INGEST`, the sync controller `SYNC`, the manual group
  assignment `MANUAL`.
- No update or delete methods on the history repositories; no cascade deletes from snapshots
  (snapshots are never deleted today).

### 8.3 API and frontend

- `GET /api/research/snapshots/{isin}/history` (all logged-in roles) →
  `{ findings: [ {criterionKey, numericValue, booleanValue, claim, sourceUrl, asOfDate, replacedAt,
  origin} ], groups: [ {previousGroup {key,name}|null, previousSource, newGroup {key,name},
  newSource, changedAt, origin} ] }`, both newest first; 404 if the snapshot doesn't exist.
- Research detail: each criterion with history gets a toggle `Verlauf (<n>)` that expands its older
  values (value or claim, as-of date, `ersetzt am <date>`). Under "A. Überblick" a small list
  `Gruppenverlauf` when group changes exist (`<alt> → <neu> (<manuell|KI-Vorschlag>) am <date>`).
- History is loaded together with the detail page; a failed history load shows the page without
  history and a short note `Verlauf konnte nicht geladen werden`.

## 9. Constraints

- Same working mode and rules as phases 1 and 2a: subagent-driven, TDD, the user does all git
  operations, concept explanation before each task, no new npm or Maven dependencies, UI texts
  German, code English, JSON errors `{"error":"…"}`.
- Flyway: new migrations after V4 (V5 thesis, V6 history, or one V5 — builder's call). Testcontainers
  tests for the migrations run on the user's machine or here when Docker is available.
- Backend tests follow the existing patterns (`@WebMvcTest` slices with `SecuritySliceTestConfig`,
  repository tests with Testcontainers, `Clock` fixed in tests).

## 10. Left to the builder

Class and file names, DTO shapes beyond the fields named here, how the upsert detects "different
content", task order, test granularity, exact layout of the new sections (plain and functional).

## 11. Done when

- All goals in Section 3 work against `./dev-up.sh` for ADMIN and USER; the user checks manually.
- Backend `mvn test` (with Docker) and frontend `npm test` + `npm run build` green.
- Scope spec Section 4a, its decision log and the README visibility table updated (Section 2).

## Deutsche Zusammenfassung

Phase 2b bringt drei Dinge. Danach ist Phase 2 fertig.

- **Daten-Alter:**
  - Befunde, deren Datum älter als **9 Monate** ist (einstellbar über `research.stale-after`), sind
    „veraltet“.
  - Jede Aktie zeigt „Zuletzt recherchiert …“ und bei Bedarf „Neu-Recherche erwägen“.
  - Die Übersicht markiert veraltete Aktien.
- **Investment-These:**
  - drei Textfelder plus Status Beobachten/Abgelehnt mit Begründung
  - „Halte ich“ wird aus dem Portfolio abgeleitet und nie gespeichert.
  - **USER dürfen die These lesen** (geänderte Entscheidung), nur der ADMIN bearbeitet sie.
  - Fester Hinweis „Persönliche Einschätzung des Betreibers, keine Anlageempfehlung.“
  - Das optionale Feld „Warum kaufst du?“ im Kaufen-Formular hängt den Text mit Datum an die These an
    und überschreibt nie etwas.
- **Recherche-Historie:**
  - Ändert eine Recherche oder ein Import einen Befund, werden die alten Werte in einer Tabelle
    aufbewahrt, in die nur geschrieben, aber nie geändert wird.
  - Gruppenwechsel werden ebenso festgehalten.
  - Auf der Detailseite lässt sich je Kriterium der Verlauf aufklappen, dazu kommt ein
    Gruppenverlauf.
  - Export und Import übertragen weiterhin nur den aktuellen Stand.

Scope-Spec und README werden an die beiden geänderten Entscheidungen angepasst (9 Monate, These für
USER lesbar).
