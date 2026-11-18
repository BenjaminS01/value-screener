# Package structure cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Split the overloaded backend packages `research` and `security` by business topic, and mark the discarded `company-research-agent` as a prototype, without changing behaviour.

**Architecture:** Pure moves: classes and their tests move into topic subpackages (variant A, flat inside). Only `package`/`import` lines change, plus the minimal `public` where a class is now used across packages. The prototype moves unchanged to `prototypes/`.

**Tech Stack:** Java 21, Spring Boot 3.3.5, Maven, JUnit 5 (existing suite is the safety net).

**Spec:** [`docs/superpowers/specs/2026-11-18-package-structure-cleanup-design.md`](../specs/2026-11-18-package-structure-cleanup-design.md)

## Global Constraints

- No behaviour change: same endpoints, status codes, JSON, configuration keys, DynamoDB item layout. No class renames, no logic refactoring, frontend untouched.
- Only `package`/`import` changes and minimal visibility changes (package-private → `public`); list every visibility change in the task report.
- No layer subpackages (`domain/`, `application/`, `infrastructure/`, `api/`, `dynamo/`).
- Historical plans/specs (`docs/superpowers/plans|specs` older than 2026-11-18) are not edited.
- Working mode: the **user does all git operations**; implementers move files with plain `mv` (git detects renames) and never run state-changing git. Each task ends with the commit command for the user.
- Environment: `export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64; export PATH=$JAVA_HOME/bin:$PATH`; Maven from `backend/`, in the foreground. Docker rootless for Testcontainers. Review diffs with `git diff --ignore-cr-at-eol -M`.
- Accepted package cycles (existing coupling, not to be "fixed" here): `research.snapshot` ↔ `research.history` (snapshot save writes history items; history uses `ReplacedFinding`/`GroupSource`), `research.snapshot` ↔ `research.group` (`markReferenced`, group counts via snapshot index).

## Review Focus

1. Spring wiring after moves: every `@Component`/`@Configuration`/`@ControllerAdvice` is still found (component scan from `com.valuescreener`), and `@ControllerAdvice` handlers still apply to their controllers (check `basePackages`/`assignableTypes` if any) → full suite + `ValueScreenerApplicationTests` green.
2. Slice tests (`@WebMvcTest(controllers=…)`, `@Import(...)`) still load the same configs and handlers → same assertions pass.
3. Main-class paths in docs/scripts (`MachineTokenGenerator`, `AdminPasswordHashGenerator`) point to the new packages → `grep` finds no old path.
4. Session deserialization allow-list `com.valuescreener.**` still covers moved classes (`Role` etc.) → `DynamoSessionRepositoryTest.securityContextSurvivesRoundTrip` green.
5. Nothing left behind: no `.java` file directly in `research/` or `security/` (main and test), except shared test helpers named in Task 3.

---

### Task 1: Mark the prototype, remove BASTI.md

**Files:**
- Move: `company-research-agent/` → `prototypes/company-research-agent/` (content unchanged; do not move or commit its `target/`)
- Create: `prototypes/README.md`
- Delete: `BASTI.md`
- Modify: `README.md` (component table ~line 486: path `prototypes/company-research-agent/`, status "Prototyp, verworfen – nicht gebaut/deployt")

