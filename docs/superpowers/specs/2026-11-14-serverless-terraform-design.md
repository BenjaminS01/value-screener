# Design: serverless rebuild with Terraform (DynamoDB + Lambda + CloudFront)

Date: 2026-11-14. Status: design, awaiting user approval. Supersedes the hand-built AWS architecture in
[`2026-09-20-aws-deployment-design.md`](2026-09-20-aws-deployment-design.md) (VPC, RDS, ECS, VPC Link,
interface endpoints) and the AWS part of
[`2026-11-12-v2-phase3-deep-analysis-lambda-design.md`](2026-11-12-v2-phase3-deep-analysis-lambda-design.md)
("Later: Terraform", Lambda VPC endpoint). Context: [`../plans/2026-11-14-terraform-handoff.md`](../plans/2026-11-14-terraform-handoff.md).

## 1. Intent and why

The app (v2 phases 1–3: login, portfolio, research, thesis, history, deep-analysis demo) should run in
AWS again, this time **entirely described in Terraform**. The earlier hand-built infrastructure was a
teaching exercise and has been deleted; nothing is imported.

What matters to the user, in order:

1. **Cost.** At rest the app should cost (almost) nothing. It does not need to run permanently; few
   users, a few hundred stocks at most. Cost beats learning ECS/VPC networking further.
2. **Security.** No long-lived credentials, secrets never in repo, state or shell history.
3. **Data is kept.** Everything may be torn down and rebuilt, except the data.
4. **Learning Terraform.** The user is a Terraform beginner; every step is explained before the user
   runs it.

Decisions taken in the 2026-11-14 session follow from this: replace Postgres/RDS with **DynamoDB**,
run the Spring Boot backend as a **Lambda with SnapStart** instead of ECS, host the frontend on
**S3 + CloudFront**. With no VPC and nothing billed per hour, there is nothing to "shut down": the app
scales to zero by itself.

## 2. Goals

1. The backend persists all data in DynamoDB; Postgres, JPA, Flyway are removed. All existing business
   rules (Section 6) still hold, proven by tests.
2. The backend runs both as a normal Spring Boot app locally and as a Lambda (SnapStart) in AWS.
3. Sessions and login-throttling state survive across Lambda instances.
4. Secrets come from SSM Parameter Store at runtime.
5. Terraform code in `infra/` creates the whole AWS setup: state bucket, budget brake, IAM/OIDC,
   DynamoDB, both Lambdas, HTTP API, S3 + CloudFront, SSM parameters, log groups.
6. CI deploys new backend/Lambda code and frontend files without running Terraform.
7. Live smoke test as USER and ADMIN through the CloudFront URL: login, logout, portfolio (buy, sell,
   correct, delete), stock groups, quota line. **Decided (user, 2026-11-14):** research data stays
   empty in AWS for now, because ingest does not exist there and research-sync is deferred. The full
   live test (research, thesis, history, deep-analysis button with `Quelle: AWS Lambda`) moves to the
   end of the project together with the research-sync / token topic. A partly working app being live
   meanwhile is acceptable: everything needs a login and there are no users yet.

## 3. Non-goals

- No VPC, NAT, VPC endpoints, ECS, ECR, RDS, Cloud Map, VPC Link.
- No custom domain yet (CloudFront default `*.cloudfront.net` domain). Can be added later
  (ACM in `us-east-1`, Route 53 zone ~0.50 $/month, domain ~10–15 €/year).
- No migration of old data: nothing exists in AWS, and the local Postgres data (volume
  `value-screener-postgres-data`) is test data only and is discarded (user, 2026-11-14). The plan
  includes removing the volume from `docker-compose.yml`; the user deletes the volume.
- No Cognito, no change to the login model (account list in config, bcrypt hashes, session cookie).
- No real deep analysis / LLM call; Company Research Agent stays frozen.
- No managing the account identity itself in Terraform (root user, daily IAM user, `Admin` role):
  Terraform runs *as* that identity (see 7.2).
- Deferred to the end of the project as before: token renaming and the live research transfer test.

## 4. Anti-goals (would be a failure even if it works)

- Any resource billed per hour while idle (endpoints, NAT, provisioned concurrency, RDS).
- DynamoDB tables in on-demand mode or above the always-free provisioned capacity (25 RCU / 25 WCU
  summed over all tables and GSIs).
