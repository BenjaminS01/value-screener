# Serverless backend rewrite (part 1) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The Spring Boot backend stores everything in DynamoDB (no Postgres), keeps sessions and login throttling in DynamoDB, is ready for CloudFront/SSM, and can run as an AWS Lambda with SnapStart, while still running as a normal app locally.

**Architecture:** One DynamoDB table (`pk`/`sk` strings, `ttl` number) holds all item types. Repository interfaces stay where they are but become plain interfaces with DynamoDB implementations (AWS SDK v2 Enhanced Client); services keep their public behaviour. Atomic multi-item writes use `TransactWriteItems`; optimistic locking uses `@DynamoDbVersionAttribute`. Spring Session (core) with a custom DynamoDB `SessionRepository` replaces the in-memory servlet session. `aws-serverless-java-container-springboot3` provides the Lambda handler; Spring Cloud AWS loads secrets from SSM in profile `aws`.

**Tech Stack:** Java 21, Spring Boot 3.3.5, AWS SDK 2.58.0 (`dynamodb`, `dynamodb-enhanced`, `lambda`), Spring Session core, Spring Cloud AWS 3.2.x (`spring-cloud-aws-starter-parameter-store`), `aws-serverless-java-container-springboot3` 2.x, Testcontainers with `amazon/dynamodb-local` 2.x, JUnit 5.

**Spec:** [`docs/superpowers/specs/2026-11-14-serverless-terraform-design.md`](../specs/2026-11-14-serverless-terraform-design.md) (part 1 = Section 12 step 1; Sections 6, 7, 8 "same origin").

## Global Constraints

- Working mode: the **user does all git operations** (branch, add, commit). Implementer and reviewer never run state-changing git; each task ends with the commit command for the user. Before each task the controller explains its concept in German.
- Suggested branch (user creates it): `feature/serverless-backend`.
- Environment: `export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64; export PATH=$JAVA_HOME/bin:$PATH`. Docker needed for tests; keep `backend/src/test/resources/docker-java.properties` (`api.version=1.44`). Review diffs with `git diff --ignore-cr-at-eol`.
- No new AWS resources, no `aws`/`terraform` commands. Nothing in this plan talks to real AWS.
- One table. Name from `app.dynamodb.table-name` (default `value-screener`). Key attributes `pk` (S), `sk` (S); TTL attribute `ttl` (N, epoch seconds). **Provisioned** capacity, 20 RCU / 20 WCU, no GSI, no auto scaling (spec 6.2: sum ≤ 25).
- DynamoDB Local does not delete expired items: every read of an item with `ttl` must treat `ttl <= now` as absent.
- Behaviour of all existing endpoints, status codes and JSON fields stays the same, **except** `CompanySnapshotView.id` is removed (there is no numeric id any more; the frontend does not use it).
- Business rules from spec 6.1 hold; each has a test (named in the tasks).
- `app.research-ingest.enabled` and `app.research-sync.enabled` default `false` (unchanged).
- No secrets in repo, env examples hold placeholders/hashes only. Never echo secret env vars.
- Concurrency conflicts are reported as `com.valuescreener.persistence.ConcurrentUpdateException` and mapped to the **same HTTP status and body** the handlers produce today for `ObjectOptimisticLockingFailureException`/`DataIntegrityViolationException`.

## Review Focus

1. **Expired items read as live** (DynamoDB Local and real DynamoDB before TTL sweep, up to 48 h): an expired session, login block, or yesterday's counter must behave as absent. → Tests `expiredSessionIsNotFound` (Task 5), `expiredBlockIsIgnoredEvenIfItemStillExists` (Task 5), `newDayStartsAtZeroEvenIfOldItemExists` (Task 1).
2. **Two clicks racing past the daily limit** (limit 5, two concurrent requests at count 4): exactly one succeeds. → Test `concurrentConsumesNeverExceedLimit` (Task 1).
3. **A group deleted while a stock is being assigned to it**: either the assignment fails or the delete fails, never a snapshot pointing at a missing group. → Tests `deleteFailsOnceGroupWasEverReferenced` (Task 3), `upsertFailsWhenGroupWasDeletedMeanwhile` (Task 4).
4. **Lost update on concurrent saves of the same snapshot/position/thesis** (two stale copies): the second save gets 409, nothing silently overwritten. → Tests `staleSaveIsRejected` in Tasks 2, 3, 4.
5. **Session after login on another Lambda instance / after logout**: new repository instance finds the session; after logout the old cookie is rejected. → Tests `sessionIsVisibleToAnotherRepositoryInstance`, `logoutInvalidatesSessionEverywhere` (Task 5).

---

### Task 1: DynamoDB foundation + daily usage limits on DynamoDB

Concept to explain first: what a single-table DynamoDB design is (partition key/sort key, items of different shapes in one table), provisioned capacity vs. on-demand, conditional writes, transactions, TTL; DynamoDB Local for development.

