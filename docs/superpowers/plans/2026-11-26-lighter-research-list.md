# Lighter Research List (Task 11) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `GET /api/research/snapshots` returns small summary rows read from the snapshot index instead of all full
snapshot items; overview and portfolio look the same as today.

**Architecture:** The existing index item per snapshot (`pk=SNAPINDEX`, `sk=ISIN#<isin>`, written in the save
transaction) gets the summary fields. The list endpoint maps index entries plus group names to
`SnapshotSummaryView`. A v2 backfill fills the new fields for existing snapshots once. The frontend switches the
list to a `SnapshotSummary` type.

**Tech Stack:** Java 21 / Spring Boot / AWS SDK v2 DynamoDB (tests against DynamoDB Local via Docker), React +
TypeScript + Vitest.

**Spec:** `docs/superpowers/specs/2026-11-26-lighter-research-list-design.md`

## Global Constraints

- Working mode: Claude edits only inside the repo; the user runs all git commands (Claude gives them, ending with
  `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`). One user step per message.
- Work on branch `feature/lighter-research-list` (multi-file change → branch + PR, not directly on `main`).
- Backend tests: `cd backend && mvn -q test -Dtest=<Class>`; full: `mvn test`. Frontend: `cd frontend && npm test`
  and `npm run build` (type check).
- The detail endpoint `GET /api/research/snapshots/{isin}`, history, group assignment and sync stay unchanged.
- Index items stay small: no findings, no `businessDescription`.
- Backend and frontend ship together with one `v*` tag.

## Review Focus

1. A snapshot saved while the v2 backfill runs: the backfill must not overwrite its (newer) index item → test in
   Task 2 (`neverOverwritesAnIndexItemWrittenByASave`).
2. Live table with v1 marker present and v1 index items (only `groupKey`): v2 must still run and upgrade them →
   Task 2 (`upgradesV1IndexItemsEvenWhenTheV1MarkerExists`).
3. Snapshot without findings: `lastResearchedAt` absent, row `stale=true` → Task 1 and Task 3 tests.
4. Snapshot without country or group: attributes absent, row `country=null`, `group=null` → Task 1 and Task 3.
5. Group key in the index whose group was deleted/renamed: row shows the key as name (as today) → Task 3.

---

### Task 1: Index item carries the summary fields

**Files:**
- Modify: `backend/src/main/java/com/valuescreener/research/snapshot/CompanySnapshot.java` (add `lastResearchedAt()`)
- Modify: `backend/src/main/java/com/valuescreener/research/snapshot/CompanySnapshotView.java` (use it)
- Modify: `backend/src/main/java/com/valuescreener/research/snapshot/SnapshotIndex.java`
- Modify: `backend/src/main/java/com/valuescreener/research/snapshot/SnapshotIndexEntry.java`
- Modify: `backend/src/main/java/com/valuescreener/research/snapshot/DynamoCompanySnapshotRepository.java` (`save`)
- Test: `backend/src/test/java/com/valuescreener/research/snapshot/CompanySnapshotRepositoryTest.java`,
  `CompanySnapshotTest.java`; adapt `research/group/StockGroupLookupTest.java` constructors

**Interfaces:**
- Produces:
  - `LocalDate CompanySnapshot.lastResearchedAt()`: newest finding `asOfDate`, `null` without findings.
  - `record SnapshotIndexEntry(String isin, String ticker, String companyName, String country, String groupKey,
    GroupSource groupSource, LocalDate lastResearchedAt)`.
  - `static Map<String, AttributeValue> SnapshotIndex.item(CompanySnapshot snapshot)` and
    `static TransactWriteItem SnapshotIndex.put(String tableName, CompanySnapshot snapshot)`; attribute names
    `ticker`, `companyName`, `country`, `groupKey`, `groupSource` (enum name), `lastResearchedAt` (ISO date
    string); null values are left out. Constant `SnapshotIndex.COMPANY_NAME = "companyName"` (Task 2 uses it).
  - `SnapshotIndex.toEntry` reads items that lack the new attributes (v1) as nulls.

