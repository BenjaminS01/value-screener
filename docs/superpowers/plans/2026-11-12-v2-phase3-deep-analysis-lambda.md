# v2 Phase 3 — Deep-Analysis Demo via AWS Lambda (code only) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The "Tiefenanalyse starten" button calls a Quarkus Lambda through the AWS SDK (locally a labelled stub) after an atomic daily-limit check, and shows the Lambda's demo answer with metadata and the remaining quota.

**Architecture:** New Maven module `deep-analysis/` (Quarkus Lambda handler). Backend: `UsageLimiter` (Postgres `usage_counter`, one atomic statement per row), `DeepAnalysisInvoker` (AWS SDK `LambdaClient` or local stub), one controller with two endpoints. Frontend: section F of the research detail page.

**Tech Stack:** Quarkus (current LTS) + `quarkus-amazon-lambda`, Java 21; Spring Boot 3.3.5, AWS SDK for Java v2 `lambda`, Flyway/PostgreSQL 16 (Testcontainers); React 18 / TypeScript / Vitest.

**Spec:** [`docs/superpowers/specs/2026-11-12-v2-phase3-deep-analysis-lambda-design.md`](../specs/2026-11-12-v2-phase3-deep-analysis-lambda-design.md)

## Global Constraints

- **The user does all git operations.** "Commit" steps: stop, show `git diff --ignore-cr-at-eol --stat`, propose a short message with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. Never run state-changing git.
- Before each task give the user the **Concept** line in plain words. TDD: red → green → refactor.
- New dependencies **only**: Quarkus BOM + `quarkus-amazon-lambda` + its test support (`quarkus-junit5`, `quarkus-test-amazon-lambda`) in `deep-analysis/`; AWS SDK v2 BOM + `software.amazon.awssdk:lambda` in `backend/`. Nothing else, no npm packages.
- No AWS resources, no credentials, no real AWS calls in any test.
- Java 21: `export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64; export PATH=$JAVA_HOME/bin:$PATH`. Backend full run: `cd backend && rm -rf target/surefire-reports && mvn -q test` (Docker available). Frontend: `cd frontend && npm test && npm run build`.
- ISIN: trim + uppercase, regex `[A-Z]{2}[A-Z0-9]{9}[0-9]`.
- Lambda response JSON fields exactly: `status`, `message`, `greeting`, `functionName`, `functionVersion`, `region`, `memoryLimitMb`, `awsRequestId`, `coldStart`, `invokedAt`. Never the invoked ARN, account id, log group/stream, or other env vars.
- Exact copy:
  - Lambda `message`: `Noch nicht implementiert – hier wird künftig eine Tiefenanalyse für <ISIN> ausgegeben.`; `greeting`: `Hallo von AWS Lambda`; `status`: `NOT_IMPLEMENTED` (invalid input: `INVALID_REQUEST`, message `Ungültige ISIN`)
  - stub `greeting`: `Lokale Simulation – keine AWS-Lambda aufgerufen`, `functionName` `local-stub`, `functionVersion` `local`, `region` `local`
  - backend errors: 429 `Tageslimit für die Tiefenanalyse erreicht` (account) / `Tageslimit der Anwendung erreicht` (global); 502 `Tiefenanalyse nicht verfügbar`; 404 `Snapshot not found`
  - UI: `Heute noch <n> von <limit> Analysen`, button `Tiefenanalyse starten`, labels `Funktion`, `Version`, `Region`, `Speicher (MB)`, `Request-ID`, `Kaltstart` (`ja`/`nein`), `Ausgeführt am`, `Quelle: AWS Lambda` / `Quelle: lokale Simulation`
- Limits (`app.limits.*`): `deep-analysis-per-user: 5`, `deep-analysis-per-admin: 20`, `global-per-day: 100`. Job type `DEEP_ANALYSIS_DEMO`. Global row account `*`. Day = `LocalDate.now(clock)` (UTC clock bean).
- Invoker selection: `app.deep-analysis.mode` = `lambda` (default) | `stub` (set in `application-local.yml`); `app.deep-analysis.function-name` default `deep-analysis-demo`; `app.deep-analysis.region` default `eu-central-1`.