- A secret value in the repo, in Terraform state, in a Lambda environment variable, or on a command
  line.
- `terraform destroy` of the app stack deleting data.
- A user able to cause more deep-analysis invocations than the configured limits.
- The ingest endpoint (`app.research-ingest`) existing in AWS.

## 5. Approaches considered

| Approach | Trade-off | Outcome |
|---|---|---|
| **A. Backend as Lambda (SnapStart) + DynamoDB, frontend S3 + CloudFront** | 0 $ at rest, nothing to switch on/off; cold start ~1–2 s after a pause; backend rewrite of persistence, sessions, login throttling | **chosen** |
| B. ECS Fargate in a public subnet with public IP (no NAT/endpoints) + DynamoDB, API Gateway via VPC Link | no cold start, smaller rewrite, keeps ECS/VPC learning; ~0.50 $/month idle, ~0.02 $/h running, must be scaled to 0/1 by hand | rejected: cost and manual on/off |
| C. Small EC2 instance started on demand + DynamoDB | cheap while running, ~1 $/month idle (EBS); OS patching, little showcase value | rejected |
| Keep RDS (stop between uses) | ~2–3 $/month idle storage, AWS restarts a stopped DB after 7 days; snapshot/restore alternative costs 10–20 min per start | rejected |
| App Runner | closed to new customers since 2026-04-30 | not possible |

Cold start mitigation options: SnapStart (chosen, free for Java), provisioned concurrency (costs money,
rejected), GraalVM native image (build complexity, rejected), rewrite on Quarkus (too large, rejected).

## 6. Backend: persistence on DynamoDB

### 6.1 Business rules that must survive the move

Today Postgres enforces these; afterwards the code must (with tests for each):

| Rule | Today | Target |
|---|---|---|
| One portfolio position per ISIN | `UNIQUE (isin)` | ISIN is the key; create with `attribute_not_exists` |
| No lost update on concurrent buy/sell/correct, thesis, snapshot, group | `@Version` optimistic locking | conditional write on a `version` attribute; conflict → same HTTP result as today |
| Snapshot + findings + history written together | one DB transaction | `TransactWriteItems` (max 100 items; a snapshot has ≤ 18 findings + history rows, check the worst case) |
| One finding per (snapshot, criterion) | unique constraint | key design |
| History tables are append-only | no update code path | same; no update/delete code path |
| A group referenced by a snapshot or by history cannot be deleted | FK `REFERENCES stock_group` | checked in code inside the delete; must not be raceable (e.g. condition/transaction or a reference counter) |
| Group source/key pairs and status/reason checks | CHECK constraints | validated in the domain/service before writing |
| 35 seed stock groups | Flyway V4 insert | idempotent seeding (see 6.3) |
| Daily limits: account row + global row incremented atomically before invoking, never above the limit, no refund | one SQL upsert per row in one transaction | `TransactWriteItems` with two `Update` items, each `ADD count :1` with condition `attribute_not_exists(count) OR count < :limit`; a failed condition → 429; items carry a TTL so old days expire |
| Numbers rounded to 4 decimals to avoid false history entries | `NUMERIC(12,4)` + code | keep the code rounding; store as DynamoDB Number |
| Dates in UTC | as today | unchanged |

### 6.2 Data access

- AWS SDK v2 **DynamoDB Enhanced Client** (already on SDK 2.58.0 for Lambda). No Spring Data DynamoDB.
- Repository interfaces keep their role (`PortfolioPositionRepository`, `CompanySnapshotRepository`,
  …); implementations switch to DynamoDB. Services keep their public behaviour; controllers and DTOs
  should not need changes.
- Table layout (single table vs. one table per aggregate, keys, GSIs) is the builder's call, under
  these constraints: provisioned capacity, **sum of all RCU and WCU (tables + GSIs) ≤ 25 each**; all
  read patterns used by today's endpoints (list portfolio, list snapshots by group/country, snapshot
  by ISIN, history by snapshot, groups sorted by `sortOrder`, quota) must be served by a key lookup,
  a query, or a scan over a table that holds at most a few hundred items. Scans of small tables are
  acceptable at this size.
- Auto scaling of capacity: off (it would allow exceeding the free tier).

