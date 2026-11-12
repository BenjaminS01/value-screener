# v2 Phase 2b — Data Age, Investment Thesis, Research History — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show how old research data is, let the owner keep an investment thesis per stock (read by all, edited by ADMIN), and keep an append-only history of replaced findings and group changes.

**Architecture:** Backend first (three tasks, each with its own migration/tests), then two frontend tasks on the research detail page, overview and buy form. "Today" always comes from the existing `Clock` bean; the stale threshold is configuration.

**Tech Stack:** Spring Boot 3.3 / Java 21 / JPA / Flyway / PostgreSQL 16 (Testcontainers), React 18 / TypeScript / Vitest.

**Spec:** [`docs/superpowers/specs/2026-11-12-v2-phase2b-data-age-thesis-history-design.md`](../specs/2026-11-12-v2-phase2b-data-age-thesis-history-design.md)

## Global Constraints

- **The user does all git operations.** "Commit" steps mean: stop, show `git diff --ignore-cr-at-eol --stat`, propose a short message with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. Never run state-changing git.
- Working mode: give the user the task's **Concept** line before each task (the user knows Java well, is new to React — explain frontend concepts). TDD: red → green → refactor.
- No new Maven or npm dependencies. No router. UI plain and functional, texts German, code English.
- Backend build: `export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64; export PATH=$JAVA_HOME/bin:$PATH; cd backend && mvn -q test -Dtest=<Class>`; full `mvn -q test` (Docker available). Before counting tests `rm -rf backend/target/surefire-reports`.
- Frontend: `cd frontend && npm test`, `npm run build`. Network mocked with real `Response` objects (empty success bodies are real since phase 2a).
- Base package `com.valuescreener`; new research code in `research`. Records for DTOs, entities validate in constructors, `@RestControllerAdvice(assignableTypes = …)` per controller group. New `@WebMvcTest` slices use `@Import(SecuritySliceTestConfig.class)`.
- JSON errors `{"error":"<message>"}`; fixed messages: 409 `Conflict, please reload`, 404 thesis `Thesis not found`, 422 `Thesis text too long`.
- ISIN normalization trim + uppercase, regex `[A-Z]{2}[A-Z0-9]{9}[0-9]`.
- Stale threshold: property `research.stale-after`, ISO-8601 period, default `P9M`. A date is stale when it is **before** `LocalDate.now(clock).minus(staleAfter)`.
- Thesis limits: each text field ≤ 4000 characters, `statusReason` ≤ 500, `purchaseReason` ≤ 2000. Status values `WATCHING`, `REJECTED`. Purchase reason appended as a new paragraph `"\n\n[" + yyyy-MM-dd + "] Kauf: " + text` (no leading blank lines when the field was empty).
- History origin values: findings `INGEST` | `SYNC`; groups `INGEST` | `SYNC` | `MANUAL`. History tables are insert-only.
- Exact German UI copy (verbatim):
  - `Zuletzt recherchiert <dd.mm.yyyy>`, suffix ` – Neu-Recherche erwägen`; `Noch keine Befunde`; marker `veraltet`
  - section `Investment-These`; note `Persönliche Einschätzung des Betreibers, keine Anlageempfehlung.`; headings `Warum interessant`, `Erwartete Entwicklung`, `Wann verkaufen oder verwerfen`; status `Halte ich` / `Beobachten` / `Abgelehnt` / `Kein Status`; `Aktualisiert am <dd.mm.yyyy>`; `Noch keine Investment-These`
  - ADMIN: button `These bearbeiten`, select options `Kein Status` / `Beobachten` / `Abgelehnt`, hint `Status gilt nicht, solange die Position gehalten wird`, label `Begründung`, buttons `Speichern` / `Abbrechen`; 409 `Die These wurde inzwischen geändert, bitte neu laden`; 422 shows the backend message
  - buy form textarea label `Warum kaufst du? (optional, wird an die Investment-These angehängt)`
  - history: toggle `Verlauf (<n>)`, entry suffix `ersetzt am <dd.mm.yyyy>`, list title `Gruppenverlauf`, entry `<alt> → <neu> (<manuell|KI-Vorschlag>) am <dd.mm.yyyy>` (no previous group → `—`), load failure `Verlauf konnte nicht geladen werden`

