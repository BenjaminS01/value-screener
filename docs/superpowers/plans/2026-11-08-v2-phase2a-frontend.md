# v2 Phase 2a — Frontend for Login, Portfolio and Research — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Connect the React frontend to the phase 1 backend: session login, role-dependent portfolio, research overview grouped by group or country, research detail with placeholders.

**Architecture:** One small `apiClient` wraps `fetch` (CSRF header, JSON errors, global 401 handling). `App.tsx` holds the logged-in user and a view state (no router). Pages consume typed API modules (`authApi`, `portfolioApi`, `researchApi`).

**Tech Stack:** React 18, TypeScript, Vite 5, Vitest + Testing Library (jsdom). No new dependencies.

**Spec:** [`docs/superpowers/specs/2026-11-08-v2-phase2a-frontend-design.md`](../specs/2026-11-08-v2-phase2a-frontend-design.md)

## Global Constraints

- **No new npm dependencies.** No router library; navigation is view state in `App.tsx`.
- **The user does all git operations.** "Commit" steps mean: stop, show `git status` / diff summary (`git diff --ignore-cr-at-eol --stat`), propose a short commit message with the line `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. Never run state-changing git.
- Working mode: before each task give the user the task's **Concept** line in plain words (the user is new to React: explain the frontend concept, not only the diff). TDD: red → green → refactor.
- Tests: `cd frontend && npm test` (Vitest, jsdom). Build check: `cd frontend && npm run build`. Mock the network with `vi.stubGlobal('fetch', vi.fn(...))`; never call a real backend in tests.
- UI texts German, code identifiers English. Plain and functional markup; no styling work.
- Requests use `credentials: 'same-origin'`. Every `POST`/`PUT`/`PATCH`/`DELETE` sends header `X-XSRF-TOKEN` = current value of cookie `XSRF-TOKEN`; if the cookie is missing, call `GET /api/auth/csrf` first.
- Never store passwords, tokens or session ids in `localStorage`, `sessionStorage`, URLs or console output.
- A USER must never see quantity, entry price, purchase date or ADMIN action buttons. Render from `user.role`, not from which fields exist.
- Error bodies are `{"error":"<message>"}`. Fixed backend messages: 401 `Authentication required` / `Invalid username or password`, 403 `Forbidden`, 429 `Too many login attempts` + `Retry-After` (seconds).
- Exact German UI copy (use verbatim):
  - login errors: 401 → `Benutzername oder Passwort falsch`; 429 → `Zu viele Anmeldeversuche, bitte in {N} Minuten erneut versuchen` (N = ceil(Retry-After / 60), minimum 1); 429 without `Retry-After` → `Zu viele Anmeldeversuche, bitte später erneut versuchen`; anything else → `Anmeldung fehlgeschlagen`
  - session expired note on the login page: `Sitzung abgelaufen, bitte erneut anmelden`
  - 403 anywhere: `Keine Berechtigung`
  - 409 in portfolio: `Die Position wurde inzwischen geändert, bitte neu laden`
  - start view title: `Unsere Anwendung`
  - not researched (portfolio click): `Noch nicht recherchiert`; missing criterion: `nicht recherchiert`; detail 404: `Für diese Aktie gibt es noch keine Recherche`
  - portfolio badge on detail: `Der Betreiber hält diese Position`; overview marker: `im Portfolio`
  - grouping switch labels: `nach Gruppe` / `nach Land`; section names `Ohne Gruppe` / `Ohne Land`
  - group source: `AI` → `KI-Vorschlag`, `MANUAL` → `manuell`
  - placeholders: `Folgt in einer späteren Phase`; disabled upload button `Upload` with `title="Noch nicht verfügbar"`; disabled chat button `Quartalsberichte zusammenfassen`

## Review Focus

1. **Session expires while using the app** (any API call returns 401 after login) → exactly one switch to the login page with `Sitzung abgelaufen, bitte erneut anmelden`; no loop, no stale data shown. Test in Task 2.
2. **USER account with a response that (wrongly) contains holdings fields** → still no quantity/price/date columns and no action buttons. Test in Task 3.
3. **429 on login** with `Retry-After: 61` → `… in 2 Minuten …`; without header → the "später" text. Test in Task 2.
4. **`sourceUrl` that is not http/https** (e.g. `javascript:alert(1)`) → rendered as plain text, never as a link. Test in Task 5.
5. **Snapshot with `group: null` or empty `country`** → listed under `Ohne Gruppe` / `Ohne Land`, never dropped. Test in Task 4.

---

## Shared types (defined in the tasks named, listed here for orientation)

```ts
// src/api/apiClient.ts (Task 1)
export class ApiError extends Error { status: number; retryAfterSeconds: number | null }
export function apiFetch<T>(path: string, options?: { method?: 'GET'|'POST'|'PUT'|'PATCH'|'DELETE'; body?: unknown; handleUnauthorized?: boolean }): Promise<T>
export function setUnauthorizedHandler(handler: (() => void) | null): void

