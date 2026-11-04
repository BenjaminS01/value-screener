# Phase 1 Backend: Login, Roles, Research Ingest and Stock Groups — Design

**Status: agreed with the user (2026-11-04). Not implemented yet.** This document refines
[`2026-11-03-app-scope-v2-design.md`](2026-11-03-app-scope-v2-design.md) (top-level source of truth)
for the **backend** work of phase 1, and adds two topics the scope v2 spec did not cover: how
web-research data gets from the local research session into the live system, and stock groups. Where
this document is more specific than scope v2, this document wins. German summary at the end.

Calls marked **(builder's call)** were made by Claude without explicit user input; they are listed in
Section 10 so the user can check them.

## 1. Intent

- Turn the backend into the login-only, two-role application described in scope v2 (Section 2 and 8
  there): one ADMIN, a handful of individual USER accounts, session login, brute-force protection,
  role-dependent portfolio API.
- Keep the web-research workflow (the `research-company` Claude Code skill) working **without** ever
  putting the ADMIN password into the sandbox where Claude reads untrusted web pages. The research
  itself runs **only locally**; the owner pushes checked results to the live system.
- Introduce a maintained list of **stock groups** (finer than the 11 GICS sectors) so the portfolio
  can be grouped and the research area filtered. The AI proposes a group during research; the ADMIN
  can correct it.

## 2. Goals, non-goals, anti-goals

**Goals**
- Everything behind login except the login endpoints (and the frontend's static legal pages).
- Every write endpoint requires ADMIN (or a single-purpose machine token, Section 5) — enforced and
  tested in the backend.
- USER responses structurally never contain quantity, entry price or purchase date.
- The sandbox never holds a credential that works against the live system or grants ADMIN rights.
- Stock groups maintained in the database, assigned per stock, AI-proposed, manually overridable.

**Non-goals (this round)**
- Frontend changes. There is **no live operation yet**; the frontend is adapted afterwards and may be
  broken until then (user decision 2026-11-04).
- AWS changes (SSM parameters, API Gateway throttling, CloudFront routing) — written down here as
  deployment prerequisites (Section 9), done later by the user.
- Usage limits / `usage_counter` (phase 3), reports, chat, deep analysis.
- Spring Boot upgrade (currently 3.3.5) — noted, not done.

**Anti-goals** (would make this a failure even if it works)
- The ADMIN password, or any token valid against the live system, reachable from inside the sandbox.
- The web-research write path existing in the AWS deployment.
- A USER able to see holdings data or to change anything.
- An AI re-research silently overwriting a group the owner set by hand.

## 3. Accounts and login

### 3.1 Account list (configuration, no user table)

- `app.admin.username`, `app.admin.password-hash` (as today) → role `ADMIN`.
- `app.users[n].username`, `app.users[n].password-hash` → role `USER`. Locally via `.env` /
  environment variables (`APP_USERS_0_USERNAME`, `APP_USERS_0_PASSWORD_HASH`, …), in AWS from SSM
  Parameter Store SecureString injected as environment variables into the ECS task.
- **Startup validation** (fail fast): ADMIN hash set; every username non-blank and unique
  (case-insensitive) across ADMIN and USERs; reserved name `*` (global usage cap, scope v2 §8.6)
  rejected; every hash is a BCrypt hash. Password length rules (ADMIN ≥ 20, USER ≥ 16) can't be
  checked from a hash → documented in README next to `AdminPasswordHashGenerator`.
- `AdminPasswordHashGenerator` stays the tool for creating hashes (name kept; README explains it
  works for USER passwords too).

### 3.2 Session login (replaces HTTP Basic)

| Endpoint | Access | Behaviour |
|---|---|---|
| `GET /api/auth/csrf` | public | returns the CSRF token and sets the `XSRF-TOKEN` cookie **(builder's call: SPA fetches it before login)** |
| `POST /api/auth/login` | public, CSRF required | JSON `{username, password}` → `200` with `{username, role}`; failure → `401 {"error":"Invalid username or password"}` |
| `POST /api/auth/logout` | logged in | invalidates the session → `204` |
| `GET /api/auth/me` | logged in | `{username, role}` |

- Session cookie: `HttpOnly`, `SameSite=Strict`, `Secure` (configurable; `false` only in the local
  profile), idle timeout 2 h, new session id after login (Spring default), in-memory sessions.
- CSRF on: `CookieCsrfTokenRepository` (cookie readable by JS) + SPA request handler; every
  state-changing request with a session needs the `X-XSRF-TOKEN` header. Token-authenticated machine
  endpoints (Section 5) are stateless and exempt.
- Unauthenticated API request → `401` JSON, **no** `WWW-Authenticate: Basic` header (no browser
  popup), no redirect. Missing role → `403` JSON. HTTP Basic is removed entirely.
- No new public health endpoint **(builder's call)** — the ECS task health does not depend on an HTTP
  path today.

### 3.3 Brute-force protection (in memory)

- **Per IP:** max 5 login attempts per minute; beyond that `429` with `Retry-After`, the wait growing
  with repeated violations (e.g. 1, 2, 4 … up to 15 min). Implemented as rejection, **not** by sleeping
  the request thread **(builder's call: sleeping threads would be a DoS vector)**.
- **Per account:** 10 consecutive failures → account locked 15 min. During the lock even the correct
  password gets the same generic `401` (no hint that the account exists or is locked). Success resets
  the counter. Only **existing** accounts are tracked, so spraying random usernames cannot grow memory.
- Counter maps bounded and periodically cleaned of expired entries.
- **Client IP:** `app.security.trust-forwarded-for` (default `false` → remote address). When `true`
  the backend uses the address added by API Gateway. Which header/position API Gateway (HTTP API via
  VPC Link) really sends is **verified once AWS runs again** (Section 9) — until then the code keeps
  the extraction in one small, tested class.
- Logging: every success and failure with username, client IP and reason category; never the password.
  Usernames are personal data → covered by the privacy policy (scope v2 §9).

## 4. Authorization and portfolio API

### 4.1 Rules (`SecurityConfig`)

- Remove `permitAll` for `/api/portfolio/public` and `/api/research/snapshots/**`.
- Public: `GET /api/auth/csrf`, `POST /api/auth/login`. Everything else requires a session, except the
  token endpoints of Section 5, which have their own filter chain.
- ADMIN required for: all portfolio writes, group writes, manual group assignment.
- Tests per endpoint: anonymous → `401`, USER on write → `403`, ADMIN → success.

### 4.2 Portfolio endpoints

| Endpoint | ADMIN | USER |
|---|---|---|
| `GET /api/portfolio` | full view: ticker, isin, companyName, group (key + name or null), quantity, entryPrice, purchaseDate | `PortfolioPositionView`: ticker, isin, companyName, group — no holdings fields |
| `POST /api/portfolio` (buy / top up) | ✅ | `403` |
| `POST /api/portfolio/sell` | ✅ | `403` |
| `PUT /api/portfolio/{isin}` (correction) | ✅ ticker, companyName, quantity, entryPrice, purchaseDate; ISIN is **not** changeable (wrong ISIN = delete + new) | `403` |
| `DELETE /api/portfolio/{isin}` | ✅ removes a wrong entry (unlike a full sale it is not a business event) | `403` |

- `GET /api/portfolio/public` is removed.
- The two views are separate record types; a regression test asserts the USER JSON has no
  `quantity`/`entryPrice`/`purchaseDate` keys (same idea as today's `PublicPortfolioPositionView` test).
- **Group in the portfolio** comes from the `CompanySnapshot` with the same ISIN; no snapshot or no
  group → `null` (UI shows "Ungrouped"). No group column on `portfolio_position`.
- `PUT` uses optimistic locking as today (`version`); validation reuses the entity rules.

## 5. Web-research data: local ingest and push to live

```
Sandbox (Claude Code, reads web pages)        Host (owner's machine)                AWS (live)
 research-company skill                         scripts/push-research.sh <ISIN>
   │ GET groups, POST snapshot                    │ 1. GET export (local token)
   │ Bearer RESEARCH_INGEST_TOKEN                 │ 2. POST import (sync token) ───────►  backend
   ▼                                              ▼                                     /api/research/
 local backend (localhost:8080) ◄─────────────────┘                                     sync/snapshots  
   ingest endpoints exist ONLY here                                                     (sync token only)
```

### 5.1 Local ingest (sandbox → local backend)

- Switch `app.research-ingest.enabled` (default `false`; `true` only in the local profile). When
  `false` the endpoints and their filter chain are **not registered** → `404`. When `true` without a
  configured token → startup fails.
- Token `app.research-ingest.token-hash` (SHA-256 of a long random token, compared in constant time
  **(builder's call: high-entropy tokens don't need BCrypt)**). The plaintext is exported only as
  `RESEARCH_INGEST_TOKEN` for the sandbox.
- Endpoints accepted with this token (`Authorization: Bearer …`, stateless, no CSRF):
  - `GET /api/research/ingest/groups` — the group list for the skill
  - `POST /api/research/ingest/snapshots` — upsert as today plus `groupKey` (Section 6)
  - `GET /api/research/ingest/snapshots/{isin}/export` — the snapshot in exactly the import payload
    format, used by the push script
  Separate paths under `/api/research/ingest/**` **(builder's call)**, so one filter chain can be
  switched off as a whole and the session-protected read endpoints stay unchanged.
- Read endpoints for logged-in users stay `GET /api/research/snapshots` and `/{isin}` (both roles).
  The old `POST /api/research/snapshots` is removed.

### 5.2 Live import (host → live backend)

- Switch `app.research-sync.enabled` (default `false`; `true` in AWS) and
  `app.research-sync.token-hash` (SHA-256, hash in SSM; plaintext only on the owner's machine **outside
  the repo**, e.g. `~/.config/value-screener/live.env`, because the repo is mounted into the sandbox).
- `POST /api/research/sync/snapshots` with `Authorization: Bearer <sync token>`; the only right of
  this token. Same payload and same validation as the local ingest; unknown `groupKey` → `422` with
  reason. Every import logged (ISIN, group, number of findings, "via sync token").
- The sync token and the ingest token are different values; the ingest switch is never enabled in AWS.
- API Gateway throttling on the import route (Section 9).

### 5.3 Push script

- `scripts/push-research.sh <ISIN>` (bash + curl, no jq needed): fetches
  `/api/research/ingest/snapshots/{isin}/export` from the local backend with the local token, posts the
  body to `${LIVE_BACKEND_URL}/api/research/sync/snapshots` with the sync token read from the
  out-of-repo file, prints the HTTP status. Never echoes tokens. Refuses to run inside the sandbox
  (checks for the sandbox marker env var set by `run.sh`) **(builder's call)**.
- Pushing all snapshots at once is not required now **(builder's call: one ISIN at a time matches
  "check, then push")**.

### 5.4 Validation hardening (both endpoints)

- `sourceUrl`: only `http`/`https` (today `@URL` also lets `ftp:`/`file:` through; the UI renders it
  as a link).
- `isin`: ISIN format checked on the request (today only `@NotBlank`; the entity checks later).
- Existing limits stay: `claim` ≤ 2000 chars, ≤ 50 findings.

### 5.5 Skill and sandbox changes

- `docker/claude-sandbox/run.sh`: stop passing `ADMIN_USERNAME`/`ADMIN_PASSWORD`; pass
  `RESEARCH_INGEST_TOKEN` and a sandbox marker variable instead.
- **Mount the repo read-only** (`-v "$REPO_ROOT:/workspace:ro"`, user decision 2026-11-04). Reason: a
  prompt-injected session must not be able to modify `scripts/push-research.sh` (which runs on the
  host with the live sync token), `dev-up.sh` or the backend code. The skill only writes over HTTP, so
  it needs no write access. Trade-off accepted: the sandbox is only for research, not for tasks that
  edit repo files. Verify during step 5 that the skill and Claude Code start fine with a read-only
  `/workspace` (session state lives in the separate `/root/.claude` volume).
- `research-company` skill:
  1. fetch the group list from `GET /api/research/ingest/groups`;
  2. research as today and pick exactly one `groupKey` from that list (fallback `DIVERSIFIED_OTHER`);
  3. **show the owner the full payload and wait for explicit OK** before posting (scope v2 §4: "checked
     by the owner");
  4. post to `/api/research/ingest/snapshots` with `Authorization: Bearer $RESEARCH_INGEST_TOKEN`;
  5. report the effective group from the response (it may differ if the owner had set one manually).
- README and `.env.example` updated accordingly.

## 6. Stock groups

### 6.1 Data model (Flyway, next free version number)

`stock_group`: `key` (PK, `UPPER_SNAKE_CASE`, immutable), `name` (display, English, unique),
`sort_order`, `version`.

`company_snapshot` gets `group_key` (FK → `stock_group.key`, nullable) and `group_source`
(`AI` / `MANUAL`, nullable together with `group_key`).

The free-text `sector` column: made nullable, no longer written or returned by the API; dropped in a
later cleanup **(builder's call: no data is deleted now; existing local snapshots keep the old value
in the DB and show as "Ungrouped" until a group is assigned)**. The display "sector" everywhere is now
the group.

Migration numbering: versions are assigned in implementation order; the `V4__create_quarterly_report`
name in the RAG design is a placeholder, not a reservation **(builder's call)**.

### 6.2 Seed list (35 groups, agreed 2026-11-04)

| Area | Groups (key → name) |
|---|---|
| Financials | `BANKS` Banks · `INSURANCE` Insurance · `ASSET_MANAGEMENT_EXCHANGES` Asset Management & Exchanges · `PAYMENTS_FINTECH` Payments & Fintech |
| Energy | `OIL_GAS` Oil & Gas · `RENEWABLE_ENERGY` Renewable Energy · `UTILITIES` Utilities |
| Materials | `CHEMICALS` Chemicals · `MINING_METALS` Mining & Metals · `CONSTRUCTION_BUILDING_MATERIALS` Construction & Building Materials |
| Industrials | `INDUSTRIAL_MACHINERY` Industrial Machinery · `ELECTRICAL_EQUIPMENT_AUTOMATION` Electrical Equipment & Automation · `AEROSPACE_DEFENSE` Aerospace & Defense · `TRANSPORTATION_LOGISTICS` Transportation & Logistics · `AUTOMOTIVE` Automotive & Suppliers · `BUSINESS_SERVICES` Business Services |
| Consumer | `FOOD_BEVERAGES` Food & Beverages · `HOUSEHOLD_PERSONAL_CARE` Household & Personal Care · `LUXURY_APPAREL` Luxury & Apparel · `RETAIL_ECOMMERCE` Retail & E-Commerce · `TRAVEL_HOSPITALITY` Travel, Hotels & Restaurants |
| Health | `PHARMA` Pharma · `BIOTECH` Biotech · `MEDICAL_TECHNOLOGY` Medical Technology · `HEALTH_CARE_SERVICES` Health Care Services |
| Technology | `SEMICONDUCTORS` Semiconductors · `SOFTWARE` Software · `CYBERSECURITY` Cybersecurity · `IT_SERVICES` IT Services · `HARDWARE_ELECTRONICS` Hardware & Electronics |
| Communication | `INTERNET_PLATFORMS` Internet Platforms · `MEDIA_ENTERTAINMENT` Media & Entertainment · `TELECOMMUNICATIONS` Telecommunications |
| Other | `REAL_ESTATE` Real Estate · `DIVERSIFIED_OTHER` Diversified / Other |

`DIVERSIFIED_OTHER` is for conglomerates and the AI's fallback; many stocks there signal that a new
group is needed. Seeded in the same migration; afterwards the list is maintained via the API. The same
migration runs in AWS, so the list starts identical in both systems; groups added later must be
created in both (import rejects unknown keys with a clear message).

### 6.3 Rules

- Exactly **one** group per stock (snapshot). Portfolio positions inherit it via ISIN.
- Ingest/import with `groupKey`: if the stored `group_source` is `MANUAL`, an incoming `AI` group is
  ignored (logged); an incoming `MANUAL` group (import of a group the owner set locally) is applied.
  Otherwise the group is set with source `AI`. The import payload therefore carries `groupKey` **and**
  `groupSource`; the local ingest always sends `AI`.
- `groupKey` is required on ingest/import; must exist.

### 6.4 Endpoints

| Endpoint | Access |
|---|---|
| `GET /api/research/groups` (key, name, sort order, number of stocks) | logged in (both roles) |
| `POST /api/research/groups` `{key, name}` | ADMIN |
| `PATCH /api/research/groups/{key}` `{name, sortOrder}` (key immutable) | ADMIN |
| `DELETE /api/research/groups/{key}` → `409` while stocks are assigned | ADMIN |
| `PUT /api/research/snapshots/{isin}/group` `{groupKey}` → sets source `MANUAL` | ADMIN |
| `GET /api/research/snapshots?group={key}` (filter; `group=none` for ungrouped) | logged in |

Snapshot views gain `group: {key, name, source}` (or `null`) and lose `sector`.

## 7. Testing

TDD throughout. Integration tests with Testcontainers Postgres (existing pattern), MockMvc with
session/CSRF (`spring-security-test`). Must-have tests:
- account list validation (duplicates, reserved `*`, missing ADMIN hash, non-BCrypt hash);
- login success/failure, generic message, session fixation (id changes), logout, `/me`;
- CSRF: state-changing call with session but without token → `403`;
- IP limit → `429`; account lock after 10 failures, correct password still `401` during lock,
  unknown usernames not tracked;
- per endpoint: anonymous `401`, USER write `403`, ADMIN ok; USER portfolio JSON without holdings
  fields;
- ingest switch off → `404`; ingest token rejected on any other endpoint; ingest token rejected by the
  import endpoint and vice versa; sync switch off → `404`;
- `sourceUrl` with `ftp:`/`javascript:` → `400`; malformed ISIN → `400`;
- group rules: AI doesn't overwrite MANUAL; MANUAL import does; unknown key → `422`; delete used
  group → `409`; filter by group and `none`;
- existing `ResearchSnapshotSecurityTest.acceptsUnauthenticatedRead` flips to `401`.

## 8. Implementation order (backend)

1. Account list + startup validation.
2. Session login, CSRF, JSON 401/403, `SecurityConfig` lock-down (incl. removal of Basic Auth).
3. Brute-force protection + login logging.
4. Portfolio: role-dependent `GET`, `PUT`, `DELETE`, removal of `/public`.
5. Research ingest: switch + token + `/api/research/ingest/**`, validation hardening, `run.sh`, skill,
   README/`.env.example`.
6. Stock groups: migration + seed, group API, assignment rules, snapshot view/filter, portfolio group
   field, skill picks a group.
7. Live import + export + push script.

Steps 1–4 are scope v2 phase 1 (backend part); 5–7 come from this document. Each step: concept
explanation first, TDD, the user does all git operations.

## 9. Deployment prerequisites (later, by the user)

- SSM SecureString parameters for the account list and the sync-token hash; injected into the ECS task.
- `app.research-ingest.enabled` never set in AWS; `app.research-sync.enabled=true` there.
- API Gateway throttling on `POST /api/auth/login` and `POST /api/research/sync/snapshots`.
- **Same-site hosting:** CloudFront serves the frontend and forwards `/api/*` to API Gateway, so the
  `SameSite=Strict` session cookie and the CSRF cookie work (frontend and API on different domains
  would break both).
- Verify which client-IP header API Gateway (HTTP API, VPC Link) delivers, then set
  `app.security.trust-forwarded-for` accordingly.

## 10. Calls made by Claude (for the user to check)

1. `GET /api/auth/csrf` as a public endpoint for the SPA.
2. IP limit answered with `429` + growing `Retry-After` instead of delaying requests.
3. Tokens hashed with SHA-256 (constant-time compare) instead of BCrypt.
4. Separate path prefix `/api/research/ingest/**` for the local token endpoints.
5. Push script refuses to run inside the sandbox; one ISIN per run.
6. `sector` column kept nullable for now instead of dropping it.
7. Migration numbers by implementation order.
8. No public health endpoint.
9. New Spring profile `local` (`application-local.yml`), activated by `dev-up.sh`; it switches the
   ingest endpoints on and the `Secure` cookie flag off. The defaults stay safe: `Secure` on, ingest
   and sync off.
10. Disabled ingest/sync paths answer `404` even for anonymous requests. They don't fall through to
    the session chain's `401`.
11. On the local ingest the server always sets `groupSource = AI` and ignores any `groupSource` in the
    body, so the sandbox cannot claim `MANUAL`.
12. `PUT /api/portfolio/{isin}` relies on the entity's `@Version` only; the client sends no version.
13. Usernames are matched case-insensitively. "Number of stocks" in the group list counts snapshots
    with that group. New groups are sorted after the existing ones by default.
14. Live import path is `/api/research/sync/snapshots` instead of `/api/research/snapshots/import`: under
    `/api/research/snapshots/` the existing `GET /{isin}` would answer a disabled import with `405`
    instead of `404`.

Decided with the user (2026-11-04), for reference: no live operation yet, frontend follows later;
research ingest only local; push to live via host script and sync token; skill waits for OK before
posting; proposals for cookie/hosting, sector-from-snapshot (now: group), `PUT` fields, forwarded-IP
switch, JSON 401, accounts via env/SSM, Europe/Berlin day for limits (phase 3), no Spring Boot upgrade;
a maintained group list, one group per stock, AI-proposed and manually overridable, 35 seed groups;
sandbox mounts the repo read-only.

---

## Deutsche Zusammenfassung

**Login und Rollen:** Ein ADMIN und einige USER-Accounts stehen als Liste mit BCrypt-Hash in der
Konfiguration (lokal Umgebungsvariablen, in AWS SSM). Beim Start wird die Liste geprüft. Login per
Session-Cookie statt Basic Auth, mit CSRF-Schutz. Fehlversuche werden pro IP gebremst (`429`), nach
10 Fehlversuchen ist ein Account 15 Minuten gesperrt. USER bekommen im Portfolio nie Stückzahl, Preis
oder Datum; alle Schreibzugriffe nur für ADMIN. Neu: Position korrigieren (`PUT`) und löschen (`DELETE`).

**Webrecherche:** Der Skill schreibt nur noch ins **lokale** Backend, mit einem eigenen Token statt des
ADMIN-Passworts. Diese Endpoints gibt es nur, wenn ein lokaler Schalter sie aktiviert; in AWS liefern
sie `404`. Der Skill zeigt dir die Daten vor dem Absenden und wartet auf dein OK. Ins Live-System
bringst du geprüfte Daten mit `scripts/push-research.sh <ISIN>` von deinem Rechner (nicht aus der
Sandbox) über einen eigenen Sync-Token, der außerhalb des Repo-Ordners liegt. `run.sh` gibt das
ADMIN-Passwort nicht mehr in die Sandbox und bindet das Repo nur lesbar ein, damit eine manipulierte
Sitzung das Push-Skript nicht verändern kann.

**Gruppen:** Eine gepflegte Liste mit 35 Gruppen (z. B. Banks, Semiconductors, Software,
Cybersecurity) in der Datenbank. Jede Aktie hat genau eine Gruppe. Die KI wählt sie bei der Recherche
aus der Liste; du kannst sie ändern, und eine von dir gesetzte Gruppe überschreibt die KI nicht. Das
Portfolio übernimmt die Gruppe über die ISIN, im Research kann man danach filtern. Das alte freie Feld
`sector` wird nicht mehr verwendet.

**Später in AWS:** SSM-Parameter, Drosselung in API Gateway, Frontend und API über CloudFront auf einer
Domain, Prüfung des IP-Headers.