## Review Focus

1. **Re-sending identical research** (same values for a criterion) → no history row; only real changes create one. Test in Task 2.
2. **AI group proposal ignored because MANUAL wins** → no group-history row; the ADMIN re-assigning the same group → no row either. Test in Task 2.
3. **Buy with a purchase reason whose append would exceed 4000 characters** → 422 `Thesis text too long`, and neither position nor thesis changed (one transaction). Test in Task 3.
4. **USER sends `PUT /api/research/theses/{isin}`** → 403; anonymous → 401; thesis unchanged. Test in Task 3.
5. **Thesis text containing `<script>` or line breaks** → shown literally with line breaks kept, never interpreted as HTML. Test in Task 5.

---

### Task 1: Data age in the backend

**Concept:** "How old is this?" is answered on the server: one small component knows "today" (from the `Clock`) and the threshold (from configuration) and tells the views which findings and snapshots are stale. The frontend only displays the flags.

**Files:**
- Create: `backend/src/main/java/com/valuescreener/research/ResearchFreshness.java`
- Modify: `FindingView.java`, `CompanySnapshotView.java`, `CompanySnapshotService.java` (all `CompanySnapshotView.from` calls), `backend/src/main/resources/application.yml`
- Test: `ResearchFreshnessTest.java` (plain JUnit, fixed `Clock`), adapt `CompanySnapshotServiceTest`, `CompanySnapshotControllerTest`

**Interfaces:**
- Produces:
  - `@Component class ResearchFreshness(Clock clock, @Value("${research.stale-after:P9M}") Period staleAfter)` with `LocalDate staleBefore()` and `boolean isStale(LocalDate date)` (null → true).
  - `FindingView` gains `boolean stale` (last component). `FindingView.from(ResearchFinding, ResearchFreshness)`.
  - `CompanySnapshotView` gains `LocalDate lastResearchedAt` (newest finding `asOfDate`, null without findings) and `boolean stale` (`isStale(lastResearchedAt)`), placed after `findings`. `CompanySnapshotView.from(CompanySnapshot, ResearchFreshness)`.

- [ ] **Step 1: Failing tests** `ResearchFreshnessTest`: clock fixed at 2026-11-12, `P9M` → `staleBefore()` = 2026-02-12; 2026-02-11 stale, 2026-02-12 not stale, null stale; property `P6M` → 2026-05-12.
- [ ] **Step 2: Failing tests** in `CompanySnapshotServiceTest`: snapshot with findings dated 2026-01-10 and 2026-06-01 → `lastResearchedAt` 2026-06-01, snapshot `stale` false, the 2026-01-10 finding `stale` true; no findings → `lastResearchedAt` null, `stale` true. Controller test asserts the JSON fields `lastResearchedAt`, `stale`, `findings[0].stale`.
- [ ] **Step 3: Run** the tests — expect FAIL.
- [ ] **Step 4: Implement**; add `research.stale-after: P9M` to `application.yml`.
- [ ] **Step 5: Run** the research test package, then full `mvn -q test` — expect PASS.
- [ ] **Step 6: Commit (user).** `feat(research): data age — stale flags and last-researched date (default P9M)`

---

### Task 2: Research history in the backend

**Concept:** Before a re-research overwrites a finding, the old values are copied into a separate table that is only ever appended to. The entity reports "this is what I replaced"; the service writes the history row with the origin (local research, live import, manual change).