- [ ] **Step 1: Write failing tests**
  - `CompanySnapshotTest.lastResearchedAtIsNewestFindingDateOrNull`: no findings → `null`; findings dated
    2026-03-01 and 2026-05-10 → `2026-05-10`.
  - `CompanySnapshotRepositoryTest.indexItemCarriesSummaryFields`: save a snapshot (ticker `AAPL`, name
    `Apple`, country `USA`, group `BANKS` source AI, one finding dated 2026-05-10) →
    `findIndex()` contains exactly `new SnapshotIndexEntry(ISIN, "AAPL", "Apple", "USA", "BANKS", GroupSource.AI,
    LocalDate.of(2026, 5, 10))`.
  - `CompanySnapshotRepositoryTest.indexItemLeavesOutMissingValues`: snapshot without country, group and findings →
    entry has `country`, `groupKey`, `groupSource`, `lastResearchedAt` all `null`; the raw item (GetItem on
    `SNAPINDEX`/`ISIN#…`) has none of these four attributes.
  - Update `indexItemIsWrittenWithEverySaveAndFollowsTheGroup` and `StockGroupLookupTest` to the new record.
- [ ] **Step 2: Run** `mvn -q test -Dtest=CompanySnapshotTest,CompanySnapshotRepositoryTest` → FAIL (compile).
- [ ] **Step 3: Implement** the interfaces above; `CompanySnapshotView.from` uses `snapshot.lastResearchedAt()`;
  `save` calls `SnapshotIndex.put(tableName, snapshot)` at the same position as today (transaction layout and
  `translate` unchanged).
- [ ] **Step 4: Run** the same command plus `-Dtest=StockGroupLookupTest,StockGroupServiceTest` → PASS.
- [ ] **Step 5: Commit** (user): `feat(research): snapshot index carries the list fields`.

### Task 2: Backfill v2 fills the new fields for existing snapshots

**Files:**
- Modify: `backend/src/main/java/com/valuescreener/research/snapshot/SnapshotIndexBackfill.java`
- Test: `backend/src/test/java/com/valuescreener/research/snapshot/SnapshotIndexBackfillTest.java`

**Interfaces:**
- Consumes: `SnapshotIndex.item(CompanySnapshot)`, `SnapshotIndex.COMPANY_NAME` (Task 1).
- Produces: `MARKER_SK = "INDEX#snapshots-v2"`.

- [ ] **Step 1: Write failing tests** (adapt the existing `legacy(...)` helper to write a snapshot item plus, where
  needed, a v1 index item with only `pk`, `sk`, `groupKey`):
  - `createsMissingIndexItemsAndTheMarker`: legacy snapshot without index item → after run `findIndex()` has the
    full entry; marker `META / INDEX#snapshots-v2` exists.
  - `upgradesV1IndexItemsEvenWhenTheV1MarkerExists`: v1 marker + v1 index item present → after run the entry has
    ticker, name, country, group source and date.
  - `neverOverwritesAnIndexItemWrittenByASave`: index item already has `companyName` with a different value (as a
    save would write) → after run that value is unchanged.
  - Keep `secondRunIsANoOp`, `doesNothingWithoutAnyDynamoConfiguration`, `runsAfterTheGroupSeeder`.
- [ ] **Step 2: Run** `mvn -q test -Dtest=SnapshotIndexBackfillTest` → FAIL.
- [ ] **Step 3: Implement:** read the SNAPSHOT partition in full (consistent read, no projection; map with
  `SnapshotItem.SCHEMA.mapToItem(...).toDomain()`), put `SnapshotIndex.item(snapshot)` with condition
  `attribute_not_exists(pk) OR attribute_not_exists(#n)` (`#n` = `companyName`), ignore
  `ConditionalCheckFailedException`/`TransactionConflictException`, write the v2 marker last. Update the class
  Javadoc (v2, why the condition).
