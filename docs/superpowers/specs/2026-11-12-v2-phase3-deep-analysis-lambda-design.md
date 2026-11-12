# v2 Phase 3 — Deep-Analysis Demo via AWS Lambda (code only) — Design

Date: 2026-11-12. Branch: `feature/v2-phase3-deep-analysis-job`.
Parent spec: [`2026-11-03-app-scope-v2-design.md`](2026-11-03-app-scope-v2-design.md) Sections 5.1, 6, 8 item 6, 12.
Related: [`2026-11-03-quarterly-report-rag-design.md`](2026-11-03-quarterly-report-rag-design.md) (Quarkus Lambda module pattern),
[`2026-09-20-aws-deployment-design.md`](2026-09-20-aws-deployment-design.md) (VPC, ECS, no NAT).

## 1. Intent

The "Tiefenanalyse starten" button on the research detail page should do something real: the
backend calls an AWS Lambda function through the AWS SDK, the Lambda answers with a "not
implemented yet" text plus harmless metadata that proves it ran on Lambda, and the backend adds the
user's remaining daily quota. It is a showcase of the wiring (backend → AWS SDK → Lambda) and the
first Quarkus Lambda module, which later grows into the real deep analysis.

Most important constraint (user, 2026-11-12): **no user can blow the budget with many requests.**

## 2. Changes against the scope spec (user decisions 2026-11-12)

1. **No DynamoDB job table, no stream, no polling in this phase.** The backend invokes the Lambda
   **synchronously** (`LambdaClient.invoke`, `RequestResponse`) and returns the answer directly.
   The job pattern (scope spec §6/§7) comes later with chat/summary.
2. **Code only.** This phase writes and tests the code locally. Creating the AWS resources (Lambda,
   IAM role, invoke permission, Lambda VPC interface endpoint, reserved concurrency) happens **later
   with Terraform**. Terraform (infrastructure) and the AWS SDK (runtime calls in the backend) are
   complementary and are used together.
3. **The Lambda is written with Quarkus** (`quarkus-amazon-lambda`), the stack planned for the real
   deep research later and for `report-intelligence/`.
4. Scope spec Section 6, 7 and 12 (phase 3 row) are updated to match.

## 3. Goals

1. New module `deep-analysis/` (Quarkus, Java 21, Maven) with one Lambda handler
   `deep-analysis-demo` that returns the demo answer with Lambda metadata.
2. Backend endpoint that checks login and daily limits, invokes the Lambda through the AWS SDK and
   returns its answer plus the remaining quota.
3. Daily usage limits in Postgres (`usage_counter`), checked and incremented atomically **before**
   the invocation, per account plus a global cap.
4. Locally (profile `local`, no AWS credentials) a clearly labelled stub answers instead of Lambda,
   so the whole flow can be clicked through.
5. Frontend: the button on the research detail page triggers the call and shows the answer and the
   remaining quota.

## 4. Non-goals

- Any AWS resource, Terraform code, deployment pipeline for the Lambda. (Later, separate phase.)
- DynamoDB, streams, asynchronous jobs, polling.
- An LLM call or any real analysis.
- The EventBridge heartbeat from the old Topic 5 plan.

## 5. Anti-goals

- No way for a user to cause more than the configured number of Lambda invocations per day: the
  limit check happens before the invocation and cannot be raced past.
- The Lambda answer must not reveal sensitive data: no account id, no full ARN, no environment
  variables, no credentials, no log-group names.
- The frontend must never pretend the stub is Lambda: the stub answer says so explicitly.

## 6. The Lambda (`deep-analysis/`)

- New top-level Maven module `deep-analysis/`, Quarkus (current LTS line), extension
  `quarkus-amazon-lambda`, Java 21, JVM packaging (native image is not needed now).
- Handler `DeepAnalysisDemoHandler implements RequestHandler<DeepAnalysisRequest, DeepAnalysisResponse>`.
  - Request: `{ "isin": "US0378331005" }`. Validated with the ISIN regex `[A-Z]{2}[A-Z0-9]{9}[0-9]`
    after trim + uppercase; invalid → response with `status: "INVALID_REQUEST"` and a short message
    (no exception stack to the caller).
  - Response (JSON):

    | Field | Source |
    |---|---|
    | `status` | `"NOT_IMPLEMENTED"` |
    | `message` | `"Noch nicht implementiert – hier wird künftig eine Tiefenanalyse für <ISIN> ausgegeben."` |
    | `greeting` | `"Hallo von AWS Lambda"` |
    | `functionName` | `Context.getFunctionName()` |
    | `functionVersion` | `Context.getFunctionVersion()` |
    | `region` | env `AWS_REGION` |
    | `memoryLimitMb` | `Context.getMemoryLimitInMB()` |
    | `awsRequestId` | `Context.getAwsRequestId()` |
    | `coldStart` | true on the first invocation of this execution environment (static flag) |
    | `invokedAt` | `Instant.now()` (ISO-8601, UTC) |

  - Deliberately not returned: `Context.getInvokedFunctionArn()` (contains the account id), log group
    and stream names, any other environment variable.