## Review Focus

1. **Two parallel clicks when the account has exactly one analysis left** → exactly one passes, the other gets 429, the counter ends at the limit. Test in Task 2.
2. **Global cap reached while the account still has quota** → 429 global message, and the account row is not incremented (same transaction rolls back). Test in Task 2.
3. **Lambda returns a function error, times out, or the SDK throws (no credentials)** → 502 `Tiefenanalyse nicht verfügbar`, no stack/ARN in the body, counter stays incremented. Test in Task 3/4.
4. **USER sends POST to another write endpoint after this security exception** (e.g. `POST /api/portfolio`, `PUT /api/research/snapshots/{isin}/group`) → still 403. Test in Task 4.
5. **Serialized Lambda response contains no `arn:` and no 12-digit account id** even though the mock context provides an ARN. Test in Task 1.

---

### Task 1: Quarkus Lambda module `deep-analysis/`

**Concept:** A Lambda function is just a class with one method that gets an input object and a `Context` from the Lambda runtime. Quarkus wires that class up as the handler; the `Context` tells us the function name, version, memory and request id — information that only exists when Lambda really runs the code.

**Files:**
- Create: `deep-analysis/pom.xml`, `deep-analysis/src/main/resources/application.properties`, `deep-analysis/README.md`
- Create: `deep-analysis/src/main/java/com/valuescreener/deepanalysis/DeepAnalysisDemoHandler.java`, `DeepAnalysisRequest.java`, `DeepAnalysisResponse.java`
- Test: `deep-analysis/src/test/java/com/valuescreener/deepanalysis/DeepAnalysisDemoHandlerTest.java`
- Modify: `.github/workflows/ci.yml` (new job `deep-analysis`: checkout, setup-java 21 temurin with maven cache, `mvn -B test` in `deep-analysis`)
- Modify: `.gitignore` if needed for `deep-analysis/target/`

**Interfaces:**
- Produces: handler `com.valuescreener.deepanalysis.DeepAnalysisDemoHandler implements com.amazonaws.services.lambda.runtime.RequestHandler<DeepAnalysisRequest, DeepAnalysisResponse>`; configured via `quarkus.lambda.handler` (named bean `deep-analysis-demo`). `DeepAnalysisRequest(String isin)`, `DeepAnalysisResponse` with the ten fields of Global Constraints (`memoryLimitMb` int, `coldStart` boolean, `invokedAt` ISO-8601 string). The backend (Task 3) deserializes exactly this JSON.
- Region comes from env `AWS_REGION`; in tests absent → `"unknown"`.

- [ ] **Step 1: Failing tests** (`@QuarkusTest`; call the handler with a mock `Context` — e.g. a small test `Context` implementation returning function name `deep-analysis-demo`, version `7`, memory `512`, request id `req-1`, invoked ARN `arn:aws:lambda:eu-central-1:123456789012:function:deep-analysis-demo`):
  - valid ISIN → `status NOT_IMPLEMENTED`, exact `message` with the ISIN, `greeting`, metadata fields from the context, `invokedAt` parseable as `Instant`.
  - ` us0378331005 ` → normalized ISIN in the message.
  - `X` → `status INVALID_REQUEST`, `message Ungültige ISIN`, no exception.
  - **Review Focus 5:** JSON of the response (Jackson) contains neither `arn:` nor `123456789012`.
  - `coldStart` true on the first call in a fresh handler state, false on the second (expose a package-private reset for the test, or test via two calls in order).
  - Also one test through the Quarkus Lambda test support (`LambdaClient.invoke` of `io.quarkus.amazon.lambda.test`) with a JSON input, to prove the Quarkus wiring.
- [ ] **Step 2: Run** `cd deep-analysis && mvn -q test` — expect FAIL. **Step 3: Implement.** **Step 4: Run** — expect PASS.
- [ ] **Step 5:** README: what the function does, `mvn test`, `mvn quarkus:dev` with the mock event server (curl example), and that deployment follows later with Terraform.
- [ ] **Step 6: Commit (user).** `feat(deep-analysis): Quarkus Lambda deep-analysis-demo returning safe Lambda metadata`

---

### Task 2: Usage limits in Postgres