- [ ] **Step 4: Run** `mvn -q test -Dtest=SnapshotIndexBackfillTest` → PASS.
- [ ] **Step 5: Commit** (user): `feat(research): backfill v2 fills the snapshot index list fields`.

### Task 3: List endpoint returns summary rows from the index

**Files:**
- Create: `backend/src/main/java/com/valuescreener/research/snapshot/SnapshotSummaryView.java`
- Modify: `CompanySnapshotService.java` (`list`), `CompanySnapshotController.java` (`list`),
  `CompanySnapshotRepository.java` + `DynamoCompanySnapshotRepository.java` (remove `findAll`,
  `findAllByGroupKey`, `findAllWithoutGroup`, the private `query`/`partitionRequest` if unused)
- Test: `CompanySnapshotServiceTest.java`, `CompanySnapshotControllerTest.java`,
  `CompanySnapshotServiceIntegrationTest.java`, `CompanySnapshotRepositoryTest.java`
  (`findAllByGroupKeyAndWithoutGroup` removed, `wholePartitionReadsFollowPagination` asserts `findIndex()` size 60
  and `countByGroupKey`), `StockGroupLookupTest.java` (drop `verify(snapshots, never()).findAll()`)

**Interfaces:**
- Consumes: `repository.findIndex()`, `SnapshotIndexEntry` (Task 1), `groups.namesByKey()`, `ResearchFreshness`.
- Produces: `record SnapshotSummaryView(String ticker, String isin, String companyName, String country,
  SnapshotGroupView group, LocalDate lastResearchedAt, boolean stale)` with
  `static SnapshotSummaryView from(SnapshotIndexEntry e, ResearchFreshness f, Map<String,String> groupNames)`
  (group name falls back to the key, like `CompanySnapshotView`); `List<SnapshotSummaryView>
  CompanySnapshotService.list()`; controller `GET /api/research/snapshots` without parameters.

- [ ] **Step 1: Write failing tests**
  - `CompanySnapshotServiceTest.listMapsIndexEntriesWithGroupNamesAndStaleFlag`: `findIndex()` returns one grouped
    fresh entry (`BANKS`, AI, date = today) and one ungrouped entry without date; `namesByKey()` = `{BANKS: Banks}`
    → rows: first `group = (BANKS, Banks, AI)`, `stale=false`; second `group=null`, `lastResearchedAt=null`,
    `stale=true`; `verify(repository, never()).findByIsin(any())`.
  - `CompanySnapshotServiceTest.listShowsKeyWhenGroupNameUnknown`: entry with group `OLD`, names empty → group
    name `OLD`.
  - `CompanySnapshotServiceTest.emptyIndexDoesNotLoadGroupNames`: `findIndex()` empty → empty list,
    `verify(groups, never()).namesByKey()`.
  - `CompanySnapshotControllerTest.listsSnapshotSummaries`: JSON has `isin`, `ticker`, `companyName`, `country`,
    `group.key`, `group.name`, `lastResearchedAt`, `stale`, and no `findings` / `businessDescription`
    (`jsonPath("$[0].findings").doesNotExist()`). Remove `listPassesGroupFilter`, replace `listFiltersByGroupAndNone`.
  - `CompanySnapshotServiceIntegrationTest`: replace `list(null)`/`list("banks")`/`list("none")` asserts with
    `list()` containing both rows with their group names; line 184 uses `findIndex()`.
- [ ] **Step 2: Run** `mvn -q test -Dtest='CompanySnapshot*Test,StockGroupLookupTest'` → FAIL.
- [ ] **Step 3: Implement** the interfaces above; remove the now unused repository methods.
- [ ] **Step 4: Run** `mvn test` (full, Docker needed) → all green; note the test count.
- [ ] **Step 5: Commit** (user): `feat(research): snapshot list reads only the index`.

### Task 4: Frontend uses `SnapshotSummary`