**Files:**
- Modify: `backend/pom.xml` (add `software.amazon.awssdk:dynamodb`, `dynamodb-enhanced` via the existing SDK BOM; test: `org.testcontainers:testcontainers` already transitively present, use `GenericContainer`)
- Create: `backend/src/main/java/com/valuescreener/persistence/DynamoProperties.java`
- Create: `backend/src/main/java/com/valuescreener/persistence/DynamoConfig.java`
- Create: `backend/src/main/java/com/valuescreener/persistence/TableDefinition.java`
- Create: `backend/src/main/java/com/valuescreener/persistence/DynamoTableInitializer.java`
- Create: `backend/src/main/java/com/valuescreener/persistence/ConcurrentUpdateException.java`
- Create: `backend/src/main/java/com/valuescreener/persistence/Ttl.java`
- Modify: `backend/src/main/java/com/valuescreener/limits/UsageLimiter.java` (rewrite on DynamoDB)
- Modify: `backend/src/main/resources/application.yml` (block `app.dynamodb`)
- Modify: `docker-compose.yml` (add service `dynamodb-local`, keep `postgres` until Task 6)
- Create: `backend/src/test/java/com/valuescreener/persistence/DynamoTestSupport.java`
- Create: `backend/src/test/java/com/valuescreener/persistence/DynamoTableInitializerTest.java`
- Modify: `backend/src/test/java/com/valuescreener/limits/UsageLimiterTest.java`

**Interfaces:**
- Produces:
  - `record DynamoProperties(String tableName, String endpoint, String region, boolean createTable)` bound to `app.dynamodb.*`; defaults: `tableName=value-screener`, `endpoint=` (empty = real AWS), `region=eu-central-1`, `createTable=false`.
  - `DynamoConfig` beans: `DynamoDbClient dynamoDbClient(DynamoProperties)` (when `endpoint` is set: `endpointOverride` + static dummy credentials `local`/`local`; else default credentials chain), `DynamoDbEnhancedClient dynamoDbEnhancedClient(DynamoDbClient)`.
  - `final class TableDefinition` with constants `PK="pk"`, `SK="sk"`, `TTL="ttl"`, `READ_CAPACITY=20L`, `WRITE_CAPACITY=20L` and `static CreateTableRequest createTableRequest(String tableName)`. This is the single source of the table shape; part 3 (Terraform) copies these values.
  - `DynamoTableInitializer`: on startup, if `createTable` is true and the table does not exist, creates it and enables TTL on `ttl`. Idempotent.
  - `final class Ttl` with `static long epochSeconds(Instant)` and `static boolean isExpired(Long ttl, Instant now)` (`ttl != null && ttl <= now.getEpochSecond()`).
  - `class ConcurrentUpdateException extends RuntimeException` (`ConcurrentUpdateException(String message, Throwable cause)`).
  - Test support: `DynamoTestSupport` starts one shared `GenericContainer("amazon/dynamodb-local:<pinned 2.x>")` with `-jar DynamoDBLocal.jar -inMemory -sharedDb` on port 8000, registers `app.dynamodb.endpoint` and `app.dynamodb.create-table=true` via `@DynamicPropertySource`, and offers `static String freshTableName()` (unique name per test class) so tests don't share data.
  - `UsageLimiter` public API unchanged: `Quota consume(String account, Role role, JobType type)`, `Quota quota(String account, Role role, JobType type)`; constructor `UsageLimiter(DynamoDbClient, DynamoProperties, LimitsProperties, Clock)`.

Item layout for counters: `pk = "USAGE#" + day (ISO date)`, `sk = account + "#" + jobType` (account lower-cased; global: `"*#ALL"`), attribute `count` (N), `ttl` = start of day + 2 days.

- [ ] **Step 1: Write `DynamoTableInitializerTest`**: `createsTableWithKeysAndTtl` (after init: `describeTable` shows hash `pk`, range `sk`, provisioned 20/20; `describeTimeToLive` shows attribute `ttl`), `secondRunIsNoOp` (calling init twice does not throw).

- [ ] **Step 2: Run it, expect FAIL** (classes missing). `cd backend && mvn -B test -Dtest=DynamoTableInitializerTest`

- [ ] **Step 3: Add dependencies and implement** `DynamoProperties`, `DynamoConfig`, `TableDefinition`, `DynamoTableInitializer`, `Ttl`, `ConcurrentUpdateException`, `DynamoTestSupport`; `application.yml`:

```yaml
app:
  dynamodb:
    table-name: ${APP_DYNAMODB_TABLE_NAME:value-screener}
    endpoint: ${APP_DYNAMODB_ENDPOINT:}
    region: eu-central-1
    create-table: ${APP_DYNAMODB_CREATE_TABLE:false}
```

