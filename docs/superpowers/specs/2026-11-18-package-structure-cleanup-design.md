# Design: package structure cleanup and prototype marking

Date: 2026-11-18. Status: short design (small change), approved by the user 2026-11-18.

## Intent and why

The user finds the project hard to navigate. Two backend packages mix several business topics and every
technical layer: `research` (66 classes: snapshots/findings, history, stock groups, investment theses,
ingest/sync) and `security` (31 classes: login, sessions, login throttling, machine tokens, CloudFront edge).
The project follows DDD loosely; the goal is a clear split by business topic without DDD ceremony.
Separately, the discarded `company-research-agent/` must stay in the repo (it may come back, possibly rebuilt
with Quarkus) but must be recognisable as an unused prototype at first glance.

## Goals

1. Backend packages split by business topic (variant A, chosen by the user): no package above ~20 classes.
2. `company-research-agent/` moved to `prototypes/company-research-agent/` with a `prototypes/README.md`.
3. No behaviour change: same endpoints, status codes, JSON, configuration keys, DynamoDB item layout.

## Non-goals

- No layer subpackages (`domain/`, `application/`, `infrastructure/`, `api/`) and no separate `dynamo/`
  subpackages (variants B and C, rejected: many tiny packages, forced `public`, little gain at ~20 classes).
- No renaming of classes, no refactoring of logic, no frontend change.
- No changes inside the prototype (its Java packages stay as they are).
- Historical plans/specs keep their old paths and package names.

## Approaches considered

| Variant | Trade-off | Outcome |
|---|---|---|
| **A. Business-topic subpackages, flat inside** | solves the overloaded packages; follows DDD's bounded-context idea; few visibility changes; domain and DynamoDB classes still side by side | **chosen by the user** |
| B. Topic packages plus layers (domain/application/infrastructure/api) | strict separation, but ~40 packages with 2–3 classes each, deep paths, many classes forced `public` | rejected |
| C. Like A plus a `dynamo/` subpackage per topic | domain visible at a glance, but item classes become `public` and ~6 extra packages for little gain | rejected (can be applied later where a package grows again) |

## Target structure (backend `com.valuescreener`)

```
portfolio/                 unchanged
thesis/                    investment thesis (moved out of research: used by portfolio and research)
research/snapshot/         company snapshots, findings, criteria, freshness, snapshot index + backfill,
                           snapshot controller/service/views/requests, GroupSource
research/history/          finding history + stock group history (append-only), HistoryOrigin, HistoryQuery
research/group/            stock groups: domain, controller, service, repository, items, seed, seeder, lookup
research/ingest/           ResearchIngestController, ResearchSyncController
security/auth/             login/logout controller, accounts, roles, CSRF, SecurityConfig, JSON error handlers,
                           login audit, admin password hash generator
security/session/          DynamoSession, DynamoSessionRepository, SessionConfig, SerializationSupport
security/throttle/         LoginAttemptGuard, LoginAttemptStore (+ Dynamo impl), interceptor, web config
security/machinetoken/     machine token filter/properties/security config, TokenHasher, generator,
                           IngestDisabledInAwsCheck
security/edge/             OriginVerifyFilter, ClientIpResolver
deepanalysis/ limits/ persistence/ lambda/ web/   unchanged
ClockConfig                → root package next to ValueScreenerApplication (it is not security-specific)
```

Tests move in parallel to the same packages.

## Constraints

- Pure moves: only `package`/`import` lines and, where a class is now used across packages, the minimal
  visibility change (package-private → `public`). Record each such change in the plan/report.
- Full backend suite stays green; frontend untouched.
- `SerializationSupport` allow-list (`com.valuescreener.**`) already covers the new packages. Sessions stored
  before the move refer to old class names and will no longer deserialize → treated as absent → users log in
  again once. Accepted (local only; nothing live yet).
- Spring component scanning from the root package covers all subpackages; no explicit package strings exist in
  configuration.
- The user does all git operations. File moves are plain file moves; git detects renames.

## Prototype marking

- `company-research-agent/` → `prototypes/company-research-agent/` (unchanged content).
- `prototypes/README.md` (English + German summary): what is in there, that prototypes are not built, tested or
  deployed by CI or scripts, and per prototype one line: what it was, why it was set aside (design and decision
  log in `docs/superpowers/specs/2026-07-24-company-research-agent-design.md`), possible future (rebuild, e.g.
  with Quarkus).
- Update current path references: `README.md` (component table ~line 486), `PROJECT-STATUS.md` (new short
  section for this cleanup).
- `BASTI.md` (an old copy of README content) is deleted (user decision, 2026-11-18); nothing references it. Mentions of the name in
  `.claude/skills/research-company/SKILL.md` refer to the agent's name, not the path, and stay.

## Left to the builder

- Exact assignment of borderline classes (e.g. `StockGroupRef`, `SnapshotGroupView`, `ReplacedFinding`) to the
  topic that uses them most, keeping cross-package dependencies small; documented in the report.
- Order of moves (one topic at a time keeps each step compilable).

## Deutsche Kurzfassung

Aufräumen der Paketstruktur nach Variante A (vom Nutzer gewählt): `research` (66 Klassen) wird in
`snapshot`, `history`, `group` und `ingest` aufgeteilt, die Investment-These wird ein eigenes Paket `thesis`,
und `security` (31 Klassen) wird in `auth`, `session`, `throttle`, `machinetoken` und `edge` aufgeteilt.
Innerhalb der Pakete bleibt alles flach, ohne Schichten-Unterpakete. Es werden nur Klassen verschoben, das
Verhalten ändert sich nicht. Der verworfene `company-research-agent/` wandert unverändert nach
`prototypes/company-research-agent/` und bekommt eine `prototypes/README.md`, damit er sofort als
ungenutzter Prototyp erkennbar ist.