**Files:**
- Create: `backend/src/main/resources/db/migration/V5__create_research_history.sql`
- Create: `research/HistoryOrigin.java` (enum `INGEST, SYNC, MANUAL`), `research/ResearchFindingHistory.java`, `research/StockGroupHistory.java` (entities), `research/ResearchFindingHistoryRepository.java`, `research/StockGroupHistoryRepository.java`, `research/SnapshotHistoryView.java` (+ nested records), `research/ReplacedFinding.java`
- Modify: `CompanySnapshot.java` (`upsertFinding` return value), `ResearchFinding.java`, `CompanySnapshotService.java`, `ResearchIngestController.java`, `ResearchSyncController.java`, `CompanySnapshotController.java`
- Test: `CompanySnapshotTest` (entity), `CompanySnapshotServiceTest`, `CompanySnapshotControllerTest`, new `ResearchHistoryMigrationTest` (Testcontainers, same style as `StockGroupMigrationTest`)

**Interfaces:**
- Consumes: `ResearchFreshness` (Task 1) — unchanged views.
- Produces:
  - `record ReplacedFinding(ResearchCriterion criterionKey, BigDecimal numericValue, Boolean booleanValue, String claim, String sourceUrl, LocalDate asOfDate)`
  - `CompanySnapshot.upsertFinding(...)` now returns `Optional<ReplacedFinding>`: present only when an existing finding for that criterion had different content (any of the five fields, `BigDecimal` compared with `compareTo`); empty for a new criterion or identical content.
  - `CompanySnapshotService.upsert(UpsertCompanySnapshotRequest request, GroupSource source, HistoryOrigin origin)`; ingest passes `INGEST`, sync `SYNC`. `assignGroupManually` records origin `MANUAL`.
  - A group-history row is written only when the snapshot's group key or source actually changes.
  - `GET /api/research/snapshots/{isin}/history` → `SnapshotHistoryView(List<FindingHistoryEntry> findings, List<GroupHistoryEntry> groups)`, each newest first:
    - `FindingHistoryEntry(ResearchCriterion criterionKey, BigDecimal numericValue, Boolean booleanValue, String claim, String sourceUrl, LocalDate asOfDate, Instant replacedAt, HistoryOrigin origin)`
    - `GroupHistoryEntry(StockGroupRef previousGroup, GroupSource previousSource, StockGroupRef newGroup, GroupSource newSource, Instant changedAt, HistoryOrigin origin)` (`previousGroup` null when there was none)
  - 404 `{"error":"Snapshot not found"}` for an unknown ISIN (existing handler).
- Migration V5 (columns as in spec §8.1): `research_finding_history` and `stock_group_history`, FKs to `company_snapshot(id)` without `ON DELETE CASCADE`, FKs for group keys to `stock_group(key)`, index on `(company_snapshot_id, criterion_key)` and `(company_snapshot_id)`.

- [ ] **Step 1: Failing entity tests** (`CompanySnapshotTest`): new criterion → empty; same values → empty; changed claim → `ReplacedFinding` with the OLD values; `numericValue` `12.50` vs `12.5` → empty (compareTo).
- [ ] **Step 2: Failing service tests** (`CompanySnapshotServiceTest`, mocked repositories, fixed `Clock`):
  - changed finding → one finding-history row with old values, `replacedAt` = clock instant, origin `INGEST`.
  - **Review Focus 1:** identical re-send → no row.
  - group changes AI→AI different key → one group row (origin `INGEST`); manual assignment → row with origin `MANUAL`, `newSource MANUAL`.
  - **Review Focus 2:** AI proposal while MANUAL → no group row; manual re-assign of the same group with source already MANUAL → no row.
  - sync passes origin `SYNC`.