- Tests: `@QuarkusTest` with the Quarkus Lambda test support (mock context), covering valid ISIN,
  lowercase/padded ISIN, invalid ISIN, no ARN or account id in the serialized response, cold-start flag
  true on first and false on second call.
- Local run: Quarkus dev mode offers a mock Lambda event server; documented in the module README for
  manual tries. The backend does not call it (see §7.3).
- CI: `.github/workflows/ci.yml` gets a step/job that runs the module's tests.

## 7. Backend

### 7.1 Endpoint

`POST /api/research/snapshots/{isin}/deep-analysis` (any logged-in role; CSRF as for every write).

- The ISIN must belong to an existing snapshot (404 `Snapshot not found` otherwise) — the button only
  exists on researched stocks.
- Response 200:
  `{ status, message, greeting, functionName, functionVersion, region, memoryLimitMb, awsRequestId,
  coldStart, invokedAt, source: "LAMBDA" | "LOCAL_STUB", remainingToday, limitPerDay }`.
- Limit reached → 429 `{"error":"Tageslimit für die Tiefenanalyse erreicht"}` (account limit) or
  `{"error":"Tageslimit der Anwendung erreicht"}` (global cap). No invocation happens.
- Lambda error, timeout or missing configuration → 502 `{"error":"Tiefenanalyse nicht verfügbar"}`;
  details only in the log (no stack, no ARN to the client). The usage counter is **not** refunded
  (simpler, and errors must not become a way around the limit).
- `GET /api/research/deep-analysis/quota` → `{ remainingToday, limitPerDay }` for the logged-in account,
  so the UI can show the quota before the first click.

**Security note:** this is the first state-changing endpoint a USER may call. `SecurityConfig`'s
"anything except GET requires ADMIN" rule gets one explicit exception for exactly this path pattern
(`POST /api/research/snapshots/*/deep-analysis` → authenticated). Tests: USER 200, ADMIN 200,
anonymous 401, missing CSRF 403, and a USER `POST` to any other write endpoint still 403.

### 7.2 Usage limits (`usage_counter`, scope spec §8 item 6)

- Flyway migration (next free version after V6): table `usage_counter(account varchar, job_type
  varchar, day date, count int, primary key (account, job_type, day))`.
- Job type `DEEP_ANALYSIS_DEMO`. Day = `LocalDate.now(clock)` in UTC (existing `Clock` bean).
- Check and increment in **one statement per row**:
  `INSERT … ON CONFLICT (account, job_type, day) DO UPDATE SET count = usage_counter.count + 1
  WHERE usage_counter.count < :limit` — zero rows affected = limit reached.
- Two rows in the **same transaction**: the account row and the global row with the reserved account
  name `*` (global cap across all accounts and job types per day). If either is full, the transaction
  rolls back and nothing is counted.