- [ ] **Step 4: Run, expect PASS.**

- [ ] **Step 5: Rewrite `UsageLimiterTest` against DynamoDB Local** (keep every existing case; Postgres container removed from this test). Required cases, with `MutableClock`:
  - `userGetsFivePerDay`: 5 × `consume(USER)` succeed, remaining 4..0; 6th throws `LimitReachedException` with scope `ACCOUNT`.
  - `adminGetsTwentyPerDay`.
  - `globalCapStopsEveryoneAndDoesNotCountTheAccount`: with `globalPerDay=2`, third consume by another account throws scope `GLOBAL` and `quota()` of that account still shows 0 used.
  - `limitZeroMeansNoInvocationAndNoWrite`.
  - `accountNameIsCaseInsensitive`.
  - `newDayStartsAtZeroEvenIfOldItemExists`: consume 5 on day D, advance clock 1 day, consume succeeds with remaining 4.
  - `concurrentConsumesNeverExceedLimit`: at 4 used, 10 threads call `consume` concurrently; exactly 1 succeeds, 9 throw `LimitReachedException`, stored count is 5.
  - `itemsCarryTtlTwoDaysAfterDayStart`.

- [ ] **Step 6: Run, expect FAIL.**

- [ ] **Step 7: Rewrite `UsageLimiter`** with one `TransactWriteItems` of two `Update` items (account first, global second), each `ADD #c :one` with condition `attribute_not_exists(#c) OR #c < :limit`, `SET ttl`. On `TransactionCanceledException`: cancellation reason index 0 `ConditionalCheckFailed` → `ACCOUNT`, else index 1 → `GLOBAL`. After success read the account item (consistent read) for the remaining count. `limit <= 0` → throw without writing (account first, then global, as today). Remove `JdbcTemplate` usage.

- [ ] **Step 8: Add `dynamodb-local` to `docker-compose.yml`** (port `8000:8000`, `-jar DynamoDBLocal.jar -sharedDb -dbPath /home/dynamodblocal/data`, volume `value-screener-dynamodb-data`).