**Concept:** The limit is counted in the database with a single "insert or increment if below the limit" statement. The database executes it atomically, so two clicks at the same moment cannot both slip through; account row and global row are checked in one transaction, so a full global cap undoes the account increment.

**Files:**
- Create: `backend/src/main/resources/db/migration/V7__create_usage_counter.sql`
- Create: `backend/src/main/java/com/valuescreener/limits/UsageLimiter.java`, `LimitsProperties.java`, `JobType.java` (enum `DEEP_ANALYSIS_DEMO`), `LimitReachedException.java` (with `Scope scope` = `ACCOUNT` | `GLOBAL`), `Quota.java`
- Modify: `backend/src/main/resources/application.yml` (`app.limits.*`)
- Test: `UsageLimiterTest.java` (Testcontainers + `@DataJpaTest`/`@JdbcTest` with `@Import(UsageLimiter.class …)` and a fixed `Clock`), `LimitsPropertiesTest.java`

**Interfaces:**
- Produces:
  - `record Quota(int remainingToday, int limitPerDay)`
  - `UsageLimiter.consume(String account, Role role, JobType type): Quota` — `@Transactional`; increments the account row then the global row `*`; throws `LimitReachedException(ACCOUNT|GLOBAL)` and leaves both rows unchanged when either is full; returns the quota **after** the increment.
  - `UsageLimiter.quota(String account, Role role, JobType type): Quota` — read only, missing row = 0 used.
  - Per-role limit: `ADMIN` → `deep-analysis-per-admin`, `USER` → `deep-analysis-per-user`. Account names are compared lower-case (as in `AccountRegistry`).
- SQL per row: `INSERT INTO usage_counter(account, job_type, day, count) VALUES (?, ?, ?, 1) ON CONFLICT (account, job_type, day) DO UPDATE SET count = usage_counter.count + 1 WHERE usage_counter.count < ?` — affected rows 0 → limit reached. Global row: `job_type` `ALL`. Limit 0 must block the first call too (insert path: guard with a pre-check `limit > 0`).
- V7: `usage_counter(account VARCHAR(100) NOT NULL, job_type VARCHAR(40) NOT NULL, day DATE NOT NULL, count INT NOT NULL CHECK (count >= 0), PRIMARY KEY (account, job_type, day))`.

- [ ] **Step 1: Failing tests:** first call → `Quota(4, 5)` for USER; 5 calls ok, 6th → `ACCOUNT`; ADMIN limit 20; next day (clock + 1 day) starts at 0; `quota()` without row → `(5,5)`; **Review Focus 1:** two threads (separate transactions via `TransactionTemplate`, `CountDownLatch` start) at count 4 → exactly one success, one `ACCOUNT`, final count 5; **Review Focus 2:** global row at 100 while account at 0 → `GLOBAL`, account row still absent/unchanged.
- [ ] **Step 2: Run** — expect FAIL. **Step 3: Implement.** **Step 4: Run** the limits tests and full `mvn -q test` — expect PASS.
- [ ] **Step 5: Commit (user).** `feat(limits): atomic daily usage limits per account plus global cap`

---

### Task 3: Lambda invoker (AWS SDK) and local stub

**Concept:** The backend talks to Lambda through one small interface. In AWS the implementation uses the AWS SDK's `LambdaClient` (credentials come from the ECS task role later); locally another implementation answers without AWS, clearly saying it is a simulation. Tests mock the SDK client, so no AWS account is needed.