- [ ] **Step 3: Failing controller test**: `GET …/history` returns both lists newest first as JSON for USER and ADMIN; 404 for unknown ISIN; anonymous → 401.
- [ ] **Step 4: Failing migration test** `ResearchHistoryMigrationTest`: tables exist; inserting a history row for a missing snapshot id fails (FK).
- [ ] **Step 5: Run** — expect FAIL. **Step 6: Implement.** Repositories expose only `save`/`saveAll` and the two finder methods `findByCompanySnapshotIdOrderByReplacedAtDescIdDesc` / `findByCompanySnapshotIdOrderByChangedAtDescIdDesc` (extend `Repository`, not `JpaRepository`, so no delete methods exist).
- [ ] **Step 7: Run** the research tests and full `mvn -q test` — expect PASS.
- [ ] **Step 8: Commit (user).** `feat(research): append-only history of replaced findings and group changes`

---

### Task 3: Investment thesis in the backend; docs update

**Concept:** A thesis is its own small aggregate keyed by ISIN, independent of research data. Everyone may read it, only the ADMIN may write it; the buy request can carry a reason that is appended in the same transaction, so a failed append rolls back the purchase too.

**Files:**
- Create: `backend/src/main/resources/db/migration/V6__create_investment_thesis.sql`
- Create in `research`: `ThesisStatus.java` (enum `WATCHING, REJECTED`), `InvestmentThesis.java` (entity), `InvestmentThesisRepository.java`, `InvestmentThesisService.java`, `InvestmentThesisController.java`, `SaveThesisRequest.java`, `InvestmentThesisView.java`, `ThesisNotFoundException.java`, `ThesisTooLongException.java`, `InvestmentThesisExceptionHandler.java`
- Modify: `portfolio/BuyPositionRequest.java` (`@Size(max = 2000) String purchaseReason`, last component, nullable), `portfolio/PortfolioService.java` (calls `InvestmentThesisService.appendPurchaseReason` when the reason is not blank), `portfolio/PortfolioExceptionHandler.java` (422 for `ThesisTooLongException`)
- Modify docs: `docs/superpowers/specs/2026-11-03-app-scope-v2-design.md` §4a (threshold 9 months; USER reads thesis/status with the fixed note; thesis check follows the same rule) and its decision log (entry 2026-11-12); `README.md` visibility table row "Investment-These und Thesis check" → USER ✅ lesen (mit Hinweis), ❌ bearbeiten; README text where it says ADMIN-only visibility.
- Test: `InvestmentThesisTest` (entity), `InvestmentThesisServiceTest`, `InvestmentThesisControllerTest` (`@WebMvcTest`), `InvestmentThesisMigrationTest` (Testcontainers), adapt `PortfolioServiceTest`, `PortfolioControllerTest`

**Interfaces:**
- Produces:
  - `GET /api/research/theses/{isin}` → `InvestmentThesisView(String isin, String whyInteresting, String expectedDevelopment, String exitCriteria, ThesisStatus status, String statusReason, Instant updatedAt, Long version, boolean holding)`; 404 `Thesis not found`.
  - `PUT /api/research/theses/{isin}` body `SaveThesisRequest(String whyInteresting, String expectedDevelopment, String exitCriteria, ThesisStatus status, String statusReason, Long version)` → 200 view. `version` null = create (409 if a thesis already exists); non-null must match (else 409 `Conflict, please reload`). Blank texts are stored as null. `statusReason` without `status` → 400 `{"error":"statusReason requires status"}`.
  - `InvestmentThesisService.appendPurchaseReason(String isin, String reason)` — creates the thesis if missing, appends per Global Constraints, throws `ThesisTooLongException("Thesis text too long")` beyond 4000.
  - `holding` = `PortfolioPositionRepository.findByIsin(isin).isPresent()`.
- Migration V6: table per spec §7.1 with `CHECK (status IN ('WATCHING','REJECTED'))` and `CHECK (status_reason IS NULL OR status IS NOT NULL)`.