// src/api/authApi.ts (Task 1)
export type Role = 'ADMIN' | 'USER'
export interface AuthUser { username: string; role: Role }

// src/navigation.ts (Task 2)
export type View =
  | { name: 'home' } | { name: 'portfolio' } | { name: 'research' }
  | { name: 'researchDetail'; isin: string }
  | { name: 'impressum' } | { name: 'datenschutz' }
export type Navigate = (view: View) => void
```

---

### Task 1: API client and auth API

**Concept:** All talking to the backend goes through one small helper. It sends the session cookie, adds the CSRF header to every write, turns `{"error"}` bodies into one error type, and reports an expired session to the app in one place instead of in every page.

**Files:**
- Create: `frontend/src/api/apiClient.ts`, `frontend/src/api/apiClient.test.ts`
- Create: `frontend/src/api/authApi.ts`, `frontend/src/api/authApi.test.ts`

**Interfaces:**
- Produces: `ApiError`, `apiFetch`, `setUnauthorizedHandler` (signatures above); `authApi.ts`: `fetchCurrentUser(): Promise<AuthUser | null>` (401 → `null`, does not trigger the unauthorized handler), `login(username: string, password: string): Promise<AuthUser>`, `logout(): Promise<void>`, types `Role`, `AuthUser`.

- [ ] **Step 1: Write failing tests** in `apiClient.test.ts`:
  - `sends credentials same-origin and JSON body`: `apiFetch('/api/x', {method:'POST', body:{a:1}})` → fetch called with `credentials: 'same-origin'`, header `Content-Type: application/json`, body `'{"a":1}'`.
  - `adds X-XSRF-TOKEN from the cookie on writes`: `document.cookie = 'XSRF-TOKEN=abc'` → POST carries header `X-XSRF-TOKEN: abc`; a GET carries no such header.
  - `fetches the csrf endpoint first when the cookie is missing`: cookie cleared; fetch mock sets the cookie when `/api/auth/csrf` is called → first call `/api/auth/csrf`, second call the POST with the new token.
  - `throws ApiError with the backend error message`: response 409 `{"error":"Conflict, please reload"}` → `ApiError` with `status 409`, `message 'Conflict, please reload'`.
  - `reads Retry-After`: 429 with header `Retry-After: 61` → `retryAfterSeconds === 61`; without header → `null`.
  - `calls the unauthorized handler on 401`: handler registered → called once, and `ApiError(401)` still thrown; with `handleUnauthorized: false` → handler not called.
  - `returns undefined for 204`.
- [ ] **Step 2: Run** `cd frontend && npx vitest run src/api/apiClient.test.ts` — expect FAIL (module missing).
- [ ] **Step 3: Implement `apiClient.ts`.** Non-JSON or empty error bodies fall back to `message = 'HTTP <status>'`.
- [ ] **Step 4: Write failing tests** in `authApi.test.ts`: `fetchCurrentUser` returns the user on 200 and `null` on 401 without calling the unauthorized handler; `login` posts `{username,password}` to `/api/auth/login` with `handleUnauthorized: false` and returns the user; `login` rejects with `ApiError(401)` on wrong password; `logout` posts to `/api/auth/logout`.
- [ ] **Step 5: Implement `authApi.ts`**, run both test files — expect PASS.
- [ ] **Step 6: Commit (user).** Message: `feat(frontend): api client with csrf and error handling; auth api`

---

### Task 2: Login gate, app shell, legal pages

**Concept:** The app first asks the backend "who am I?". Without a session it shows only the login page (plus Impressum/Datenschutz). With a session it shows the header, the pages and the footer. The navigation is a piece of React state that says which page is visible.

**Files:**
- Create: `frontend/src/navigation.ts`
- Create: `frontend/src/pages/LoginPage.tsx`, `frontend/src/pages/LoginPage.test.tsx`
- Create: `frontend/src/pages/DatenschutzPage.tsx`
- Create: `frontend/src/pages/StartPage.tsx`
- Modify: `frontend/src/App.tsx`, `frontend/src/App.test.tsx` (rewrite tests), `frontend/src/components/Footer.tsx` (+ its test: add links)
- Delete: `frontend/src/components/LoginForm.tsx`

**Interfaces:**
- Consumes: Task 1.
- Produces: `View`, `Navigate` in `navigation.ts`. Page props used by later tasks: `PortfolioPage({ user: AuthUser; navigate: Navigate })`, `ResearchOverviewPage({ user, navigate })`, `ResearchDetailPage({ user, isin: string; navigate })`. Until Tasks 3–5 exist, `App.tsx` renders the existing `PortfolioPage`/`ResearchLibraryPage` or a stub for the detail view — the later tasks replace them.
- `Footer({ navigate: Navigate })` renders the disclaimer plus buttons `Impressum` and `Datenschutz`.
- `LoginPage({ onLoggedIn: (user: AuthUser) => void; navigate: Navigate; notice?: string })`.

- [ ] **Step 1: Write failing tests** in `App.test.tsx` (fetch mocked per test):
  - `shows the login page when /api/auth/me returns 401` (heading `Anmelden`, no `Portfolio` button).
  - `shows header and start page when logged in` — `/me` 200 `{username:'ben', role:'ADMIN'}` → heading `Unsere Anwendung`, text `ben (ADMIN)`, buttons `Portfolio`, `Research`, `Abmelden`.
  - `logout returns to the login page` — click `Abmelden` → POST `/api/auth/logout` → heading `Anmelden`.
  - `Impressum and Datenschutz are reachable without login` — from the login page.
  - **Review Focus 1:** `a 401 from any later call returns to login with the session note` — logged in, then a page call returns 401 → login page shows `Sitzung abgelaufen, bitte erneut anmelden`; the 401 handler fires once (assert `/api/auth/me` not called again in a loop).
- [ ] **Step 2: Write failing tests** in `LoginPage.test.tsx`:
  - success calls `onLoggedIn` with the returned user.
  - 401 → `Benutzername oder Passwort falsch`, password field emptied, username kept.
  - **Review Focus 3:** 429 with `Retry-After: 61` → `Zu viele Anmeldeversuche, bitte in 2 Minuten erneut versuchen`; 429 without header → `Zu viele Anmeldeversuche, bitte später erneut versuchen`.
  - button `Anmelden` disabled while the request is pending.
  - `notice` prop is shown.
- [ ] **Step 3: Run** `npx vitest run src/App.test.tsx src/pages/LoginPage.test.tsx` — expect FAIL.
- [ ] **Step 4: Implement** `navigation.ts`, `LoginPage`, `StartPage` (only the `<h1>Unsere Anwendung</h1>`), `DatenschutzPage` (heading `Datenschutz`, a paragraph marked `Entwurf` stating that the final text follows before go-live), Footer links, and `App.tsx`: on mount `fetchCurrentUser()`; while loading render nothing but a short `Lade …`; register `setUnauthorizedHandler` that clears the user and sets the session notice; header `Value Screener | Portfolio | Research | <username> (<role>) | Abmelden`. `LandingPage` stays in the code, unreachable.
- [ ] **Step 5: Run** `npm test` — expect PASS (fix or delete old tests that asserted the removed public views, e.g. `How it works` heading in `App.test.tsx`; keep `LandingPage.test.tsx`).
- [ ] **Step 6: Commit (user).** Message: `feat(frontend): login gate, app shell with header/start page, public legal pages`

---

### Task 3: Portfolio page by role

**Concept:** The same page renders two different tables depending on the role. The ADMIN gets extra columns and buttons that send write requests; after every change the list is loaded again from the backend, so the screen always shows the stored state.

**Files:**
- Modify: `frontend/src/api/portfolioApi.ts` (rewrite), add `frontend/src/api/portfolioApi.test.ts`
- Modify: `frontend/src/pages/PortfolioPage.tsx` (rewrite), `frontend/src/pages/PortfolioPage.test.tsx` (rewrite)
- Modify: `frontend/src/components/AddPositionForm.tsx` (+ test) → becomes the ADMIN buy form using `buyPosition`
- Create: `frontend/src/components/PositionActions.tsx` (+ test): sell / correct / delete for one row

**Interfaces:**
- Consumes: Task 1, `View`/`Navigate` and page props from Task 2.
- Produces in `portfolioApi.ts`:
  ```ts
  export interface GroupRef { key: string; name: string }
  export interface Position { ticker: string; isin: string; companyName: string; group: GroupRef | null }
  export interface AdminPosition extends Position { quantity: number; entryPrice: number; purchaseDate: string }
  export function fetchPortfolio(): Promise<Position[] | AdminPosition[]>
  export function buyPosition(input: { ticker: string; isin: string; companyName: string; quantity: number; entryPrice: number; purchaseDate: string }): Promise<void>  // POST /api/portfolio
  export function sellPosition(isin: string, quantity: number): Promise<void>                  // POST /api/portfolio/sell {isin, quantity}
  export function correctPosition(isin: string, input: { ticker: string; companyName: string; quantity: number; entryPrice: number; purchaseDate: string }): Promise<void>  // PUT /api/portfolio/{isin}
  export function deletePosition(isin: string): Promise<void>                                 // DELETE /api/portfolio/{isin}
  ```
  `fetchPortfolio` is also used by Tasks 4 and 5 to know which ISINs are held.
- Uses `fetchAllSnapshots()` from `researchApi.ts` (existing; Task 4 changes its type) to decide whether a click opens Research.

- [ ] **Step 1: Write failing tests** for `portfolioApi.ts`: each function calls the right method/path/body (writes go through `apiFetch`, so the CSRF header is present).
- [ ] **Step 2: Write failing tests** in `PortfolioPage.test.tsx`:
  - USER: columns `Unternehmen`, `Ticker`, `Gruppe`; no `Stückzahl`, `Einstandskurs`, `Kaufdatum`; no buttons `Kaufen`, `Verkaufen`, `Korrigieren`, `Löschen`. **Review Focus 2:** the mocked USER response additionally contains `quantity: 5` → `5` is still not rendered.
  - ADMIN: the extra columns with values (`de-DE` formatting) and the action buttons.
  - group `null` → `—`.
  - click on a position whose ISIN has a snapshot → `navigate({name:'researchDetail', isin})`; without snapshot → text `Noch nicht recherchiert`, no navigate.
  - ADMIN delete asks for confirmation (`window.confirm` mocked) and reloads the list after 204.
  - 409 on correct → `Die Position wurde inzwischen geändert, bitte neu laden`.
  - 403 on any write → `Keine Berechtigung`.
- [ ] **Step 3: Write failing tests** for `AddPositionForm` (submit calls `buyPosition` with numbers, button disabled while pending, backend `{"error"}` shown) and `PositionActions` (sell quantity, correct prefilled with current values, delete).
- [ ] **Step 4: Run** the three test files — expect FAIL.
- [ ] **Step 5: Implement.** Remove `PositionResearch` and `DeepResearchPlaceholder` usage from the portfolio; remove the old Basic-auth `Credentials` code.
- [ ] **Step 6: Run** `npm test` — expect PASS.
- [ ] **Step 7: Commit (user).** Message: `feat(frontend): role-dependent portfolio with buy, sell, correct, delete`

---

### Task 4: Research API types and research overview

**Concept:** The overview loads all researched stocks once and sorts them into sections in the browser — either by stock group (in the order of the group catalog) or by country. The search field filters the same list without asking the backend again.

**Files:**
- Modify: `frontend/src/api/researchApi.ts` (+ `researchApi.test.ts`)
- Create: `frontend/src/pages/ResearchOverviewPage.tsx` (+ test)
- Create: `frontend/src/research/grouping.ts` (+ `grouping.test.ts`)
- Modify: `frontend/src/App.tsx` (render `ResearchOverviewPage` for view `research`)

**Interfaces:**
- Consumes: Tasks 1–3 (`fetchPortfolio` for the `im Portfolio` marker).
- Produces in `researchApi.ts` (replaces `sector` with `group`):
  ```ts
  export type GroupSource = 'AI' | 'MANUAL'
  export interface SnapshotGroup { key: string; name: string; source: GroupSource }
  export interface Finding { criterionKey: string; numericValue: number | null; booleanValue: boolean | null; claim: string; sourceUrl: string; asOfDate: string }
  export interface CompanySnapshot { id: number; ticker: string; isin: string; companyName: string; country: string | null; businessDescription: string; group: SnapshotGroup | null; findings: Finding[] }
  export interface StockGroup { key: string; name: string; sortOrder: number; stockCount: number }
  export function fetchAllSnapshots(): Promise<CompanySnapshot[]>
  export function fetchSnapshot(isin: string): Promise<CompanySnapshot | null>   // 404 → null
  export function fetchGroups(): Promise<StockGroup[]>
  export function assignGroup(isin: string, groupKey: string): Promise<CompanySnapshot>  // PUT /api/research/snapshots/{isin}/group {groupKey}
  ```
- Produces in `grouping.ts`:
  ```ts
  export interface Section { title: string; snapshots: CompanySnapshot[] }
  export function groupByStockGroup(snapshots: CompanySnapshot[], groups: StockGroup[]): Section[]
  export function groupByCountry(snapshots: CompanySnapshot[]): Section[]
  export function matchesSearch(snapshot: CompanySnapshot, query: string): boolean
  ```

- [ ] **Step 1: Write failing tests** in `grouping.test.ts`:
  - `groupByStockGroup` orders sections by the catalog order of `groups`, skips groups without snapshots, puts `group: null` into a last section `Ohne Gruppe` (**Review Focus 5**); companies inside a section sorted by `companyName` (`localeCompare('de')`).
  - `groupByCountry` sorts sections alphabetically; `null`, `''` or whitespace country → last section `Ohne Land` (**Review Focus 5**).
  - `matchesSearch` is case-insensitive over name, ticker, ISIN; empty query matches all.
- [ ] **Step 2: Write failing tests** for `researchApi.ts` (`fetchGroups`, `assignGroup` path/body/method, `fetchSnapshot` 404 → `null`).
- [ ] **Step 3: Write failing tests** in `ResearchOverviewPage.test.tsx`: default grouping `nach Gruppe` shows group section headings; switching to `nach Land` shows country headings; the search field narrows the rows; a held ISIN shows `im Portfolio`; clicking a row calls `navigate({name:'researchDetail', isin})`.
- [ ] **Step 4: Run** — expect FAIL. **Step 5: Implement.** **Step 6: Run** `npm test` — expect PASS.
- [ ] **Step 7: Commit (user).** Message: `feat(frontend): research overview grouped by stock group or country, with search`

---

### Task 5: Research detail with placeholders; remove dead code

**Concept:** The detail page shows one stock in fixed sections. Every criterion from the backend's list always appears — with its value, or as "nicht recherchiert" — so gaps are visible. Sections for reports, chat and deep analysis are already on the page but disabled until later phases.

**Files:**
- Create: `frontend/src/pages/ResearchDetailPage.tsx` (+ test)
- Create: `frontend/src/research/criteria.ts` (+ `criteria.test.ts`)
- Modify: `frontend/src/components/DeepResearchPlaceholder.tsx` (+ test) — German texts, button disabled
- Modify: `frontend/src/App.tsx` (render detail for view `researchDetail`)
- Delete: `frontend/src/pages/ResearchLibraryPage.tsx` (+ test), `frontend/src/components/PositionResearch.tsx` (+ test)

**Interfaces:**
- Consumes: Task 4 API, `fetchPortfolio` (Task 3), `Navigate`.
- Produces in `criteria.ts`:
  ```ts
  export const FUNDAMENTAL_CRITERIA: readonly string[]   // order below
  export const QUALITATIVE_CRITERIA: readonly string[]
  export const CRITERION_LABELS: Record<string, string>
  export function isSafeHttpUrl(url: string): boolean    // true only for http: / https: (URL parse)
  ```
  Fundamentals, in this order, with labels: `PE_RATIO` KGV · `PB_RATIO` KBV · `FIVE_YEAR_AVERAGE_PE` KGV-Durchschnitt 5 Jahre · `FIVE_YEAR_AVERAGE_PB` KBV-Durchschnitt 5 Jahre · `ROE` Eigenkapitalrendite · `DEBT_TO_EQUITY` Verschuldungsgrad · `CURRENT_RATIO` Liquidität 3. Grades (Current Ratio) · `CURRENT_YEAR_NET_MARGIN` Nettomarge (aktuelles Jahr) · `CURRENT_YEAR_FCF_POSITIVE` Free Cashflow positiv (aktuelles Jahr) · `CURRENT_YEAR_NET_INCOME_GREW` Gewinn gestiegen (aktuelles Jahr) · `INSIDER_OWNERSHIP_SHARE` Insider-Anteil.
  Qualitative: `MARGIN_TREND` Margenentwicklung · `FREE_CASH_FLOW_TREND` Free-Cashflow-Entwicklung · `PROFIT_STABILITY` Gewinnstabilität · `INTEREST_COVERAGE` Zinsdeckung · `MOAT_ASSESSMENT` Burggraben (Moat) · `MANAGEMENT_QUALITY` Managementqualität · `VALUE_TRAP_ASSESSMENT` Value-Trap-Einschätzung.

- [ ] **Step 1: Write failing tests** in `criteria.test.ts`: both lists cover all 18 backend criteria exactly once; `isSafeHttpUrl` true for `https://x.de`, `http://x.de`; false for `javascript:alert(1)`, `ftp://x`, `''`, `not a url`.
- [ ] **Step 2: Write failing tests** in `ResearchDetailPage.test.tsx`:
  - shows name, ticker, ISIN, country, description, group name with `KI-Vorschlag` / `manuell`.
  - a fundamental with `numericValue 12.5` shows `12,5`, its as-of date and a link to its source (`target="_blank"`, `rel="noopener noreferrer"`); boolean values show `ja` / `nein`; every finding also shows its `claim`.
  - a criterion without finding shows `nicht recherchiert`.
  - **Review Focus 4:** `sourceUrl: 'javascript:alert(1)'` → no `<a>` element, the URL as plain text.
  - held ISIN → `Der Betreiber hält diese Position`; not held → absent.
  - ADMIN: group `<select>` with all catalog groups + `Speichern` → `assignGroup` called, afterwards source shows `manuell`. USER: no select, no `Speichern`.
  - placeholders: disabled button `Upload` with title `Noch nicht verfügbar`, disabled button `Quartalsberichte zusammenfassen`, text `Folgt in einer späteren Phase`, the deep-analysis placeholder.
  - `fetchSnapshot` → `null` → `Für diese Aktie gibt es noch keine Recherche`.
  - back link `Zurück zur Übersicht` → `navigate({name:'research'})`.
- [ ] **Step 3: Run** — expect FAIL. **Step 4: Implement.** Numeric values: `toLocaleString('de-DE', { maximumFractionDigits: 2 })`, shown as stored (no percent conversion); the `claim` text gives the context. Dates: `de-DE` date format.
- [ ] **Step 5: Delete** `ResearchLibraryPage` and `PositionResearch` with their tests; confirm `grep -rn "sector" frontend/src` prints nothing.
- [ ] **Step 6: Run** `npm test` and `npm run build` — expect both PASS.
- [ ] **Step 7: Commit (user).** Message: `feat(frontend): research detail with criteria, group override and placeholders; remove old research views`

---

## Finish

- [ ] User checks manually against `./dev-up.sh` with an ADMIN and a USER account: login/logout, wrong password, portfolio actions (ADMIN) and read-only view (USER), research overview both groupings + search, detail with group change (ADMIN), placeholders, Impressum/Datenschutz without login.
- [ ] Final whole-branch review, then update `PROJECT-STATUS.md` (phase 2a done, 2b next).