**Files:**
- Modify: `backend/pom.xml` (AWS SDK v2 BOM in `dependencyManagement`, dependency `software.amazon.awssdk:lambda`; exclude unused HTTP clients only if needed — use the default `url-connection-client` or `apache-client` that the artifact brings, don't add others)
- Create in `backend/src/main/java/com/valuescreener/deepanalysis/`: `DeepAnalysisInvoker.java`, `DeepAnalysisResult.java`, `LambdaDeepAnalysisInvoker.java`, `LocalStubDeepAnalysisInvoker.java`, `DeepAnalysisUnavailableException.java`, `DeepAnalysisConfig.java` (creates `LambdaClient` bean only in mode `lambda`, with API call timeout 10 s)
- Modify: `application.yml` (`app.deep-analysis.mode: lambda`, `function-name: deep-analysis-demo`), `application-local.yml` (`app.deep-analysis.mode: stub`)
- Test: `LambdaDeepAnalysisInvokerTest.java` (Mockito `LambdaClient`), `LocalStubDeepAnalysisInvokerTest.java`, `DeepAnalysisConfigTest.java` (`ApplicationContextRunner`: mode stub → stub bean, no `LambdaClient`; mode lambda → Lambda invoker)

**Interfaces:**
- Consumes: JSON shape from Task 1.
- Produces:
  - `record DeepAnalysisResult(String status, String message, String greeting, String functionName, String functionVersion, String region, int memoryLimitMb, String awsRequestId, boolean coldStart, Instant invokedAt, Source source)` with `enum Source { LAMBDA, LOCAL_STUB }`.
  - `interface DeepAnalysisInvoker { DeepAnalysisResult invoke(String isin); }` — throws `DeepAnalysisUnavailableException` (unchecked) on function error (`InvokeResponse.functionError() != null`), non-200 status, unparsable payload, or any `SdkException`; the exception message never contains the ARN; the cause is logged at WARN with the request id if available.
  - Invocation: `InvokeRequest` with `functionName`, `invocationType(RequestResponse)`, payload `{"isin":"…"}` via the existing Jackson `ObjectMapper`.

- [ ] **Step 1: Failing tests:** success payload → mapped result with `source LAMBDA`; `functionError("Unhandled")` → `DeepAnalysisUnavailableException`; `SdkClientException` (e.g. "Unable to load credentials") → same exception; garbage payload → same; request payload contains the normalized ISIN and the configured function name; stub → `source LOCAL_STUB`, stub copy, fresh UUID request id, `coldStart` false; context runner tests for the mode switch.
- [ ] **Step 2: Run** — expect FAIL. **Step 3: Implement.** **Step 4: Run** the tests and full `mvn -q test` — expect PASS (the full context `ValueScreenerApplicationTests` must still start: it runs without profile `local`, so make sure mode `lambda` needs neither credentials nor a region from the environment at startup: set the region explicitly from `app.deep-analysis.region` (default `eu-central-1`) on the builder, because the SDK's default region chain throws at build time when `AWS_REGION` is missing; credentials are only resolved on the first call).
- [ ] **Step 5: Commit (user).** `feat(deep-analysis): AWS SDK Lambda invoker with local stub`

---

### Task 4: Endpoints, security exception, docs

**Concept:** One controller ties it together: check the snapshot exists, consume the quota, call the invoker, return the answer with the remaining quota. Security gets exactly one narrow exception so a USER may make this one POST — and tests prove every other write stays ADMIN-only.

**Files:**
- Create: `backend/src/main/java/com/valuescreener/deepanalysis/DeepAnalysisController.java`, `DeepAnalysisResponse.java` (backend view), `DeepAnalysisExceptionHandler.java`
- Modify: `security/SecurityConfig.java` (before `anyRequest()`: `.requestMatchers(HttpMethod.POST, "/api/research/snapshots/*/deep-analysis").authenticated()`)
- Modify docs: `docs/superpowers/specs/2026-11-03-app-scope-v2-design.md` §6 (synchronous invoke via AWS SDK, no DynamoDB in this step), §7 diagram note, §12 phase 3 row (code only; DynamoDB/job pattern moves to the chat/summary phase; deployment via Terraform later), plus a decision-log entry 2026-11-12
- Test: `DeepAnalysisControllerTest.java` (`@WebMvcTest` + `SecuritySliceTestConfig`, mocked `UsageLimiter`, `DeepAnalysisInvoker`, `CompanySnapshotService`), extend an existing security test for Review Focus 4

**Interfaces:**
- Consumes: Task 2 `UsageLimiter`, `Quota`, `LimitReachedException`; Task 3 `DeepAnalysisInvoker`, `DeepAnalysisResult`, `DeepAnalysisUnavailableException`; existing `CompanySnapshotService.getByIsin` (404).
- Produces:
  - `POST /api/research/snapshots/{isin}/deep-analysis` → 200 `DeepAnalysisResponse(status, message, greeting, functionName, functionVersion, region, memoryLimitMb, awsRequestId, coldStart, invokedAt, source, remainingToday, limitPerDay)`.
  - `GET /api/research/deep-analysis/quota` → `Quota` JSON `{remainingToday, limitPerDay}`.
  - Order inside POST: snapshot exists (404 first, no quota used) → `consume` (429) → `invoke` (502).
  - Account and role from the `Authentication`, never from the request.

- [ ] **Step 1: Failing tests:** USER 200 with merged fields; ADMIN 200; anonymous 401; missing CSRF 403; unknown ISIN 404 and `consume` never called; `ACCOUNT` → 429 account copy, invoker never called; `GLOBAL` → 429 global copy; **Review Focus 3:** invoker throws → 502 `{"error":"Tiefenanalyse nicht verfügbar"}` exactly; quota GET for USER; **Review Focus 4:** USER `POST /api/portfolio` → 403 and USER `PUT /api/research/snapshots/US0378331005/group` → 403 still.
- [ ] **Step 2: Run** — expect FAIL. **Step 3: Implement**, then update the scope spec. **Step 4: Run** full `mvn -q test` — expect PASS.
- [ ] **Step 5: Commit (user).** `feat(deep-analysis): endpoint with daily limits; USER may trigger the demo`

---

### Task 5: Frontend — section F

**Concept:** The section first asks the backend for today's quota, then the button sends the POST and shows the answer. Disabling the button while a request runs (and when the quota is 0) prevents accidental double clicks; the backend limit stays the real protection.

**Files:**
- Create: `frontend/src/api/deepAnalysisApi.ts` (+ test), `frontend/src/components/DeepAnalysisSection.tsx` (+ test)
- Modify: `frontend/src/pages/ResearchDetailPage.tsx` (+ test) — replace `DeepResearchPlaceholder` with `DeepAnalysisSection`
- Delete: `frontend/src/components/DeepResearchPlaceholder.tsx` (+ test)

**Interfaces:**
- Consumes: Task 4 JSON; `apiFetch`, `ApiError`, `writeErrorText`, `formatDate` (`research/format.ts`).
- Produces in `deepAnalysisApi.ts`:
  ```ts
  export interface DeepAnalysisQuota { remainingToday: number; limitPerDay: number }
  export interface DeepAnalysisResult { status: string; message: string; greeting: string; functionName: string; functionVersion: string; region: string; memoryLimitMb: number; awsRequestId: string; coldStart: boolean; invokedAt: string; source: 'LAMBDA' | 'LOCAL_STUB'; remainingToday: number; limitPerDay: number }
  export function fetchDeepAnalysisQuota(): Promise<DeepAnalysisQuota>
  export function runDeepAnalysis(isin: string): Promise<DeepAnalysisResult>
  ```
- `DeepAnalysisSection({ isin: string })`.

- [ ] **Step 1: Failing tests:** quota line `Heute noch 5 von 5 Analysen`; click → POST, result shows message, greeting, the seven labelled values, `Kaltstart: ja`, `Ausgeführt am` with de-DE date and time (UTC), `Quelle: lokale Simulation` for `LOCAL_STUB` and `Quelle: AWS Lambda` for `LAMBDA`; quota line updates to `Heute noch 4 von 5 Analysen`; button disabled while pending and when `remainingToday` is 0; 429 shows the backend message; 502 shows `Tiefenanalyse nicht verfügbar`; quota load failure → section still usable, no quota line.
- [ ] **Step 2: Run** — expect FAIL. **Step 3: Implement.** **Step 4: Run** `npm test`, `npm run build` — expect PASS.
- [ ] **Step 5: Commit (user).** `feat(frontend): deep-analysis demo section with quota`

---

## Finish

- [ ] User checks locally with `./dev-up.sh` as USER and ADMIN: stub answer, quota counting down, 6th USER click → limit message; optionally `mvn quarkus:dev` in `deep-analysis/` with the mock event server.
- [ ] Final whole-branch review, `PROJECT-STATUS.md` (phase 3 code done; next: Terraform for the Lambda incl. VPC endpoint decision), finish the branch (git by the user).