- [ ] **Step 1: Failing tests** entity/service: create, replace, version conflict → 409 path, blank → null, `statusReason` without status rejected, append to empty field (no leading blank lines), append to existing text keeps it unchanged and adds `\n\n[2026-11-12] Kauf: …`, append beyond 4000 throws; `holding` true when the ISIN is in the portfolio, and the stored status is still returned.
- [ ] **Step 2: Failing controller tests:** USER GET 200, ADMIN PUT 200, **Review Focus 4:** USER PUT 403 and anonymous PUT 401 with the service never called; lowercase ISIN path normalized; 404 body `{"error":"Thesis not found"}`; 409 body.
- [ ] **Step 3: Failing portfolio tests:** buy with `purchaseReason` calls `appendPurchaseReason` with the normalized ISIN; blank reason → not called; **Review Focus 3:** `PortfolioService.buy` calls `appendPurchaseReason` **before** saving the position (both inside the existing `@Transactional`), so in `PortfolioServiceTest` a thrown `ThesisTooLongException` propagates and `repository.save` is never called; in `PortfolioControllerTest` the exception maps to 422 `{"error":"Thesis text too long"}`.
- [ ] **Step 4: Failing migration test** `InvestmentThesisMigrationTest`: table exists; the two CHECK constraints reject bad rows.
- [ ] **Step 5: Run** — expect FAIL. **Step 6: Implement**, then update the two docs.
- [ ] **Step 7: Run** full `mvn -q test` — expect PASS.
- [ ] **Step 8: Commit (user).** `feat(research): investment thesis per ISIN, readable by all, ADMIN writes; purchase reason appends`

---

### Task 4: Frontend — data age and history

**Concept:** The detail page now loads a second resource (the history) next to the snapshot. Each criterion row gets a small "Verlauf" toggle — local `useState` per row that shows or hides the older values. Stale flags come ready-made from the backend and only change what is displayed.

**Files:**
- Modify: `frontend/src/api/researchApi.ts` (+ test), `frontend/src/pages/ResearchDetailPage.tsx` (+ test), `frontend/src/pages/ResearchOverviewPage.tsx` (+ test)
- Create: `frontend/src/components/FindingHistory.tsx` (+ test)

**Interfaces:**
- Consumes: Task 1 and Task 2 JSON.
- Produces in `researchApi.ts`:
  ```ts
  // Finding gains: stale: boolean
  // CompanySnapshot gains: lastResearchedAt: string | null; stale: boolean
  export type HistoryOrigin = 'INGEST' | 'SYNC' | 'MANUAL'
  export interface FindingHistoryEntry { criterionKey: string; numericValue: number | null; booleanValue: boolean | null; claim: string; sourceUrl: string; asOfDate: string; replacedAt: string; origin: HistoryOrigin }
  export interface GroupHistoryEntry { previousGroup: { key: string; name: string } | null; previousSource: GroupSource | null; newGroup: { key: string; name: string }; newSource: GroupSource; changedAt: string; origin: HistoryOrigin }
  export interface SnapshotHistory { findings: FindingHistoryEntry[]; groups: GroupHistoryEntry[] }
  export function fetchHistory(isin: string): Promise<SnapshotHistory>
  ```
- `FindingHistory({ entries: FindingHistoryEntry[] })` renders nothing for an empty list, otherwise the toggle and the list.

- [ ] **Step 1: Failing tests:**
  - detail header `Zuletzt recherchiert 01.06.2026`; with `stale: true` the suffix ` – Neu-Recherche erwägen`; `lastResearchedAt: null` → `Noch keine Befunde`.
  - a finding with `stale: true` shows `veraltet`; a fresh one does not.
  - criterion with 2 history entries shows `Verlauf (2)`; clicking shows both values with `ersetzt am …`, newest first; a criterion without history has no toggle.
  - `Gruppenverlauf` lists `Halbleiter → Software (manuell) am 03.11.2026`; previous null → `— → …`.
  - history request 500 → page renders, text `Verlauf konnte nicht geladen werden`.
  - overview row of a stale snapshot shows `veraltet`.