### 6.3 Seeding and schema

No Flyway. The 35 stock groups are seeded idempotently (conditional put per group) by the app on
startup or by a one-off step; builder's call. Locally, tables are created by the app or a script for
DynamoDB Local; in AWS, Terraform creates tables. The table definitions must not drift between the two
(one source of truth, e.g. a shared JSON definition or a test that compares them).

### 6.4 Local development and tests

- `docker-compose.yml`: replace `postgres` with **DynamoDB Local** (`amazon/dynamodb-local`).
- Persistence tests use DynamoDB Local via Testcontainers instead of Postgres. The WSL Docker fix
  (`docker-java.properties`, `api.version=1.44`) stays.
- Profile `local` keeps the deep-analysis stub.
- Postgres-specific dependencies (`spring-boot-starter-data-jpa`, `postgresql`, `flyway-*`) are
  removed at the end, when no code uses them.

## 7. Backend: running in Lambda

### 7.1 Runtime

- Managed runtime **Java 21**, **zip** package (SnapStart does not support container images).
- Adapter: `aws-serverless-java-container-springboot3` (HTTP API payload 2.0). The app keeps its normal
  `main` for local runs.
- **SnapStart** on published versions; API Gateway invokes an **alias** (e.g. `live`) that points to
  the latest published version.
- SnapStart correctness: no per-instance randomness or connections captured in the snapshot without a
  restore hook (CRaC `afterRestore` where needed, e.g. for anything seeded from `SecureRandom` at
  startup; AWS SDK clients are fine).