- Configuration (`application.yml`, `app.limits.*`), start values:

  | Limit | Value |
  |---|---|
  | deep-analysis per USER per day | 5 |
  | deep-analysis per ADMIN per day | 20 *(Claude's call; scope spec only fixes the USER value)* |
  | global cap, all accounts and job types, per day | 100 |

- Username `*` is already reserved (phase 1), so no real account can collide with the global row.
- Tests: Testcontainers test that two parallel requests at count = limit − 1 cannot both pass; the
  global cap blocks even when the account still has quota; a new day starts at 0.

### 7.3 Calling Lambda

- Interface `DeepAnalysisInvoker { DeepAnalysisResult invoke(String isin); }`.
- `LambdaDeepAnalysisInvoker` (default): AWS SDK for Java v2 `LambdaClient` (`software.amazon.awssdk:lambda`,
  BOM-managed), function name from `app.deep-analysis.function-name` (default `deep-analysis-demo`),
  region from the default provider chain, timeouts: API call 10 s. Credentials from the default chain
  (in ECS later the task role). Maps the JSON payload to the result; `FunctionError` set → 502 path.
- `LocalStubDeepAnalysisInvoker` (active with `app.deep-analysis.mode=stub`, set in profile `local`):
  returns the same shape with `source: "LOCAL_STUB"`, `greeting: "Lokale Simulation – keine AWS-Lambda
  aufgerufen"`, `functionName: "local-stub"`, region `"local"`, its own random request id. The limits
  still apply, so the limit behaviour can be tested locally.
- Tests: the Lambda invoker against a mocked `LambdaClient` (success, function error, SDK exception);
  the stub; the controller with a mocked invoker.

### 7.4 Budget protection overview

| Layer | Where | This phase? |
|---|---|---|
| Per-account daily limit + global cap, atomic, before invocation | backend + Postgres | yes |
| No refund on errors | backend | yes |
| Reserved concurrency (e.g. 2) on the function | Terraform | later |
| API Gateway throttling on the route | Terraform | later |
| AWS Budget alarms + Budget Action (existing) | AWS account | exists |

A Lambda invocation of this size costs a fraction of a cent; with the global cap of 100 per day the
worst case stays far below 1 $/month. The bigger fixed cost later is the Lambda **VPC interface
endpoint** (~7–8 $ per month and AZ) that the backend needs in its private subnets without NAT —
to be decided in the Terraform phase (for example one AZ only, or create it only while demoing).

## 8. Frontend

- Research detail page, section "F. Tiefenanalyse": replace the disabled placeholder with
  - the quota line `Heute noch <n> von <limit> Analysen` (from the quota endpoint),
  - an enabled button `Tiefenanalyse starten` (disabled while running and when `remainingToday` is 0),
  - the result block: `message`, `greeting`, then a small list `Funktion`, `Version`, `Region`,
    `Speicher (MB)`, `Request-ID`, `Kaltstart` (`ja`/`nein`), `Ausgeführt am` (de-DE date and time,
    UTC), and the label `Quelle: AWS Lambda` or `Quelle: lokale Simulation`,
  - after the call the quota line updates from the response.
- 429 → the backend message; 502 → `Tiefenanalyse nicht verfügbar`; other errors via `writeErrorText`.
- Visible to USER and ADMIN.

## 9. Constraints

- Working mode as in phases 1–2: subagent-driven, TDD, the user does all git operations, concept
  explanation before each task, UI texts German, code English, JSON errors `{"error":"…"}`.
- New dependencies are allowed **only** where this spec names them: Quarkus (BOM + `quarkus-amazon-lambda`
  + test support) in `deep-analysis/`, AWS SDK v2 `lambda` (via BOM) in `backend/`. Nothing else.
- No AWS resources, no credentials, no real AWS calls in tests.

## 10. Left to the builder

Quarkus version within the current LTS line, package names, DTO class names, how the stub is
selected (`@ConditionalOnProperty` or profile), test granularity, task order.

## 11. Done when

- `deep-analysis/` tests green (`mvn test` in the module); backend `mvn test` green incl. the
  Testcontainers limit test; frontend `npm test` + `npm run build` green.
- Locally with `./dev-up.sh`: USER and ADMIN click the button, see the stub answer and the quota
  counting down; the 6th USER click shows the limit message.
- Scope spec §6, §7, §12 updated (Section 2).

## Deutsche Zusammenfassung

Phase 3 macht den Button „Tiefenanalyse starten“ echt, allerdings zunächst **nur als Code, lokal
getestet**. Das Deployment folgt später mit Terraform.

- **Ablauf:** Das Backend prüft Login und Tageslimit und ruft dann über das **AWS SDK** direkt eine
  **Lambda** auf. Es gibt kein DynamoDB und kein Polling.
- **Die Lambda** entsteht in einem neuen **Quarkus-Modul** `deep-analysis/`. Sie antwortet mit „Noch nicht
  implementiert …“, „Hallo von AWS Lambda“ und unbedenklichen Metadaten: Funktionsname, Version,
  Region, Speicher, Request-ID, Kaltstart und Zeit. Die ARN fehlt bewusst, weil sie die Account-ID
  enthält.
- **Das Backend** ergänzt die verbleibenden Analysen des Tages.
- **Budget-Schutz:**
  - 5 Analysen pro USER und 20 pro ADMIN am Tag, dazu eine globale Obergrenze von 100.
  - Geprüft und hochgezählt wird in Postgres in einem Schritt *vor* dem Aufruf, sodass sich das
    Limit auch mit parallelen Klicks nicht überlisten lässt.
  - Fehler geben keinen Versuch zurück.
  - Später kommen in AWS eine feste Obergrenze für gleichzeitige Ausführungen und die Drosselung in
    API Gateway dazu.
- **Lokal** antwortet eine klar gekennzeichnete Simulation, die Limits gelten trotzdem.
- **Terraform und AWS SDK ergänzen sich:** Terraform baut die Infrastruktur, das SDK ruft sie zur
  Laufzeit auf.
- **Kostenhinweis für später:** Der VPC-Endpunkt für Lambda kostet etwa 7 bis 8 $ pro Monat und
  Availability Zone. Darüber wird in der Terraform-Phase entschieden.