**Files:**
- Modify: `frontend/src/api/researchApi.ts`, `frontend/src/research/grouping.ts`,
  `frontend/src/pages/ResearchOverviewPage.tsx`, `frontend/src/pages/PortfolioPage.tsx` (type only, if needed)
- Test: `frontend/src/api/researchApi.test.ts`, `frontend/src/research/grouping.test.ts`,
  `frontend/src/pages/ResearchOverviewPage.test.tsx`, `frontend/src/pages/PortfolioPage.test.tsx`

**Interfaces:**
- Consumes: JSON of `SnapshotSummaryView` (Task 3).
- Produces: `export interface SnapshotSummary { ticker: string; isin: string; companyName: string; country:
  string | null; group: SnapshotGroup | null; lastResearchedAt: string | null; stale: boolean }`;
  `fetchAllSnapshots(): Promise<SnapshotSummary[]>`; `grouping.ts` functions and `Section.snapshots` typed with
  `SnapshotSummary`. `CompanySnapshot` stays for detail and `assignGroup`.

- [ ] **Step 1: Update tests to the summary shape** (fixtures without `findings`/`businessDescription`):
  `researchApi.test.ts` asserts the returned summary; overview test still shows group sections, country view,
  "veraltet" and "im Portfolio"; portfolio test still opens a researched position.
- [ ] **Step 2: Run** `cd frontend && npm run build` → FAIL (type errors in the fixtures/consumers).
- [ ] **Step 3: Implement** the type change.
- [ ] **Step 4: Run** `npm test` and `npm run build` → PASS.
- [ ] **Step 5: Commit** (user): `feat(frontend): research list uses the summary rows`.

### Task 5: Docs, PR, release, live check

**Files:**
- Modify: `PROJECT-STATUS.md` (new top section "Task 11 done", with German summary),
  `docs/superpowers/plans/2026-11-26-lighter-research-list.md` (short "Result" note per task)
- Create: new handoff `docs/superpowers/plans/2026-11-26-task11-done-handoff.md` (replaces the part-3 handoff;
  next items from the backlog)
- Memory: update `project-entry-point`.

- [ ] **Step 1:** Write the docs; user commits, pushes the branch, opens and merges the PR.
- [ ] **Step 2:** User tags `v3.2.0` on `main` and pushes the tag; the deploy workflow runs green.
- [ ] **Step 3: Live check** (user, in the browser): research overview shows the groups, country view, "veraltet",
  "im Portfolio"; portfolio opens a researched position.

**Note:** the backfill runs live because `application-aws.yml` sets `app.dynamodb.seed-groups: true` (checked
2026-11-26). With SnapStart it runs during Lambda init (at `publish-version` or a cold start) and writes the v2
marker once. CloudWatch shows the log line `Snapshot index backfilled for N snapshots`. If the overview still shows
empty names after the deploy, re-push the affected snapshots with `scripts/push-research.sh <ISIN>`; a save always
writes the full index item.

## Result (2026-11-26)

- Tasks 1-4 done as planned, executed inline; commits by the user. Backend `mvn test` 558/0, frontend `npm test`
  175 passed, `npm run build` clean.
- Rulings: `StockGroupServiceTest` also built the old 2-arg index entry and verified `findAll()`; switched to an
  `entry()` helper and `never().findByIsin(any())`.
- Final review (fresh reviewer): no Critical. Important fixed: a v1 index item (no name/ticker), e.g. written by the
  old release between `publish-version` and the alias switch or after a rollback, crashed the frontend sort and
  search. Now the row shows the ISIN as name and an empty ticker
  (`CompanySnapshotServiceTest.listFallsBackWhenAnIndexItemHasNoNameOrTicker`). Such rows heal on the next save or a
  re-push.
- Deferred minors: `StockGroup.java` comment still mentions `?group=none`; the backfill maps full snapshots, so one
  unmappable item would fail Lambda init (catch per item, test with null country); an unknown stored
  `groupSource` would fail the whole list (`valueOf`).