- Memory: start with 1024 MB (Spring needs it for acceptable latency; free tier is 400,000 GB-s per
  month). Timeout: 29 s (API Gateway's limit is 30 s).
- The deep-analysis call stays as built in phase 3 (`LambdaDeepAnalysisInvoker`, 10 s, no SDK
  retries). Without a VPC no endpoint is needed.
- Deep-analysis Lambda (Quarkus): **no SnapStart** (Quarkus starts fast enough for a demo), but it is
  published as versions with an alias `live` like the backend; the backend invokes
  `deep-analysis-demo:live` (qualifier configurable) and its invoke permission is scoped to that
  alias (Claude's call: same deploy mechanics for both functions).

### 7.2 Sessions

Today: servlet session in memory, 2 h timeout, cookie `HttpOnly`, `Secure`, `SameSite=Strict`, CSRF
token for the SPA. With several Lambda instances the in-memory session breaks.

Requirement: a session created by one instance is valid on all instances; logout invalidates it at
once; 2 h timeout; the cookie flags stay the same; CSRF protection stays. The timeout stays sliding (2 h after
the last request), but to save write capacity the stored expiry is refreshed at most every ~10 minutes
per session (Claude's call; effective timeout 1 h 50 min to 2 h). Implementation is the
builder's call; preferred: a session store in DynamoDB with TTL (there is no official Spring Session
DynamoDB module, so a small custom `SessionRepository` is acceptable). A stateless signed-cookie
session would need a signing key from SSM and cannot be revoked on logout before expiry; only choose it
if revocation is still guaranteed.

### 7.3 Login throttling and client IP

`LoginAttemptGuard` keeps per-IP and per-account state in a `ConcurrentHashMap`. It moves to DynamoDB
(atomic counters with TTL), same thresholds and behaviour.

Client IP: requests arrive CloudFront → API Gateway → Lambda, so the TCP source seen by API Gateway is a
CloudFront edge. To get the real client IP without letting clients forge it:

- CloudFront adds a **secret origin header** (e.g. `X-Origin-Verify`) to every request to API Gateway
  and forwards the `CloudFront-Viewer-Address` header.
- The backend **rejects every request without the correct origin header** (403). This also closes the
  direct path to the API Gateway URL that bypasses CloudFront.
- `ClientIpResolver` then uses `CloudFront-Viewer-Address` (replacing the current
  `trust-forwarded-for` logic in AWS; locally unchanged).
- The origin header value is generated by Terraform (`random_password`) and therefore stored in the
  Terraform state, and passed to the backend via SSM. Accepted (Claude's call): it only proves the
  request came through CloudFront, it grants no access to data, and the state bucket is private and
  encrypted. Rotation = `terraform apply -replace`.

### 7.4 Secrets and configuration

- Secrets in **SSM Parameter Store `SecureString`** (standard tier, AWS-managed key `aws/ssm`, free):
  `ADMIN_PASSWORD_HASH`, each `APP_USERS_<n>_PASSWORD_HASH`, the origin-verify value, later the
  research-sync token hash and other keys.
- Terraform creates each user-supplied parameter with a placeholder value and
  `lifecycle { ignore_changes = [value] }`. **The user enters the real value once in the AWS console**
  (browser). Never via CLI arguments.
- The backend reads the parameters at startup (SDK, `GetParametersByPath` under a prefix such as
  `/value-screener/`); the Lambda role may read only that prefix. Values read during SnapStart init end
  up in the encrypted snapshot; after changing a value a new version must be published (deploy). This
  is accepted (rare password changes).
- Non-secret config (usernames, limits, `mode: lambda`, function name, table name(s)) as Lambda
  environment variables set by Terraform.
- `app.research-ingest.enabled` stays `false` in AWS (never set its variables). `app.research-sync`
  stays disabled until the deferred live-transfer topic.

## 8. Frontend hosting

- `npm run build` output (`dist/`) in a **private S3 bucket**, readable only by CloudFront through
  **Origin Access Control**. Block public access on.
- **One CloudFront distribution**, two behaviours:
  - `/api/*` → HTTP API origin; no caching; forwards cookies, all query strings, required headers
    (incl. `X-XSRF-TOKEN`/CSRF header used by `apiClient.ts`); all HTTP methods; adds the origin-verify
    header.
  - default → S3; caching on; SPA fallback: 403/404 from S3 → `/index.html` with 200 (check whether the
    app needs it: it has no router today).
- Same origin for SPA and API: the frontend already calls relative `/api/...` paths with
  `credentials: 'same-origin'`, so **no frontend code change and no CORS**. Session cookie and CSRF
  work as locally.
- Security response headers (HSTS, `X-Content-Type-Options`, frame denial, a CSP that fits the built
  app) via a CloudFront response headers policy.

## 9. API Gateway

- **HTTP API**, one `ANY /{proxy+}` route plus `ANY /` if needed → backend alias. Payload format 2.0.
- Throttling on the stage: low limits (e.g. rate 10 req/s, burst 20); builder may adjust.
- Access logs off or into a log group with short retention (builder's call; cost ≈ 0).

## 10. AWS protections

| Protection | Where |
|---|---|
| Per-account and global daily limits, atomic, before invoking, no refund | backend (6.1) |
| API Gateway stage throttling | Terraform |
| Deep-analysis Lambda: timeout ≤ 10 s, memory 256–512 MB, **reserved concurrency 2** | Terraform |
| Backend Lambda: reserved concurrency (e.g. 5) | Terraform |
| Backend role: `lambda:InvokeFunction` only on `deep-analysis-demo`; DynamoDB only on its table(s); SSM read only on `/value-screener/*` | Terraform |
| Log groups created by Terraform, retention 14 days | Terraform |
| AWS Budgets: monthly budget **5 $**, e-mail alerts at 1 $ actual and 3 $ forecast, automatic deny action at 5 $ (Claude's call, see below) | Terraform |
| DynamoDB: deletion protection, point-in-time recovery (cost ≈ 0 at this size), `prevent_destroy` | Terraform |

**Budget action (Claude's call):** the old `budget-deny-policy.json` only blocks creating expensive
resources (EC2, RDS, …). For this setup the deny policy is extended to also deny
`lambda:InvokeFunction` and `dynamodb:UpdateTable`/capacity changes, and it is attached to the
**backend Lambda role and the GitHub deploy role**, never to the `Admin` role (the user must always be
able to fix things). Effect when triggered: the app stops calling the deep-analysis Lambda and no deploys
run; the user investigates and detaches the policy.

**Pre-check before `foundation` (Claude's call):** the user said nothing is left in the account; before
the first apply the user still checks in the console that no GitHub OIDC provider, budget, or old
deploy role remains (one OIDC provider per URL, unique budget names). Anything found is deleted by hand,
not imported.

**Known risk:** new accounts often have an account concurrency quota of **10**, and Lambda requires at
least 10 unreserved, so reserved concurrency may be impossible. First step of the Terraform phase: the
user checks the quota "Concurrent executions" in Service Quotas. If it is 10, either request an
increase (free) or drop reserved concurrency and rely on the app limits plus API Gateway throttling;
the user decides then.

## 11. Terraform layout and operation

### 11.1 Stacks (separate state each)

```
infra/
  bootstrap/   state bucket (S3, versioned, SSE, public access blocked); local state, applied once
  foundation/  budget + deny action, GitHub OIDC provider + deploy role, artifact bucket for Lambda zips
  data/        DynamoDB table(s), PITR, deletion protection, prevent_destroy; SSM parameters
  app/         both Lambdas + aliases + log groups + IAM roles, HTTP API, frontend bucket, CloudFront
```

`app` can be destroyed and rebuilt without touching `data`. `app` reads table names/ARNs from `data`
via `terraform_remote_state` or data sources (builder's call). Region `eu-central-1`; CloudFront is
global. Tags on everything (`project = value-screener`).

### 11.2 State

S3 backend in the bootstrap bucket, **native S3 locking** (`use_lockfile = true`, no DynamoDB lock
table), encryption on. State never in git (`.gitignore`: `*.tfstate*`, `.terraform/`; the
`.terraform.lock.hcl` is committed). The state contains no user passwords (7.4); it does contain the
origin-verify value (7.3).

### 11.3 Who runs Terraform (decided)

The user runs Terraform **locally in WSL**:
1. `aws login --profile vs-login` (console sign-in in the browser with MFA; temporary credentials,
   max 12 h; no access keys).
2. Profile `vs-admin` assumes the existing `Admin` role (`source_profile = vs-login`, `mfa_serial`).
   Whether the role's fresh-MFA condition is met by the console MFA or needs the MFA prompt on
   AssumeRole is tested during setup.
3. `terraform plan`, review, `terraform apply`.
4. `aws logout` when done.

Requirements on the user's machine (user installs): AWS CLI ≥ 2.32, Terraform ≥ 1.15 (needed for
`login_session` with the S3 backend; versions from a third-party source, verify at setup), AWS provider
≥ 6.23. **Claude never runs `terraform` or `aws` with these credentials**; Claude writes code and
explains, the user executes. Version constraints are pinned in `required_version` /
`required_providers`.

### 11.4 Code deployment (CI, no Terraform in CI)

Terraform owns the configuration; CI owns the code. Lambda code is ignored by Terraform after creation
(`ignore_changes` on the S3 key/version or a fixed key with object versions; builder's call).

- **Backend + deep-analysis:** on a version tag, GitHub Actions (OIDC role from `foundation`, no keys)
  runs tests, builds the zips, uploads to the artifact bucket, `update-function-code`,
  `publish-version` (SnapStart), points alias `live` at the new version.
- **Frontend:** build, `aws s3 sync dist/` to the frontend bucket, CloudFront invalidation.
- The deploy role may do exactly these actions on exactly these resources.
- `build-and-push-backend.yml` (ECR/ECS) is replaced. `ci.yml` (tests on push) stays.
- First deployment before CI exists: the user builds and uploads the initial zips by hand, step by
  step as explained.

## 12. Order of work (decided)

1. **Backend rewrite** (local, no AWS cost): DynamoDB persistence, sessions, login throttling, origin
   header + client IP, SSM config loading, Lambda adapter + SnapStart readiness. Ends with the full
   test suite green and the app working locally against DynamoDB Local (manual test by the user as
   ADMIN and USER).
2. **Terraform foundations:** user installs tools, `aws login` setup, quota check, `bootstrap`,
   `foundation` (budget brake, OIDC, artifact bucket).
3. **Terraform app:** `data`, then `app`, first manual deploy, user enters secrets in the console, live
   smoke test as USER and ADMIN (scope in goal 7); then CI deploy workflow. The full live test follows
   at the end of the project with research-sync.

Each part gets its own plan. Part 1 uses subagent-driven development as before; parts 2 and 3 are
teaching sessions: Claude writes each Terraform file, explains every block, the user runs the commands.

## 13. Cost estimate (eu-central-1)

| Item | At rest | Per use |
|---|---|---|
| Lambda (both), SnapStart | 0 (always-free tier) | 0 |
| DynamoDB provisioned ≤ 25/25, < 25 GB | 0 (always free) | 0 |
| PITR on a few MB | < 0.01 $ | – |
| CloudFront | 0 (1 TB / 10 M requests free) | 0 |
| HTTP API | 0 | ~1 $ per million requests (after the first-year allowance) |
| S3 (state, artifacts, frontend) | cents | cents |
| SSM standard parameters, CloudWatch Logs < 5 GB, Budgets | 0 | 0 |

Expected total: well below 1 $/month. The Free Plan ends around **2027-03-11** (6 months after account
creation on 2026-09-11) or when the credit is used up; then the account must move to a paid plan. The
setup is designed so that this changes the bill by cents.

## 14. Left to the builder

- DynamoDB table and key design (within 6.2), seeding mechanism (6.3), session store implementation
  (7.2), how CloudFront forwards the CSRF header, SPA fallback, CSP details, Terraform module split
  inside each stack, how `app` reads `data` outputs, how Lambda code versions are ignored by Terraform.
- Exact throttling numbers, memory sizes, retention values within the ranges given.

## 15. Docs to update when done

`PROJECT-STATUS.md`, `README.md` (local setup with DynamoDB Local), `.env.example`,
`docs/aws/aws-architecture-overview.md` (rewrite for the serverless setup), the handoff file, and a
"superseded" note in the 2026-09-20 deployment design/plan. Curriculum topic 14 ("AWS CDK"): open, not
part of this design.

## Deutsche Kurzfassung

Die gesamte AWS-Infrastruktur wird neu und vollständig mit Terraform aufgebaut. Die alte, von Hand
gebaute Umgebung ist gelöscht. Ziel ist vor allem: im Ruhezustand praktisch 0 $, sicher, Daten bleiben
erhalten. Deshalb wechselt die Datenbank von Postgres auf **DynamoDB** (provisioned, im dauerhaft
kostenlosen Rahmen), das Spring-Boot-Backend läuft als **Lambda mit SnapStart** (Kaltstart nach Pause
ca. 1–2 s) und das Frontend liegt in **S3 hinter CloudFront**. Kein VPC, keine Endpoints, nichts muss
hoch- oder heruntergefahren werden. CloudFront liefert Frontend und `/api/*` unter einer Adresse aus,
daher kein CORS und keine Änderung am Frontend; ein geheimer Header von CloudFront sperrt den direkten
Weg zu API Gateway und liefert die echte Client-IP für den Login-Schutz. Alle Geschäftsregeln (eindeutige
ISIN, optimistisches Sperren, gemeinsame Schreibvorgänge, Historie nur anhängen, Gruppen-Löschschutz,
atomare Tageslimits) werden im Code mit DynamoDB-Bedingungen und Transaktionen abgesichert und getestet.
Sessions und Login-Schutz wandern nach DynamoDB. Secrets liegen in **SSM Parameter Store**, Terraform
legt nur Platzhalter an, die echten Werte trägt der Nutzer in der Konsole ein. Terraform führt der Nutzer
lokal in WSL aus, angemeldet mit `aws login` und der `Admin`-Rolle mit MFA, ohne Access Keys; Claude führt
nie `terraform`/`aws` mit diesen Zugangsdaten aus. State in S3 mit nativer Sperre. Vier getrennte Stacks
(`bootstrap`, `foundation`, `data`, `app`), damit ein Abriss der App nie die Daten löscht. Code-Deploys
über GitHub Actions mit OIDC. Reihenfolge: 1. Backend-Umbau lokal, 2. Terraform-Grundlagen,
3. Terraform für die App und Live-Test. Risiko: Neue Accounts haben oft nur 10 gleichzeitige
Lambda-Ausführungen, dann ist Reserved Concurrency nicht möglich – wird zuerst geprüft. Live wird
vorerst nur ein Kurztest gemacht (Login, Portfolio, Gruppen); die Research-Daten bleiben in AWS leer,
der vollständige Live-Test kommt mit dem Research-Sync am Projektende. Die lokalen Postgres-Daten sind
Testdaten und werden verworfen. Budget 5 $ mit Warnungen bei 1 $ und 3 $; die Notbremse sperrt die
App- und Deploy-Rolle, nie die `Admin`-Rolle.