- [ ] **Step 1:** `grep -rn "company-research-agent" .github scripts dev-up.sh docker-compose.yml backend frontend deep-analysis` → confirm nothing builds or references the module path (expected: no hits).
- [ ] **Step 2:** Move the directory; delete `BASTI.md`.
- [ ] **Step 3:** Write `prototypes/README.md` (English body + "Deutsche Kurzfassung"): purpose of the folder (prototypes kept for reference, not built, tested or deployed by CI or scripts); entry `company-research-agent/` — Spring Boot + LLM agent for company research, set aside 2026-07/11 in favour of the Claude Code skill `research-company` + ingest; may be revived or rebuilt (e.g. with Quarkus); design and decision log: `docs/superpowers/specs/2026-07-24-company-research-agent-design.md`.
- [ ] **Step 4:** Update the README table row. Verify: `grep -rn "company-research-agent/" README.md PROJECT-STATUS.md` shows only the new path in README (PROJECT-STATUS history lines stay).
- [ ] **Step 5: Commit (user)**
```bash
git add -A company-research-agent prototypes BASTI.md README.md docs/superpowers
git commit -m "chore: move company-research-agent to prototypes, remove BASTI.md

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: Split `research` by topic, extract `thesis`

**Files:** all under `backend/src/{main,test}/java/com/valuescreener/`. Main classes move as listed; each test moves with the class it tests.

| Target package | Main classes |
|---|---|
| `thesis` | InvestmentThesis, InvestmentThesisController, InvestmentThesisExceptionHandler, InvestmentThesisRepository, InvestmentThesisService, InvestmentThesisView, DynamoInvestmentThesisRepository, ThesisItem, ThesisNotFoundException, ThesisStatus, ThesisTooLongException, SaveThesisRequest |
| `research.snapshot` | CompanySnapshot, CompanySnapshotController, CompanySnapshotExceptionHandler, CompanySnapshotNotFoundException, CompanySnapshotRepository, CompanySnapshotService, CompanySnapshotView, DynamoCompanySnapshotRepository, SnapshotItem, FindingRequest, FindingView, ResearchCriterion, ResearchFinding, ResearchFreshness, SnapshotGroupView, SnapshotIndex, SnapshotIndexBackfill, SnapshotIndexEntry, UpsertCompanySnapshotRequest, AssignGroupRequest, GroupSource |
| `research.history` | ResearchFindingHistory, ResearchFindingHistoryRepository, DynamoResearchFindingHistoryRepository, FindingHistoryItem, StockGroupHistory, StockGroupHistoryRepository, DynamoStockGroupHistoryRepository, GroupHistoryItem, HistoryOrigin, ReplacedFinding, SnapshotHistoryView |
| `research.group` | StockGroup, StockGroupController, StockGroupExceptionHandler, StockGroupIllegalArgumentHandler, StockGroupConflictException, StockGroupNotFoundException, StockGroupService, StockGroupView, StockGroupRepository, DynamoStockGroupRepository, StockGroupItem, GroupNameItem, StockGroupSeed, StockGroupSeeder, StockGroupLookup, StockGroupRef, CreateStockGroupRequest, UpdateStockGroupRequest, UnknownGroupException |
| `research.ingest` | ResearchIngestController, ResearchSyncController |
| `persistence` | HistoryQuery (generic partition query helper used by snapshot and history) |

Tests: `InvestmentThesis*Test` → `thesis`; `CompanySnapshot*Test` (incl. `CompanySnapshotHistoryControllerTest`), `ResearchFindingTest`, `ResearchFreshnessTest`, `SnapshotIndexBackfillTest` → `research.snapshot`; `HistoryAppendOnlyTest`, `ResearchHistoryEntryTest` → `research.history`; `StockGroup*Test` → `research.group`; `ResearchIngestControllerTest`, `ResearchSyncControllerTest` → `research.ingest`; `ConflictResponseTest` (spans ingest and groups) → `research.ingest`. Update imports in `portfolio`, `deepanalysis` and root tests (`OversizedKeyTest`, `AwsProfileConfigTest`, `ValueScreenerApplicationTests`) as needed.

**Interfaces:**
- Produces: the package names above; later tasks and docs reference them.

- [ ] **Step 1:** Record the baseline: `mvn -B -q test` → note the total test count from the surefire summary (expected 541, all passing).
- [ ] **Step 2:** Move the files per table (one target package at a time, compile with `mvn -B -q test-compile` after each), fix `package`/`import` lines, add `public` only where the compiler requires it.
- [ ] **Step 3:** Structural check: `ls backend/src/main/java/com/valuescreener/research/*.java backend/src/test/java/com/valuescreener/research/*.java 2>/dev/null` → no output; every moved file's `package` line matches its directory (`grep -L "^package com.valuescreener.research.snapshot;" backend/src/main/java/com/valuescreener/research/snapshot/*.java` → no output, same per package).
- [ ] **Step 4:** `mvn -B test` → same test count as Step 1, 0 failures.
- [ ] **Step 5:** Report lists every visibility change (class/member, from → to, why).
- [ ] **Step 6: Commit (user)**
```bash
git add -A backend/src
git commit -m "refactor: split research package by topic, extract thesis

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Split `security` by topic, update class paths in docs, status entry

**Files:** under `backend/src/{main,test}/java/com/valuescreener/`; plus `dev-up.sh:37`, `.env.example:5,31`, `README.md:537,666`, `PROJECT-STATUS.md`.

| Target package | Main classes | Tests |
|---|---|---|
| `security.auth` | AuthController, AuthExceptionHandler, AuthUserView, LoginRequest, CsrfTokenView, SpaCsrfTokenRequestHandler, SecurityConfig, JsonSecurityErrorHandlers, AccountRegistry, AccountsProperties, Role, AdminPasswordHashGenerator, LoginAudit | AccountRegistryTest, AdminPasswordHashGeneratorTest, AuthControllerTest, CsrfCookieAttributesTest, EndpointAuthorizationTest, LoginAuditTest |
| `security.session` | DynamoSession, DynamoSessionRepository, SessionConfig, SerializationSupport | DynamoSessionRepositoryTest, SessionEndToEndTest |
| `security.throttle` | LoginAttemptGuard, LoginAttemptStore, DynamoLoginAttemptStore, LoginThrottleInterceptor, LoginThrottleWebConfig | DynamoLoginAttemptStoreTest, LoginAttemptGuardTest, LoginThrottleWiringTest, InMemoryLoginAttemptStore |
| `security.machinetoken` | MachineTokenFilter, MachineTokenGenerator, MachineTokenProperties, MachineTokenSecurityConfig, TokenHasher, IngestDisabledInAwsCheck | MachineToken*Test (4), MachineTokenTestData, TokenHasherTest, IngestDisabledInAwsCheckTest |
| `security.edge` | OriginVerifyFilter, ClientIpResolver | ClientIpResolverTest, OriginVerifyFilterTest, OriginVerifyIntegrationTest |
| `com.valuescreener` (root) | ClockConfig | shared test helpers used across topics: MutableClock, SecuritySliceTestConfig |

- [ ] **Step 1:** Baseline test count from Task 2 (same number expected).
- [ ] **Step 2:** Move files per table, fix `package`/`import`, minimal `public`. Check `@ControllerAdvice` annotations for `basePackages`/`basePackageClasses`/`assignableTypes` and adjust only if a moved controller would fall out of scope.
- [ ] **Step 3:** Update main-class paths: `com.valuescreener.security.MachineTokenGenerator` → `com.valuescreener.security.machinetoken.MachineTokenGenerator` (dev-up.sh:37, .env.example:31, README.md:537); `com.valuescreener.security.AdminPasswordHashGenerator` → `com.valuescreener.security.auth.AdminPasswordHashGenerator` (.env.example:5, README.md:666). Verify: `grep -rn "com.valuescreener.security\.[A-Z]" --include=*.md --include=*.sh --include=.env.example . | grep -v docs/superpowers` → no output. Verify the generator still runs: `cd backend && mvn -q spring-boot:run -Dspring-boot.run.main-class=com.valuescreener.security.machinetoken.MachineTokenGenerator` prints `token=` and `sha256=` lines (do not paste the output anywhere).
- [ ] **Step 4:** Structural check as in Task 2 Step 3 for `security/` (no `.java` directly in `security/`, main or test; package lines match directories).
- [ ] **Step 5:** `mvn -B test` → same count, 0 failures.
- [ ] **Step 6:** `PROJECT-STATUS.md`: new short top section "Package structure cleanup (2026-11-18)" (English + German summary): new package layout (one line per top-level topic), prototype folder, BASTI.md removed, test count, next step: the user's follow-up improvements, then part 2 (Terraform foundations).
- [ ] **Step 7: Commit (user)**
```bash
git add -A backend/src dev-up.sh .env.example README.md PROJECT-STATUS.md
git commit -m "refactor: split security package by topic

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

## Deutsche Kurzfassung

Drei Tasks: 1. `company-research-agent/` nach `prototypes/` verschieben, `prototypes/README.md` anlegen, `BASTI.md` löschen. 2. `research` in `snapshot`, `history`, `group`, `ingest` aufteilen und `thesis` herauslösen. 3. `security` in `auth`, `session`, `throttle`, `machinetoken`, `edge` aufteilen, Klassenpfade in Skript und Doku anpassen, Eintrag in `PROJECT-STATUS.md`. Nur Verschiebungen, die bestehende Testsuite (541 Tests) muss danach unverändert grün sein.
