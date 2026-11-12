# v2 Phase 2a — Frontend for Login, Portfolio and Research — Design

Date: 2026-11-08. Branch: `feature/v2-phase2-frontend`.
Parent spec: [`2026-11-03-app-scope-v2-design.md`](2026-11-03-app-scope-v2-design.md) (Sections 2, 3, 12).
Backend it builds on: [`2026-11-04-v2-phase1-backend-research-groups-design.md`](2026-11-04-v2-phase1-backend-research-groups-design.md)
(merged to `main`, PR #2).

## 1. Intent

Phase 1 delivered the backend for login, roles, portfolio and stock groups, but the frontend still
shows the old public app (landing page, open portfolio, `sector`). Phase 2a connects the frontend to
that backend so the owner can **use the whole app end to end**: log in as ADMIN or USER, see the
portfolio in the role's view, browse the researched stocks grouped by stock group or country, and
open one stock with all its research data.

Phase 2 of the scope spec is split in two (user decision 2026-11-08: order doesn't matter, only that
everything fits together at the end; there is no live system, so frontend and backend may differ
for a while):

- **2a (this document):** frontend only, on the existing phase 1 API. No backend changes, except
  where the builder finds a real gap (Section 9).
- **2b (separate spec later):** data age, investment thesis (ADMIN), research history — new tables,
  endpoints and their display (scope spec Section 4a).

## 2. Goals

1. Login gate: nothing except the login page and the legal pages is reachable without a session.
2. Header and start page after login as in the scope spec.
3. Portfolio page, role-dependent, with all ADMIN actions.
4. Research overview: all researched stocks, grouped by stock group or by country, switchable.
5. Research detail per stock: overview, fundamentals, qualitative findings, portfolio badge, and
   visible placeholders for quarterly reports, AI chat and deep analysis.
6. ADMIN can change a stock's group on the detail page.
7. `sector` disappears from the frontend; the group replaces it.

## 3. Non-goals

- **Visual design.** Plain and functional is enough (user decision 2026-11-08); design comes later
  (login page design is scope phase 8).
- Data age, investment thesis, research history, the "Why are you buying?" field → phase 2b.
- Real quarterly-report upload, chat, deep analysis → scope phases 3–6 (need AWS / Anthropic). Here
  only placeholders.
- Group catalog management UI (create/rename/reorder/delete groups). The API exists and can be used
  directly; a UI can follow later. *(Builder's call taken by Claude: not requested by the user.)*
- Bookmarkable URLs / a router library (see Section 7).
- Project description for showcase visitors on the login page (scope phase 8).

## 4. Anti-goals

- A USER must never see quantity, entry price or purchase date — not even hidden in the DOM or in
  component state. The backend already omits them; the frontend must not request or render ADMIN-only
  data for a USER, and must not show ADMIN action buttons to a USER.
- No password, token or session id in `localStorage`, `sessionStorage`, logs or URLs. The session is
  the `HttpOnly` cookie only.
- No buy/sell wording that reads as investment advice; the existing disclaimer stays in the footer.

## 5. Constraints

- Stack stays as it is: React 18 + Vite + TypeScript, Vitest + Testing Library. **No new npm
  dependencies** (Claude's call; keeps it simple for a React beginner and avoids installing tools).
- Dev setup: Vite proxies `/api` to `http://localhost:8080` (already configured), so cookies are
  same-origin locally.
- Working mode as before: concept explanation before each task (the user is new to React — explain
  frontend concepts, not just the diff), TDD, subagent-driven, the user does all git operations.
- Frontend language: UI texts German (as the existing pages); code and identifiers English.

## 6. Backend contract (existing, phase 1)

All under `/api`, JSON, errors as `{"error":"…"}`.

| Purpose | Request | Notes |
|---|---|---|
| CSRF token | `GET /api/auth/csrf` | sets cookie `XSRF-TOKEN`, returns `{headerName, token}` |
| Login | `POST /api/auth/login` `{username,password}` | 200 `{username, role}`; 401 `Invalid username or password`; 429 `Too many login attempts` + `Retry-After` (seconds); 400 on malformed body |
| Logout | `POST /api/auth/logout` | 204 |
| Current user | `GET /api/auth/me` | 200 `{username, role}` or 401 `Authentication required` |
| Portfolio | `GET /api/portfolio` | ADMIN: `{ticker, isin, companyName, group, quantity, entryPrice, purchaseDate}`; USER: `{ticker, isin, companyName, group}`; `group` = `{key, name}` or null |
| Buy (add/top up) | `POST /api/portfolio` | ADMIN only |
| Sell | `POST /api/portfolio/sell` | ADMIN only |
| Correct | `PUT /api/portfolio/{isin}` | ADMIN only; 409 on concurrent change |
| Delete | `DELETE /api/portfolio/{isin}` | ADMIN only |
| Snapshots | `GET /api/research/snapshots[?group=KEY]` | list of `CompanySnapshotView` |
| One snapshot | `GET /api/research/snapshots/{isin}` | 404 if not researched |
| Assign group | `PUT /api/research/snapshots/{isin}/group` `{groupKey}` | ADMIN only; sets source `MANUAL` |
| Groups | `GET /api/research/groups` | `{key, name, sortOrder, stockCount}`, sorted |

`CompanySnapshotView`: `id, ticker, isin, companyName, country, businessDescription,
group {key, name, source AI|MANUAL} | null, findings[]`; each finding: `criterionKey, numericValue,
booleanValue, claim, sourceUrl, asOfDate`.

Exact request bodies for buy/sell/correct: see `BuyPositionRequest`, `SellPositionRequest`,
`CorrectPositionRequest` in `backend/src/main/java/com/valuescreener/portfolio/`.

**CSRF rule:** every state-changing request (`POST`/`PUT`/`PATCH`/`DELETE`) sends header
`X-XSRF-TOKEN` with the current value of the `XSRF-TOKEN` cookie. The token rotates on login and
logout, so the client re-reads the cookie before each write (or calls `GET /api/auth/csrf` first
when the cookie is missing). Requests use `credentials: 'same-origin'`.

## 7. Decided parts of the how

### 7.1 App shell and navigation

- On start the app calls `GET /api/auth/me`.
  - 200 → logged-in shell. 401 → login page.
- Navigation stays a simple view state in `App.tsx` (no router library). A view can carry a
  parameter (e.g. the ISIN for the research detail), so "click a position → open it in Research"
  works. Consequence accepted: no bookmarkable URLs, browser back button does not navigate inside
  the app.
- **Any 401 from any API call** while logged in → back to the login page with the note
  "Sitzung abgelaufen, bitte erneut anmelden". A 403 shows "Keine Berechtigung" in place.
- Logged-in shell (scope spec Section 3):
  `Header: Value Screener | Portfolio | Research | <username> (<role>) | Logout`
  `Footer: disclaimer · Impressum · Datenschutz`
- Start view after login: title "Unsere Anwendung", otherwise empty.
- The old `LandingPage` stays in the code but is not reachable (scope spec Section 3).

### 7.2 Login page (functional only)

- Username, password, "Anmelden" button, error line, links Impressum / Datenschutz.
- Flow: ensure CSRF cookie (`GET /api/auth/csrf`) → `POST /api/auth/login` → on 200 enter the shell
  with the returned user.
- Messages: 401 → "Benutzername oder Passwort falsch"; 429 → "Zu viele Anmeldeversuche, bitte in
  N Minuten erneut versuchen" (N from `Retry-After`, rounded up); other errors → generic message.
- Password field is cleared after a failed attempt. Button disabled while the request runs.

### 7.3 Legal pages

- Impressum (exists) and Datenschutz are reachable **without login** from the login page and from the
  footer.
- Datenschutz page is new; it gets a short placeholder text marked as "Entwurf" that the owner
  replaces before any go-live. *(Claude's call: the real legal text is the owner's.)*

### 7.4 Portfolio page

- Table columns for both roles: company name, ticker, group name ("—" if none).
- ADMIN additionally: quantity, average entry price, purchase date; and actions:
  - **Buy** (add or top up) — form with the fields of `BuyPositionRequest`.
  - **Sell** (partial or full) — per row.
  - **Correct** — per row, edit the correctable fields of `CorrectPositionRequest`.
  - **Delete** — per row, with a confirmation.
  - After each action the list reloads. 409 → "Die Position wurde inzwischen geändert, bitte neu
    laden". Backend `{"error"}` messages are shown otherwise.
- USER: read-only table, no extra columns, no buttons. Render from the role, not from which fields
  happen to be present.
- Click on a position:
  - if a snapshot exists for the ISIN → open Research detail of that stock;
  - otherwise show "Noch nicht recherchiert" (no navigation).
  - How "exists" is determined is the builder's call (e.g. one snapshot list request).
- The existing per-position expandable research (`PositionResearch`) is removed from the portfolio
  (replaced by the link to Research, scope spec Section 3.2).

### 7.5 Research overview

- Lists all snapshots (`GET /api/research/snapshots`).
- **Grouping switch:** "nach Gruppe" (default) | "nach Land".
  - By group: sections in the catalog order of `GET /api/research/groups` (only groups with stocks),
    then a section "Ohne Gruppe" for snapshots with `group = null`.
  - By country: sections by the snapshot's `country` value, alphabetical; empty or missing country →
    "Ohne Land". Grouping by country is done in the frontend only (no backend change; the user asked
    for it only if cheap — this is cheap).
- Each row: company name, ticker, ISIN, group (or country, whichever is not the grouping), and a
  marker "im Portfolio" if the ISIN is in the portfolio. Click → research detail.
- A text search field filtering by name, ticker or ISIN (scope spec: "searchable"). *(Claude's call:
  simple client-side filter.)*

### 7.6 Research detail

Layout follows scope spec Section 3.2, sections A–F, top to bottom:

- Header: company name, ticker, ISIN, back link to the overview. Badge **"Der Betreiber hält diese
  Position"** if the ISIN is in the portfolio (conflict-of-interest note, visible to all roles).
- **A. Company overview:** name, ticker, ISIN, group (with source "KI-Vorschlag" or "manuell"),
  country, business description.
  - ADMIN: a group selector (all catalog groups) + "Speichern" → `PUT …/group`; afterwards the
    source shows "manuell". USER: group as text only.
- **B. Fundamentals:** the numeric/boolean criteria
  (`PE_RATIO, PB_RATIO, FIVE_YEAR_AVERAGE_PE, FIVE_YEAR_AVERAGE_PB, ROE, DEBT_TO_EQUITY,
  CURRENT_RATIO, CURRENT_YEAR_NET_MARGIN, CURRENT_YEAR_FCF_POSITIVE, CURRENT_YEAR_NET_INCOME_GREW,
  INSIDER_OWNERSHIP_SHARE`), each as a simple tile or row: German label, value, as-of date, source
  link.
- **C. Qualitative findings:** `MARGIN_TREND, FREE_CASH_FLOW_TREND, PROFIT_STABILITY,
  INTEREST_COVERAGE, MOAT_ASSESSMENT, MANAGEMENT_QUALITY, VALUE_TRAP_ASSESSMENT` — claim text,
  as-of date, source link.
- Criteria without a finding are shown as **"nicht recherchiert"**, never hidden.
- Source links open in a new tab with `rel="noopener noreferrer"`; only `http`/`https` URLs are
  rendered as links.
- **D. Quarterly reports — placeholder:** heading, text "Folgt in einer späteren Phase". ADMIN and
  USER see an **Upload** button that is disabled, with a tooltip "Noch nicht verfügbar". *(Scope spec
  says USER sees it greyed out; in this phase it is disabled for everyone.)*
- **E. AI chat — placeholder:** heading, a disabled "Quartalsberichte zusammenfassen" button and a
  disabled input, text "Folgt in einer späteren Phase".
- **F. Deep analysis — placeholder:** the existing `DeepResearchPlaceholder` component, reused or
  adapted.
- 404 for an ISIN → "Für diese Aktie gibt es noch keine Recherche".

## 8. What's left to the builder

- Component and file structure, how API calls are wrapped (one small `apiClient` with CSRF and 401
  handling is expected), state handling, test granularity.
- German labels for the criteria and number formatting (`de-DE`; percentages where the criterion is
  a ratio in percent — check how values are stored in the research skill output).
- Whether the existing `ResearchLibraryPage`, `PositionResearch`, `AddPositionForm`, `LoginForm`
  are adapted or replaced; remove what becomes dead code (except `LandingPage`, which stays).
- Order of tasks.

## 9. Possible backend gaps

The builder checks these against the code and, only if real, adds a small backend task:

- Whether the portfolio "Buy" request needs fields the old frontend form didn't send (and vice
  versa).
- Whether the frontend needs anything not in Section 6 (for example a portfolio "ISINs only"
  endpoint). Default: no new endpoints; derive in the frontend.

## 10. Done when

- All goals in Section 2 work against the local backend (`./dev-up.sh`) for an ADMIN and a USER
  account; the user checks this manually.
- `npm test` and `npm run build` are green; backend tests unchanged and green.
- No `sector` left in the frontend.

## Deutsche Zusammenfassung

Phase 2 ist zweigeteilt. **2a (dieses Dokument)** schließt das Frontend an das fertige
Phase-1-Backend an, ohne Backend-Änderungen. **2b** bringt später Daten-Alter, Investment-These und
Recherche-Historie.

**Was 2a enthält:**
- Login-Sperre: Ohne Anmeldung sind nur die Login-Seite, Impressum und Datenschutz erreichbar.
- Header mit Portfolio, Research, Name und Rolle und Logout; nach dem Login eine Startseite „Unsere
  Anwendung“.
- Portfolio je nach Rolle: Der ADMIN sieht zusätzlich Stückzahl, Preis und Datum und kann kaufen,
  verkaufen, korrigieren und löschen.
- Research-Übersicht, umschaltbar gruppiert nach Gruppe oder nach Land, mit Suchfeld.
- Research-Detail mit Überblick, Kennzahlen und qualitativen Befunden. Fehlende Kriterien erscheinen
  als „nicht recherchiert“, dazu der Hinweis „Der Betreiber hält diese Position“. Der ADMIN kann die
  Gruppe ändern.
- Platzhalter für Quartalsberichte (Upload-Button deaktiviert), KI-Chat und Tiefenanalyse.

**Technik:** React, Vite und TypeScript ohne neue Bibliotheken; Navigation per Zustand ohne Router,
also keine Lesezeichen-Adressen. Aussehen schlicht und funktional.

**Nicht in 2a:** Design, Verwaltung des Gruppenkatalogs in der Oberfläche, echter Upload, Chat und
Tiefenanalyse.