- [ ] **Step 2: Run** — expect FAIL. **Step 3: Implement** (reuse the existing value/date formatting of the detail page). **Step 4: Run** `npm test`, `npm run build` — expect PASS.
- [ ] **Step 5: Commit (user).** `feat(frontend): data age markers and research history on the detail page`

---

### Task 5: Frontend — investment thesis and purchase reason

**Concept:** A read-only section for everyone and an edit form that only the ADMIN gets. The form is a "controlled form": every input's value lives in React state, `Speichern` sends the state with the version the page loaded, and a 409 means someone else saved in between.

**Files:**
- Create: `frontend/src/api/thesisApi.ts` (+ test), `frontend/src/components/InvestmentThesisSection.tsx` (+ test)
- Modify: `frontend/src/pages/ResearchDetailPage.tsx` (+ test: section placed after "A. Überblick"), `frontend/src/components/AddPositionForm.tsx` (+ test), `frontend/src/api/portfolioApi.ts` (`buyPosition` input gains optional `purchaseReason?: string`)

**Interfaces:**
- Consumes: Task 3 API, `AuthUser`, `writeErrorText`, `apiFetch`.
- Produces in `thesisApi.ts`:
  ```ts
  export type ThesisStatus = 'WATCHING' | 'REJECTED'
  export interface InvestmentThesis { isin: string; whyInteresting: string | null; expectedDevelopment: string | null; exitCriteria: string | null; status: ThesisStatus | null; statusReason: string | null; updatedAt: string; version: number; holding: boolean }
  export interface SaveThesisInput { whyInteresting: string; expectedDevelopment: string; exitCriteria: string; status: ThesisStatus | null; statusReason: string; version: number | null }
  export function fetchThesis(isin: string): Promise<InvestmentThesis | null>   // 404 → null
  export function saveThesis(isin: string, input: SaveThesisInput): Promise<InvestmentThesis>
  ```
- `InvestmentThesisSection({ user: AuthUser; isin: string })` loads and saves on its own.

- [ ] **Step 1: Failing tests** for `thesisApi` (paths, PUT body, 404 → null) and `InvestmentThesisSection`:
  - USER and ADMIN see the note, the three headings with texts, `Beobachten` + reason, `Aktualisiert am …`; `holding: true` → `Halte ich` instead of the status.
  - no thesis → `Noch keine Investment-These`.
  - USER sees no `These bearbeiten`.
  - ADMIN edit: form prefilled, `Speichern` sends the version; creating sends `version: null`; status select disabled with the hint when `holding`; 409 → `Die These wurde inzwischen geändert, bitte neu laden` and a reload; `Abbrechen` discards changes.
  - **Review Focus 5:** text `<script>alert(1)</script>\nZeile 2` is shown literally, no `<script>` element in the DOM, two lines kept (`white-space: pre-wrap` or split into lines).
- [ ] **Step 2: Failing tests** for `AddPositionForm`: the textarea with the exact label; a filled reason is sent as `purchaseReason`; empty → field omitted; 422 shows `Thesis text too long`.
- [ ] **Step 3: Run** — expect FAIL. **Step 4: Implement.** **Step 5: Run** `npm test`, `npm run build` — expect PASS.
- [ ] **Step 6: Commit (user).** `feat(frontend): investment thesis section and purchase reason in the buy form`

---

## Finish

- [x] User checks manually against `./dev-up.sh` as ADMIN and USER: stale markers (e.g. temporarily set `RESEARCH_STALE_AFTER=P1D` or research an old date), thesis read (USER) and edit (ADMIN), purchase reason appended, history after re-researching a stock with changed values and after a manual group change.
- [x] Final whole-branch review over phase 2a + 2b, `PROJECT-STATUS.md` (phase 2 done), then finish the branch (superpowers:finishing-a-development-branch; git by the user).