- [ ] **Step 9: Run full suite, expect PASS.** `mvn -B test` (Postgres container still used by the other DB tests in this task's state).

- [ ] **Step 10: Commit (user)**

```bash
git add backend/pom.xml backend/src docker-compose.yml
git commit -m "feat(persistence): DynamoDB foundation, daily limits on DynamoDB

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: Portfolio positions and investment theses on DynamoDB

Concept: mapping a domain object to an item (Enhanced Client beans), optimistic locking with a version attribute, conditional create for uniqueness.

**Files:**
- Modify: `portfolio/PortfolioPosition.java` (remove JPA annotations, keep invariants, `getId()` removed, `version` stays as `Long`)
- Modify: `portfolio/PortfolioPositionRepository.java` (plain interface)
- Create: `portfolio/DynamoPortfolioPositionRepository.java`, `portfolio/PositionItem.java`
- Modify: `portfolio/PortfolioService.java` (drop `@Transactional`), `portfolio/PortfolioExceptionHandler.java`
- Modify: `research/InvestmentThesis.java`, `research/InvestmentThesisRepository.java`, `research/InvestmentThesisService.java`, `research/InvestmentThesisExceptionHandler.java`
- Create: `research/DynamoInvestmentThesisRepository.java`, `research/ThesisItem.java`
- Modify tests: `portfolio/PortfolioPositionRepositoryTest.java` (DynamoDB Local), `portfolio/PortfolioServiceTest.java`, `research/InvestmentThesisServiceTest.java`, controller tests only where they reference the old exception types.
- Delete: `research/InvestmentThesisMigrationTest.java` (its constraints move to `InvestmentThesisTest`/repository test)

**Interfaces:**
- Consumes: `DynamoDbEnhancedClient`, `DynamoProperties`, `ConcurrentUpdateException` (Task 1).
- Produces:
  - `interface PortfolioPositionRepository { Optional<PortfolioPosition> findByIsin(String isin); List<PortfolioPosition> findAll(); PortfolioPosition save(PortfolioPosition p); void delete(PortfolioPosition p); }`
  - `interface InvestmentThesisRepository { Optional<InvestmentThesis> findById(String isin); InvestmentThesis save(InvestmentThesis t); }` (`saveAndFlush` callers switch to `save`).
  - Items: position `pk="POSITION"`, `sk="ISIN#"+isin`; thesis `pk="THESIS"`, `sk="ISIN#"+isin`. `BigDecimal` stored as DynamoDB Number (no precision loss), `LocalDate`/`Instant` as ISO strings.
  - `save` of a new object (version null) uses condition `attribute_not_exists(pk)`; of an existing object the version condition. Any `ConditionalCheckFailedException` → `ConcurrentUpdateException`. `delete` is conditional on the version.

- [ ] **Step 1: Rewrite `PortfolioPositionRepositoryTest`** on DynamoDB Local: `savesAndFindsByIsin`, `findAllReturnsAllPositions`, `secondCreateWithSameIsinIsRejected` (two new objects with same ISIN → second save throws `ConcurrentUpdateException`), `staleSaveIsRejected` (two loaded copies, save first, save second → `ConcurrentUpdateException`), `deleteRemovesPosition`, `decimalsRoundTripExactly` (`quantity=0.123456`, `entryPrice=12345.678901` equal by `compareTo`).
- [ ] **Step 2: Same for theses** in a new `research/InvestmentThesisRepositoryTest.java`: `savesAndFindsById`, `concurrentFirstSaveIsRejected`, `staleSaveIsRejected`, `nullTextsRoundTrip`.
- [ ] **Step 3: Run both, expect FAIL.**
- [ ] **Step 4: Implement** items, repositories, domain changes; exception handlers map `ConcurrentUpdateException` exactly like today's `ObjectOptimisticLockingFailureException` (and thesis: like `DataIntegrityViolationException`). Remove `@Transactional` from these services.
- [ ] **Step 5: Update service/controller tests** so they mock the new interfaces / throw `ConcurrentUpdateException`; existing assertions on status codes and bodies stay unchanged.
- [ ] **Step 6: Run `mvn -B test`, expect PASS.**
- [ ] **Step 7: Commit (user)** `git commit -m "feat(persistence): portfolio and theses on DynamoDB"` (+ Co-Authored-By line).

---

### Task 3: Stock groups on DynamoDB (seeding, unique names, delete protection)

Concept: replacing a foreign key with a "referenced" flag written in the same transaction; uniqueness via a guard item; idempotent seeding.

**Files:**
- Modify: `research/StockGroup.java`, `research/StockGroupRepository.java`, `research/StockGroupService.java`
- Create: `research/DynamoStockGroupRepository.java`, `research/StockGroupItem.java`, `research/StockGroupSeeder.java`, `research/StockGroupSeed.java` (the 35 groups from `V4__create_stock_group.sql`, same keys, names, sort orders)
- Tests: `research/StockGroupRepositoryTest.java` (new), `research/StockGroupSeederTest.java` (new), `research/StockGroupServiceTest.java`, delete `research/StockGroupMigrationTest.java`

**Interfaces:**
- Consumes: Task 1.
- Produces:
  - `interface StockGroupRepository { Optional<StockGroup> findById(String key); List<StockGroup> findAll(); boolean existsByNameIgnoreCase(String name); int maxSortOrder(); StockGroup create(StockGroup g); StockGroup update(StockGroup g, String previousName); void delete(StockGroup g); }`
  - Items: group `pk="GROUP"`, `sk="KEY#"+key`, attributes `name`, `sortOrder`, `version`, optional `referenced` (BOOL). Name guard `pk="GROUPNAME"`, `sk=lowercase(trimmed name)`.
  - `create`: transaction Put group (`attribute_not_exists(pk)`) + Put name guard (`attribute_not_exists(pk)`). `update` with a changed name: transaction Update group (version) + Delete old guard + Put new guard. Conflicts → `ConcurrentUpdateException`; a name clash keeps today's `StockGroupConflictException` message (check `existsByNameIgnoreCase` first, transaction protects the race).
  - `delete`: transaction Delete group with condition `attribute_not_exists(referenced) AND version = :v` + Delete name guard. Condition failure → `StockGroupConflictException("Group <KEY> appears in the group history and cannot be deleted")`.
  - Static helper for Task 4: `static TransactWriteItem markReferenced(String tableName, String groupKey)` → Update `SET referenced = :true` with condition `attribute_exists(pk)`.
  - `StockGroupSeeder`: on startup, Put marker `pk="META"`, `sk="SEED#stock-groups-v1"` with `attribute_not_exists(pk)`; only if that succeeds, create the 35 groups (skip any that exist). Runs after `DynamoTableInitializer`.
  - `StockGroupService.delete` keeps the "still has n stock(s) assigned" check first (count via `CompanySnapshotRepository.countByGroupKey`, see Task 4; until Task 4 lands, keep calling the existing snapshot repository).

- [ ] **Step 1: Write tests**: `StockGroupSeederTest`: `seedsThirtyFiveGroupsWithKeysNamesAndOrder` (spot-check `BANKS`/`Banks`/10 and `DIVERSIFIED_OTHER`/`Diversified / Other`/350), `secondStartDoesNotReseedDeletedGroup`. `StockGroupRepositoryTest`: `createRejectsDuplicateNameIgnoringCase`, `renameFreesOldName`, `staleSaveIsRejected`, `deleteSucceedsForUnreferencedGroup`, `deleteFailsOnceGroupWasEverReferenced` (apply `markReferenced` in a transaction, then delete → `StockGroupConflictException`), `maxSortOrderIsZeroWhenEmpty`.
- [ ] **Step 2: Run, expect FAIL.**
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Update `StockGroupServiceTest`** to the new repository interface; messages and statuses unchanged.
- [ ] **Step 5: Run `mvn -B test`, expect PASS.**
- [ ] **Step 6: Commit (user)** `git commit -m "feat(persistence): stock groups on DynamoDB"` (+ Co-Authored-By line).

---

### Task 4: Company snapshots, findings and history on DynamoDB

Concept: embedding child rows (findings) in the parent item; one transaction for snapshot + history + group reference; append-only history as sorted items under the snapshot's partition.

**Files:**
- Modify: `research/CompanySnapshot.java`, `research/ResearchFinding.java`, `research/ResearchFindingHistory.java`, `research/StockGroupHistory.java` (remove JPA; history entries reference the snapshot by ISIN, groups by key/name)
- Modify: `research/CompanySnapshotRepository.java`, `research/ResearchFindingHistoryRepository.java`, `research/StockGroupHistoryRepository.java`, `research/CompanySnapshotService.java`, `research/StockGroupLookup.java`, `research/StockGroupService.java`, `research/CompanySnapshotView.java` (drop `id`), `research/CompanySnapshotExceptionHandler.java`
- Create: `research/DynamoCompanySnapshotRepository.java`, `research/SnapshotItem.java`, `research/FindingHistoryItem.java`, `research/GroupHistoryItem.java`
- Modify: `frontend/src/api/researchApi.ts` (remove `id` from the snapshot type) and `frontend/src/api/researchApi.test.ts` fixtures
- Tests: `research/CompanySnapshotRepositoryTest.java` (rewrite), `research/CompanySnapshotServiceIntegrationTest.java` (rewrite on DynamoDB Local), `research/CompanySnapshotServiceTest.java`, controller tests that assert `id`; delete `research/ResearchHistoryMigrationTest.java`

**Interfaces:**
- Consumes: Tasks 1, 3 (`StockGroupRepository`, `DynamoStockGroupRepository.markReferenced`).
- Produces:
  - Snapshot item `pk="SNAPSHOT"`, `sk="ISIN#"+isin`: identity fields, `groupKey`, `groupSource`, `version`, and `findings` as a map keyed by criterion key (each: `numericValue`, `booleanValue`, `claim`, `sourceUrl`, `asOfDate`). The group name is resolved from the group items when building views (one `findAll()` of groups per list request).
  - Finding history `pk="SNAPSHOT#"+isin`, `sk="FHIST#"+replacedAt(ISO, UTC, fixed width)+"#"+uuid`; group history `sk="GHIST#"+changedAt+"#"+uuid`. Query by prefix, newest first (`scanIndexForward=false`) — same order as today's `ORDER BY ... DESC, id DESC`.
  - `interface CompanySnapshotRepository { Optional<CompanySnapshot> findByIsin(String isin); List<CompanySnapshot> findAll(); List<CompanySnapshot> findAllByGroupKey(String key); List<CompanySnapshot> findAllWithoutGroup(); List<CompanySnapshot> findAllByIsinIn(Collection<String> isins); long countByGroupKey(String key); CompanySnapshot save(CompanySnapshot s, List<ResearchFindingHistory> findingHistory, Optional<StockGroupHistory> groupHistory); }`
  - `save` = one `TransactWriteItems`: Put snapshot (create: `attribute_not_exists(pk)`, else version condition), Put each history item (`attribute_not_exists(pk)`), and `markReferenced(groupKey)` whenever a group history entry is written. Snapshot condition failure → `ConcurrentUpdateException`; `markReferenced` failure → `UnknownGroupException(groupKey)`.
  - `ResearchFindingHistoryRepository`/`StockGroupHistoryRepository` keep only the read methods: `List<ResearchFindingHistory> findBySnapshotIsin(String isin)`, `List<StockGroupHistory> findBySnapshotIsin(String isin)`, plus `boolean existsForGroup(String key)` is no longer needed (delete uses `referenced`). No update/delete methods (append-only).
  - `CompanySnapshotService.upsert` and `assignGroupManually` collect history entries and call the single `save`.

- [ ] **Step 1: Rewrite `CompanySnapshotRepositoryTest`**: `savesSnapshotWithFindingsAndReadsBack` (18 findings, all fields, decimals with 4 places), `findAllByGroupKeyAndWithoutGroup`, `findAllByIsinIn`, `countByGroupKey`, `staleSaveIsRejected`, `historyIsReturnedNewestFirst` (three entries with the same `replacedAt` keep a deterministic order), `historyAndSnapshotAreWrittenAtomically` (force the snapshot condition to fail → no history item exists afterwards), `upsertFailsWhenGroupWasDeletedMeanwhile` (delete group, then save with a group history entry → `UnknownGroupException`, nothing written), `worstCaseTransactionFits` (new snapshot with 18 replaced findings + group change succeeds).
- [ ] **Step 2: Rewrite `CompanySnapshotServiceIntegrationTest`** keeping its existing scenarios (changed finding → history entry with origin; unchanged finding → no history; rounding to 4 decimals prevents false history; manual group change → group history `MANUAL`; AI proposal ignored when a manual group exists).
- [ ] **Step 3: Run, expect FAIL.**
- [ ] **Step 4: Implement** domain changes, items, repository, service and lookup changes; remove `id` from `CompanySnapshotView` and the frontend type.
- [ ] **Step 5: Update unit/controller tests**; run `mvn -B test` and `cd frontend && npm test && npm run build`, expect PASS.
- [ ] **Step 6: Commit (user)** `git commit -m "feat(persistence): research snapshots and history on DynamoDB"` (+ Co-Authored-By line).

---

### Task 5: Sessions and login throttling on DynamoDB

Concept: why in-memory state breaks with several Lambda instances; Spring Session's `SessionRepository`; sliding expiry with throttled refresh; optimistic update loops for counters.

**Files:**
- Modify: `backend/pom.xml` (`org.springframework.session:spring-session-core`, version from Boot)
- Create: `security/DynamoSessionRepository.java`, `security/SessionConfig.java` (`@EnableSpringHttpSession`)
- Modify: `security/LoginAttemptGuard.java` (state via a store)
- Create: `security/LoginAttemptStore.java`, `security/DynamoLoginAttemptStore.java`, `security/InMemoryLoginAttemptStore.java` (tests only → place in `src/test`)
- Modify: `backend/src/main/resources/application.yml` (`server.servlet.session.cookie.name: JSESSIONID` to keep today's cookie name)
- Tests: `security/DynamoSessionRepositoryTest.java` (new), `security/DynamoLoginAttemptStoreTest.java` (new), `security/LoginAttemptGuardTest.java`, `security/AuthControllerTest.java`, `security/CsrfCookieAttributesTest.java`

**Interfaces:**
- Consumes: Task 1.
- Produces:
  - `DynamoSessionRepository implements SessionRepository<MapSession>`; constructor `(DynamoDbClient, DynamoProperties, Clock, Duration maxInactive /*2h from server.servlet.session.timeout*/, Duration refreshInterval /*10 min*/)`. Item `pk="SESSION#"+id`, `sk="SESSION"`, attributes `created`, `lastAccessed`, `maxInactiveSeconds`, `attrs` (B: Java-serialized `HashMap<String,Object>`), `ttl = lastAccessed + maxInactive`.
    - `save`: write when the session is new, its id changed (delete old item in the same transaction), an attribute changed, or `lastAccessed - storedLastAccessed >= refreshInterval`; otherwise no write.
    - `findById`: `null` if missing or `lastAccessed + maxInactive <= now`.
    - `deleteById`: deletes.
  - `interface LoginAttemptStore { <S> S update(String key, Class<S> type, UnaryOperator<S> change); <S> Optional<S> get(String key, Class<S> type); }` — `update` reads, applies, writes with a version condition, retries up to 5 times on conflict; items `pk="LOGIN#"+key`, `sk="STATE"`, `ttl` derived from the state (block end or window end + `VIOLATION_MEMORY`, whichever later). Keys: `"IP#"+ip`, `"ACCOUNT#"+lowercase(name)`.
  - `LoginAttemptGuard(Clock, LoginAttemptStore)`: same public methods, constants and behaviour; the `ConcurrentHashMap` and `purgeIfLarge` go away (TTL replaces purging). `IpState`/`AccountState` become serializable records (package-private).

- [ ] **Step 1: `DynamoSessionRepositoryTest`**: `sessionIsVisibleToAnotherRepositoryInstance`, `expiredSessionIsNotFound` (advance `MutableClock` 2 h + 1 s; item still exists), `logoutInvalidatesSessionEverywhere` (`deleteById` on instance A → `findById` on B is null), `changedIdMovesSession` (old id null, new id found), `accessWithinTenMinutesDoesNotWrite` / `accessAfterTenMinutesRefreshesExpiry` (inspect stored `lastAccessed`), `securityContextSurvivesRoundTrip` (store a `SecurityContextImpl` with a `UsernamePasswordAuthenticationToken` and role, read back equal principal/authorities).
- [ ] **Step 2: `DynamoLoginAttemptStoreTest`**: `updateIsAtomicUnderConcurrency` (20 threads increment a counter state → 20), `expiredBlockIsIgnoredEvenIfItemStillExists`.
- [ ] **Step 3: Run, expect FAIL.**
- [ ] **Step 4: Implement**; `LoginAttemptGuardTest` uses `InMemoryLoginAttemptStore` and keeps all existing assertions.
- [ ] **Step 5: End-to-end check in `AuthControllerTest`/`CsrfCookieAttributesTest`** (full context, DynamoDB Local): login sets cookie `JSESSIONID` with `HttpOnly`, `Secure`, `SameSite=Strict`; session id changes on login; `/api/auth/me` works with the cookie; after logout the same cookie gets 401.
- [ ] **Step 6: Run `mvn -B test`, expect PASS.**
- [ ] **Step 7: Commit (user)** `git commit -m "feat(security): sessions and login throttling in DynamoDB"` (+ Co-Authored-By line).

---

### Task 6: Remove Postgres

Concept: what disappears (JPA, Flyway, JDBC) and why nothing else depends on it now.

**Files:**
- Delete: `backend/src/main/resources/db/migration/` (V1–V7)
- Modify: `backend/pom.xml` (remove `spring-boot-starter-data-jpa`, `postgresql`, `flyway-core`, `flyway-database-postgresql`, test `org.testcontainers:postgresql`)
- Modify: `backend/src/main/resources/application.yml` (remove `spring.datasource`, `spring.jpa`, `spring.flyway`)
- Modify: `backend/src/test/java/com/valuescreener/ValueScreenerApplicationTests.java` (DynamoDB Local instead of Postgres)
- Modify: `docker-compose.yml` (remove `postgres` service and `value-screener-postgres-data` volume), `dev-up.sh` (start `dynamodb-local`, set `APP_DYNAMODB_ENDPOINT=http://localhost:8000`, `APP_DYNAMODB_CREATE_TABLE=true`), `.env.example` (remove `DB_*`), `README.md` (local setup; the "local live instance" section at ~line 634 uses `APP_DYNAMODB_TABLE_NAME=value-screener-live` instead of `DB_URL`)
- Any remaining `@Transactional`, `jakarta.persistence`, `org.springframework.orm`, `org.springframework.dao` imports in `src/main`.

- [ ] **Step 1:** `grep -rn "jakarta.persistence\|org.springframework.orm\|org.springframework.dao\|@Transactional\|JdbcTemplate" backend/src` → remove remaining uses.
- [ ] **Step 2:** Apply the file changes above.
- [ ] **Step 3:** `mvn -B test` → PASS; `mvn -B dependency:tree | grep -i "postgres\|flyway\|hibernate"` → no output.
- [ ] **Step 4: Manual local check (user):** `./dev-up.sh`, then log in as ADMIN and USER, buy/sell/correct/delete a position, create/rename/delete a group, save a thesis, run the deep-analysis stub (`Quelle: lokale Simulation`). The user deletes the old volume with `docker volume rm value-screener_value-screener-postgres-data` (name as shown by `docker volume ls`).
- [ ] **Step 5: Commit (user)** `git commit -m "chore: remove Postgres, JPA and Flyway"` (+ Co-Authored-By line).

---

### Task 7: Lambda readiness (handler, SnapStart, origin header, client IP, SSM, deep-analysis alias)

Concept: how a Lambda handler wraps Spring (`SpringBootLambdaContainerHandler`), what SnapStart snapshots, why a secret origin header, how Spring Cloud AWS turns SSM parameters into properties, Lambda aliases.

**Files:**
- Modify: `backend/pom.xml` (`com.amazonaws.serverless:aws-serverless-java-container-springboot3` 2.x; `io.awspring.cloud:spring-cloud-aws-starter-parameter-store` with BOM `spring-cloud-aws-dependencies` 3.2.x; `maven-assembly-plugin` producing `target/value-screener-backend-lambda.zip` with `lib/*.jar` + classes, built in `package`)
- Create: `backend/src/assembly/lambda-zip.xml`
- Create: `backend/src/main/java/com/valuescreener/lambda/StreamLambdaHandler.java`
- Create: `security/OriginVerifyFilter.java`
- Modify: `security/ClientIpResolver.java`, `security/SecurityConfig.java` (register filter before everything else)
- Modify: `deepanalysis/DeepAnalysisProperties.java`, `deepanalysis/DeepAnalysisConfig.java`, `deepanalysis/LambdaDeepAnalysisInvoker.java` (qualifier)
- Create: `backend/src/main/resources/application-aws.yml`
- Tests: `lambda/StreamLambdaHandlerTest.java`, `security/OriginVerifyFilterTest.java`, `security/ClientIpResolverTest.java`, `deepanalysis/LambdaDeepAnalysisInvokerTest.java`, `AwsProfileConfigTest.java`

**Interfaces:**
- Produces:
  - `StreamLambdaHandler implements RequestStreamHandler`; static init `SpringBootLambdaContainerHandler.getHttpApiV2ProxyHandler(ValueScreenerApplication.class)`. Handler name for Terraform: `com.valuescreener.lambda.StreamLambdaHandler::handleRequest`. Nothing in init may capture per-instance randomness without a restore hook (check `MachineTokenGenerator`, `SecureRandom` uses).
  - `OriginVerifyFilter` (`OncePerRequestFilter`): active only if `app.security.origin-verify.secret` is non-blank; header `X-Origin-Verify`; constant-time compare (`MessageDigest.isEqual`); mismatch/missing → 403 empty body, before Spring Security.
  - `ClientIpResolver`: new property `app.security.client-ip-header` (default blank). If set **and** origin-verify is active: use that header (`CloudFront-Viewer-Address`, format `ip:port`; IPv6 without brackets → strip the part after the last `:`); if header missing fall back to `getRemoteAddr()`. Otherwise today's logic unchanged.
  - `DeepAnalysisProperties` gains `String qualifier` (default blank); invoker sets `.qualifier(...)` only when non-blank.
  - `application-aws.yml`: `spring.config.import: aws-parameterstore:/value-screener/`; `app.dynamodb.create-table: false`; `app.deep-analysis.qualifier: live`; `app.security.client-ip-header: CloudFront-Viewer-Address`; `app.security.trust-forwarded-for: false`. SSM parameter names (for part 3): `/value-screener/app.admin.password-hash`, `/value-screener/app.security.origin-verify.secret`, and per extra user both `/value-screener/app.users[<n>].username` and `/value-screener/app.users[<n>].password-hash` (lists must come from one property source). `ADMIN_USERNAME` and non-secret settings come from Lambda env vars.

- [ ] **Step 1: Tests**:
  - `OriginVerifyFilterTest`: `inactiveWhenSecretBlank`, `rejectsMissingHeaderWith403`, `rejectsWrongHeaderWith403`, `passesCorrectHeader`.
  - `ClientIpResolverTest` (add): `usesViewerAddressIpv4` (`203.0.113.7:51234` → `203.0.113.7`), `usesViewerAddressIpv6` (`2001:db8::1:51234` → `2001:db8::1`), `ignoresViewerHeaderWhenOriginVerifyInactive`, existing cases unchanged.
  - `LambdaDeepAnalysisInvokerTest` (add): `passesQualifierWhenSet`, `omitsQualifierWhenBlank`.
  - `StreamLambdaHandlerTest` (DynamoDB Local): an HTTP API v2 event `GET /api/auth/csrf` → status 200 with a CSRF token body; `GET /api/portfolio` without session → 401.
  - `AwsProfileConfigTest`: loading `application-aws.yml` as a YAML resource contains the import and the four properties above (no AWS call).
- [ ] **Step 2: Run, expect FAIL.**
- [ ] **Step 3: Implement**, including the assembly.
- [ ] **Step 4:** `mvn -B package` → `target/value-screener-backend-lambda.zip` exists; `unzip -l` shows `lib/` jars and `com/valuescreener/lambda/StreamLambdaHandler.class`; size < 250 MB unzipped.
- [ ] **Step 5:** `mvn -B test` → PASS; `cd frontend && npm test` → PASS.
- [ ] **Step 6: Docs:** `PROJECT-STATUS.md` (new top section "Serverless rewrite part 1 done", next step part 2), `README.md` (Lambda zip build), `.env.example` comments for `APP_DYNAMODB_*`; mark `docs/superpowers/plans/2026-11-14-terraform-handoff.md` as superseded by the design. English body + German summary for new sections.
- [ ] **Step 7: Commit (user)** `git commit -m "feat(lambda): Lambda handler, origin header, SSM config, deep-analysis alias"` (+ Co-Authored-By line).

---

## After all tasks

Whole-branch review (most capable model) against the spec's Section 6.1 table and the Review Focus list, then the user's manual local test (Task 6 Step 4 repeated on the final state), then merge by the user. Part 2 (Terraform foundations) gets its own plan.

## Deutsche Kurzfassung

Plan für Teil 1, den lokalen Backend-Umbau, in 7 Tasks:
1. DynamoDB-Grundlage mit einer Tabelle (`pk`/`sk`/`ttl`, provisioned 20/20) und die Tageslimits per DynamoDB-Transaktion.
2. Portfolio und Thesen.
3. Aktiengruppen mit Startdaten, eindeutigen Namen und Löschschutz über ein „referenced“-Kennzeichen.
4. Snapshots mit eingebetteten Befunden und Historie in einer Transaktion. Das Feld `id` fällt weg.
5. Sessions und Login-Schutz in DynamoDB.
6. Postgres, JPA und Flyway werden entfernt.
7. Lambda-Handler, Zip für SnapStart, geheimer CloudFront-Header, echte Client-IP, Secrets aus SSM und Lambda-Alias.

Jeder Task arbeitet mit Tests gegen DynamoDB Local. Vor jedem Task kommt eine deutsche Erklärung, und der Nutzer committet. Nichts im Plan berührt das echte AWS.
