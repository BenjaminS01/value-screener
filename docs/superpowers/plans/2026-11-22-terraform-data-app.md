# Terraform Data + App (Part 3) Implementation Plan

> **Execution:** teaching session, inline (superpowers:executing-plans), no subagents. Claude writes each file
> test-first and explains it in simple German. User actions: **exactly one step per message**, then wait. No AWS
> change without the user's explicit go per apply. Steps use checkbox (`- [ ]`) syntax.
>
> **Two environments:** sandbox (this WSL, Claude): Terraform `init -backend=false`, `fmt`, `validate`, `test`
> with `mock_provider`; Maven tests for Task 1. Ops environment (user only): AWS CLI, `init` with backend, `plan`,
> `apply`, uploads. The user does all git operations; Claude gives the commands.

**Goal:** the app runs live: DynamoDB table and SSM parameters (`infra/data`), both Lambdas behind HTTP API and
CloudFront with the SPA in S3 (`infra/app`), smoke-tested as USER and ADMIN, then deployed by CI on version tags.

**Architecture:** two new stacks with their own state (`data/terraform.tfstate`, `app/terraform.tfstate`) in the
existing state bucket. `app` finds `data` and `foundation` resources **by their fixed names via data sources**
(no `terraform_remote_state`). Terraform owns configuration; CI owns code (Lambda code and alias version are
`ignore_changes`).

**Tech Stack:** Terraform ≥ 1.15, AWS provider `~> 6.23`, `random` provider (origin secret), Java 21 Lambdas
(Spring Boot backend with SnapStart, Quarkus deep-analysis), GitHub Actions with OIDC.

**Spec:** `docs/superpowers/specs/2026-11-14-serverless-terraform-design.md` (sections 6.3, 7–12) and the handoff
`docs/superpowers/plans/2026-11-21-terraform-app-handoff.md`.

## Global Constraints

- Same as part 2: `required_version = ">= 1.15"`, `aws ~> 6.23`, region `eu-central-1`, `default_tags`
  `project = "value-screener"`, S3 backend with `use_lockfile = true`, bucket from gitignored `backend.hcl`,
  personal values only in gitignored `terraform.tfvars` (committed `*.example`).
- Buckets: versioning, SSE-S3, four public-access blocks, TLS-only bucket policy.
- **No reserved concurrency** on any Lambda (quota 10, user decision).
- Log groups created by Terraform, retention `14` days; Lambda roles get no `logs:CreateLogGroup`.
- Secrets never on the command line, in the repo, or in state (exception, accepted in design 7.3: origin secret).
- Tests: `infra/<stack>/tests/*.tftest.hcl`, `command = plan`, `mock_provider "aws"` (+ `mock_data`). Before each
  commit in the sandbox: `terraform fmt -check -recursive infra`, then `validate` and `test` in the stack.

## Decisions taken in this plan (please confirm)

1. **How `app` reads `data`/`foundation`:** data sources by fixed name (`aws_dynamodb_table "value-screener"`,
   `aws_iam_role "value-screener-backend"` / `"value-screener-github-deploy"`, `aws_s3_bucket
   "value-screener-artifacts-<account>"`). Reason: no coupling to another stack's state file, easy to mock in tests.
2. **One table** `value-screener` (as in `TableDefinition.java`): `pk`/`sk` String, provisioned **20/20**, TTL on
   `ttl`, PITR, deletion protection, `prevent_destroy`, auto scaling off. Drift guard (design 6.3): a backend test
   reads `infra/data/dynamodb.tf` and compares name, keys, TTL attribute and capacity with `TableDefinition`.
3. **SSM in `data`** (user-supplied, placeholder `CHANGE_ME` written via the write-only `value_wo` with `value_wo_version = 1`, `SecureString`;
   changed 2026-11-22 from `ignore_changes = [value]`, because a refresh would still copy the real console value
   into state, while with `value_wo` the provider stores no value at all):
   `/value-screener/app.admin.password-hash`, and per extra user `<n>` < `var.extra_user_count` (default `1`, the
   USER account for the smoke test) `/value-screener/app.users.<n>.username` and `.password-hash`.
   **Origin secret in `app`:** `random_password` (48 chars, no special characters) → SSM
   `/value-screener/app.security.origin-verify.secret` and the CloudFront origin header. It lives with its only
   two consumers; rebuilding `app` rotates both together.
4. **Lambda code:** fixed S3 keys in the artifact bucket, `backend/backend.zip` and
   `deep-analysis/deep-analysis.zip`. The user uploads the first zips by hand before the first `app` apply. Functions
   have `publish = true`; `ignore_changes = [s3_key, s3_object_version, source_code_hash]`; alias `live` has
   `ignore_changes = [function_version]` (CI moves it).
5. **Backend Lambda:** name `value-screener-backend`, role from `foundation`, `java21`, handler
   `com.valuescreener.lambda.StreamLambdaHandler::handleRequest`, 1024 MB, timeout 29 s,
   `snap_start { apply_on = "PublishedVersions" }`, env `SPRING_PROFILES_ACTIVE=aws`, `ADMIN_USERNAME`
   (`var.admin_username`, tfvars), `APP_DYNAMODB_TABLE_NAME=value-screener`.
   **Deep-analysis Lambda:** name `deep-analysis-demo`, own role `value-screener-deep-analysis` (logs only),
   `java21`, handler `io.quarkus.amazon.lambda.runtime.QuarkusStreamHandler::handleRequest`, 512 MB, 10 s, no
   SnapStart, alias `live`.
6. **HTTP API:** routes `ANY /api/{proxy+}` → backend alias `live` (payload 1.0 since 2026-11-23, see Task 7 result); stage `$default`, auto-deploy,
   throttling rate `5`, burst `10` (user decision 2026-11-23, see Task 6 result); no access logs. Lambda permission scoped to the alias and this API.
7. **CloudFront:** one distribution, default certificate, `PriceClass_100`, `default_root_object = "index.html"`.
   - Default behaviour → frontend bucket `value-screener-frontend-<account>` via OAC, managed policy
     `CachingOptimized`.
   - `/api/*` → HTTP API, managed `CachingDisabled`, all methods, own origin request policy: all cookies, all query
     strings, headers `Accept`, `Content-Type`, `X-XSRF-TOKEN`, `CloudFront-Viewer-Address`. Origin custom header
     `X-Origin-Verify` (CloudFront overwrites a viewer-sent one).
   - **No custom error responses / SPA fallback:** the app has no router, and a distribution-wide 403/404 rewrite
     would also turn API 403/404 into `index.html`.
   - Response headers policy: HSTS (1 year), `nosniff`, frame `DENY`, referrer `same-origin`, a CSP checked against
     the built `dist/index.html`. Checked 2026-11-23: one module script from `/assets`, no inline script/style,
     no images, nothing external → strict CSP `default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self';
     connect-src 'self'; font-src 'self'; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors
     'none'` (React `style={}` props set styles via the DOM, which CSP allows). Managed cache policies by their fixed
     AWS ids, not looked up.
8. **Deploy role rights (attached in `app`):** on both functions `lambda:GetFunction`,
   `GetFunctionConfiguration`, `UpdateFunctionCode`, `PublishVersion`, `UpdateAlias`; frontend bucket
   `s3:ListBucket`, `GetObject`, `PutObject`, `DeleteObject`; `cloudfront:CreateInvalidation` on the distribution.
   **Backend role rights** (narrowed 2026-11-22 to what the code calls with `create-table: false`): DynamoDB
   `GetItem`, `PutItem`, `UpdateItem`, `DeleteItem`, `Query` on the table; SSM `GetParametersByPath` on
   `parameter/value-screener` and `parameter/value-screener/*`; `lambda:InvokeFunction` on `deep-analysis-demo:live`
   only; logs `CreateLogStream`/`PutLogEvents` on its log group. (No `kms:Decrypt` needed with the `aws/ssm` key.)
9. **Secrets before the backend's first published version:** values read at SnapStart init are in the snapshot. The
   user enters the SSM values in the console right after the `data` apply (Task 3), before Task 5 publishes version 1.
   Any later change to an SSM value needs a new deploy (design 7.4). Likewise a Terraform config change (env) only
   reaches `live` with the next CI deploy, because the alias version is ignored.

## Review Focus

- A request straight to the `execute-api` URL (bypassing CloudFront) → 403. Check in Task 7 with `curl`.
- A viewer sends its own `X-Origin-Verify` or `CloudFront-Viewer-Address` → CloudFront overwrites both. Check: tftest
  in Task 7 asserts the origin custom header and the header list; live login throttling sees the real IP.
- An API 403/404 (e.g. not logged in) reaches the SPA as JSON, not as `index.html`. Check: tftest in Task 7 asserts no
  `custom_error_response`; live `curl https://<cf>/api/does-not-exist` gives the backend's status.
- `terraform destroy` in `app` never touches the table or user secrets (they live in `data`, `prevent_destroy` +
  deletion protection). Check: Task 4 plan shows no `aws_dynamodb_table`.
- A later `terraform apply` in `app` must not move alias `live` back or replace deployed code. Check: plan after the
  first CI deploy (Task 8) shows `No changes` for functions and aliases.

---

### Task 1: Branch + session hardening (backend)

**Files:** Modify: `backend/src/main/java/com/valuescreener/security/session/DynamoSessionRepository.java`,
`SerializationSupport.java`; Test: `backend/src/test/java/com/valuescreener/security/session/DynamoSessionRepositoryTest.java`,
`SessionEndToEndTest.java`, new `SerializationSupportTest.java`.

- [ ] User: `git switch -c feature/terraform-data-app`.
- [ ] Tests first (each must fail before the change):
  - `saveSkipsWriteWhenIdChangeTransactionConflicts`: mocked `DynamoDbClient` throws `TransactionCanceledException`
    with reason `TransactionConflict` → no exception, `storedId` unchanged. Same for `putItem` throwing
    `TransactionConflictException` (`saveSkipsWriteWhenPutConflictsWithTransaction`). Never revives.
  - `findByIdTreatsItemWithoutTimestampsAsMissing`: item with only `pk`/`sk`/`attrs` → `null` (one case each for
    missing `created`, `lastAccessed`, `maxInactiveSeconds`).
  - `deserializeRejectsDeepNesting` (100 nested `HashMap`s) and `deserializeRejectsOversizedInput` (> limit bytes)
    → `InvalidClassException`; `deserializeAcceptsRealSecurityContext` (logged-in context) still works.
  - `saveNewSessionUsesAttributeNotExists`: a new session whose id already exists with **the same `created`** →
    success (SDK retry); with a different `created` → `IllegalStateException` (collision, 500 is fine: 128-bit ids).
  - `SessionEndToEndTest.storedSessionHasMaxInactiveSecondsOf7200`.
- [ ] Implement: catch `TransactionConflictException` and cancellation reason `TransactionConflict` like the
  condition-failed case; missing timestamp attributes → `null` with a warn log; filter prefix
  `maxdepth=20;maxrefs=1000;maxbytes=16384;maxarray=4096;` in front of the allow-list (also covers throttle state,
  check its tests stay green); new-session put with `attribute_not_exists(pk)` + consistent `GetItem` check.
- [ ] `cd backend && mvn -B test` → all green (537 + new). User commits
  (`fix(session): harden dynamo session store before going live`).

### Task 2: `infra/data` skeleton + table

**Files:** Create: `infra/data/versions.tf` (key `data/terraform.tfstate`), `backend.hcl.example`,
`dynamodb.tf` (`aws_dynamodb_table.main`, decision 2), `outputs.tf` (`table_name`, `table_arn`),
`tests/dynamodb.tftest.hcl`; backend test `persistence/TableDefinitionTerraformTest.java`.

- [ ] Failing tftest: name `value-screener`, `billing_mode = "PROVISIONED"`, read/write `20`, `hash_key = "pk"`,
  `range_key = "sk"`, TTL enabled on `ttl`, PITR enabled, `deletion_protection_enabled = true`.
- [ ] Failing Java test: parses `../infra/data/dynamodb.tf` (regex on the assignments) and compares with
  `TableDefinition` constants and the default table name.
- [ ] Write `dynamodb.tf` with `lifecycle { prevent_destroy = true }`; sandbox checks + `mvn -B test` green.
- [ ] User (one step each): create `backend.hcl` → `terraform init -backend-config=backend.hcl` → `plan`
  (expected `1 to add`) → go → `apply` → commit.

### Task 3: SSM parameters in `data`

**Files:** Create: `infra/data/ssm.tf`, `variables.tf` (`extra_user_count`, number, default `1`, `>= 0`),
`tests/ssm.tftest.hcl`.

- [ ] Failing tftest: with `extra_user_count = 1` exactly 3 parameters with the names from decision 3, all
  `SecureString`, `value_wo_version = 1`; with `0` only the admin hash; `-1` rejected.
- [ ] Write `ssm.tf` (`value_wo`); sandbox checks green.
- [ ] User: `plan` (expected `3 to add`) → go → `apply`.
- [ ] User enters the real values in the console (Systems Manager → Parameter Store → edit), one parameter per
  message. Hashes from `AdminPasswordHashGenerator` (see `README.md`), never pasted into chat. Commit.

**Results Tasks 1–3 (2026-11-22):** session hardening done (backend 548 → 552 tests with the drift test). Table and
3 parameters applied. User generated the bcrypt hashes with `htpasswd -nBC 12` (own tool, `$2y$`, accepted by
`AccountRegistry`) and entered all values in the console. Proof that no value is in state: after
`terraform apply -refresh-only` (parameter versions 1 → 2 in state) the state holds `value = ""` for all three and
no 60-character string (counted by length only, nothing printed). Lesson: `terraform plan` refreshes in memory only;
a state check needs `apply -refresh-only` first.

### Task 4: `infra/app` skeleton, log groups, deep-analysis Lambda

**Files:** Create: `infra/app/versions.tf` (key `app/terraform.tfstate`, providers `aws`, `random`),
`backend.hcl.example`, `lookups.tf` (data sources, decision 1; `variables.tf` with `admin_username` and
`terraform.tfvars.example` moved to Task 5, the first task that needs them), `deep_analysis.tf` (role, log-group policy, log group, function, alias), `tests/deep_analysis.tftest.hcl`.

- [ ] Failing tftest: function `deep-analysis-demo`, runtime `java21`, handler from decision 5, memory `512`,
  timeout `10`, no `reserved_concurrent_executions`, `publish = true`, log group `/aws/lambda/deep-analysis-demo`
  retention `14`, alias `live`.
- [ ] Write files; sandbox checks green.
- [ ] User: build `cd deep-analysis && mvn -B package` → upload `target/function.zip` to
  `s3://<artifact bucket>/deep-analysis/deep-analysis.zip` → `init` → `plan` → go → `apply` →
  `aws lambda invoke --function-name deep-analysis-demo:live` with `{"isin":"US0378331005"}` (payload from a file)
  → expected "Noch nicht implementiert". Commit.

**Result Task 4 (2026-11-22):** applied (5 resources). Zip hand-over: the user builds in the sandbox, copies to
`~/dev/transfer` (seen in aws-ops as `/home/ops/transfer`, read-only), compares `sha256sum` on both sides, then
`aws s3 cp` to the artifact bucket in aws-ops (user chose this over Terraform reading the file directly: 50 MB
direct-upload limit, CI fit). `aws lambda invoke --region eu-central-1 deep-analysis-demo:live` → 200, version 1,
`NOT_IMPLEMENTED` answer. Note: with credentials from `export-credentials`, AWS CLI calls need `--region eu-central-1`.

### Task 5: Backend Lambda + its permissions + origin secret

**Files:** Create: `infra/app/backend.tf` (log group, function, alias, inline policies on the foundation role),
`origin_secret.tf` (`random_password.origin_verify`, SSM parameter), `tests/backend.tftest.hcl`.

- [ ] Failing tftest: values from decision 5 (incl. `snap_start`, env vars), log group retention `14`, policy
  actions from decision 8 with resources limited to the table, the SSM prefix, `deep-analysis-demo:live` and the
  log group; origin parameter is `SecureString` with the exact name.
- [ ] Write files; sandbox checks green.
- [ ] User: `mvn -B package -DskipTests` in `backend` → upload the Lambda zip to `backend/backend.zip` → `plan` →
  go → `apply` (SnapStart publish takes a few minutes) → CloudWatch log of version 1 shows Spring started without
  errors. Commit.

**Result Task 5 (2026-11-23):** applied (6 resources); backend zip 45.7 MB via the transfer folder + `aws s3 cp`.
Version 1 went `Pending` → `Active` (SnapStart snapshot takes a few minutes after the apply). Init log: Spring
started in 7.5 s, `Seeded 35 stock groups`. Note: `ADMIN_USERNAME` appears in plan output and state (not secret by
design).

### Task 6: HTTP API

**Files:** Create: `infra/app/api.tf` (`aws_apigatewayv2_api`, integration on the backend alias ARN, route,
stage, `aws_lambda_permission` with `qualifier = "live"`), `tests/api.tftest.hcl`; output `api_endpoint`.

- [ ] Failing tftest: protocol `HTTP`, payload `1.0`, route key `ANY /api/{proxy+}`, stage `$default` with
  `throttling_rate_limit = 5`, `throttling_burst_limit = 10`, permission qualifier `live`.
- [ ] **Cost check (flood on the `execute-api` URL):** brute-forcing the origin secret is not a risk (48 random
  alphanumeric chars ≈ 2²⁸⁶, constant-time compare), but a flood of requests that only get 403 is still billed. At
  rate 10 req/s that is ≈ 26 M requests/month, roughly 20–30 $ (HTTP API + Lambda); the budget deny action stops
  Lambda invokes, not API Gateway charges. Before the apply, check in the AWS docs whether HTTP API bills requests
  rejected by throttling, and review the limits (lower rate? burst?). Result and decision go into the plan and
  `PROJECT-STATUS.md`.
- [ ] Write; sandbox checks green. User: `plan` → go → `apply` → `curl -i <api_endpoint>/api/auth/me` → `403`
  (no origin header: proves the filter). Commit.

**Result Task 6 cost check (2026-11-23):** AWS docs do not say whether HTTP API bills requests rejected by
throttling (pricing: "API calls you receive"; throttles are "targets", best effort), so assume yes. Month-long flood
estimate incl. Lambda: ~35 $ at 10/s, ~17 $ at 5/s → user chose rate 5 / burst 10. The flood risk is the same via
CloudFront; the direct `execute-api` URL is not revealed by CloudFront. Budget alert at 1 $ fires within about a day.
Emergency switch (documented in `api.tf`): `terraform destroy -target=aws_apigatewayv2_api.main` (new URL on
re-create). Later option (backlog): CloudFront flat-rate Free plan with WAF; Free-tier account eligibility is
contradictory in the docs.

**Result Task 6 (2026-11-23):** applied (5 resources). `curl` on `<api_endpoint>/api/auth/me` without the origin
header → `403` from the backend (proves route, permission, alias, SnapStart restore and the origin filter).

### Task 7: Frontend bucket + CloudFront, first live test

**Files:** Create: `infra/app/frontend.tf` (bucket + OAC + bucket policy for this distribution only),
`cdn.tf` (origin request policy, response headers policy, distribution), `tests/cdn.tftest.hcl`; outputs
`frontend_bucket_name`, `cloudfront_domain`, `distribution_id`.

- [ ] Failing tftest: everything in decision 7 (two origins, `/api/*` behaviour with `CachingDisabled` and the
  header list, origin custom header `X-Origin-Verify`, no `custom_error_response`, HSTS/`nosniff`/`DENY`), bucket
  policy principal `cloudfront.amazonaws.com` with `AWS:SourceArn` = the distribution.
- [ ] Write; check the CSP against `frontend/dist/index.html`; sandbox checks green.
- [ ] User: `plan` → go → `apply` (distribution takes ~5 min) → `npm ci && npm run build` → `aws s3 sync dist/`
  to the bucket → open `https://<cloudfront_domain>` → smoke test as USER, then as ADMIN (design goal 7) → the
  Review Focus `curl` checks. Commit.

**Task 7 live bug and fix (2026-11-23):** login showed the app, the next click got 401 ("Sitzung abgelaufen").
Evidence: browser kept only `XSRF-TOKEN`; the login response had one `set-cookie` (the cleared `XSRF-TOKEN`), no
`JSESSIONID`. Root cause (read in the adapter source): for HTTP API v2 the adapter 2.0.3 writes `headers` with
`getFirst` per name plus `multiValueHeaders`; API Gateway format 2.0 ignores `multiValueHeaders`. Fix (user chose A):
payload format 1.0 in `api.tf` and `getAwsProxyHandler` in `StreamLambdaHandler`; `StreamLambdaHandlerTest` now sends
1.0 events and logs in end-to-end (both cookies present, `/api/auth/me` 200 with the session). Backend 553 tests.

**Result Task 7 (2026-11-24):** applied (10 resources + payload change). Frontend built in the sandbox, handed over as
`frontend-dist.tar.gz` via the transfer folder, extracted in aws-ops into a fresh `~/frontend-dist`, `aws s3 sync
--delete`. Backend fix deployed by hand: `update-function-code` → `publish-version` (version 2, SnapStart) →
`update-alias live` → 2. Checks: direct API 403, via CloudFront 401; login as USER and ADMIN works (`JSESSIONID` and
`XSRF-TOKEN` set), buying a position works; `/api/gibt-es-nicht` → 401 JSON (no index.html); HSTS, CSP, frame DENY,
nosniff, referrer same-origin present; 35 groups in the table (they show in the UI only once research snapshots
exist: by design of `grouping.ts` and the portfolio group column).
**Backlog (later, not this plan):** viewer TLS minimum is `TLSv1` with the default `*.cloudfront.net` certificate
(only changeable with an own domain + ACM certificate); CloudFront flat-rate Free plan with WAF (eligibility unclear).

### Task 8: CI deploy workflow

**Files:** Create: `infra/app/deploy_role.tf` (inline policy on the foundation deploy role, decision 8),
`tests/deploy_role.tftest.hcl`, `.github/workflows/deploy.yml`. Delete: `.github/workflows/build-and-push-backend.yml`.

- [ ] Failing tftest on the policy actions/resources; write; sandbox checks; user `plan` → go → `apply`.
- [ ] `deploy.yml` on `push: tags: ['v*']`, `permissions: id-token: write, contents: read`, role ARN and names from
  GitHub repository **variables** (not in the file): tests of all three modules → build zips → upload →
  `update-function-code` → `aws lambda wait function-updated` → `publish-version` → `update-alias live` (both
  functions) → frontend build → `s3 sync --delete` → `create-invalidation /*`.
- [ ] User sets the repository variables, merges, pushes tag `v3.0.0` → workflow green → live app still works →
  `terraform plan` in `app` shows `No changes` (Review Focus).

### Task 9: Docs and handoff

**Files:** Modify: `PROJECT-STATUS.md` (top section, English + German summary), `README.md` (deploy),
`docs/aws/aws-architecture-overview.md` (serverless), `deep-analysis/README.md` (deployment), design section 15
list. Create: next handoff; update memory `project-entry-point`.

- [ ] Claude writes; user reviews, commits, opens the PR; `aws logout`.

## Deutsche Kurzfassung

Plan für Teil 3 in neun Schritten: zuerst die Session-Härtung im Backend (Konflikte, kaputte Einträge, Größen-Limits
beim Einlesen, neue Sessions ohne Überschreiben). Dann `infra/data` mit der einen DynamoDB-Tabelle (20/20, TTL,
Sicherung, Löschschutz; ein Test vergleicht sie mit dem Java-Code) und den SSM-Parametern, deren echte Werte du in
der Konsole einträgst, bevor das Backend zum ersten Mal veröffentlicht wird. Danach `infra/app` Stück für Stück:
Deep-Analysis-Lambda, Backend-Lambda mit Rechten und geheimem Origin-Header, HTTP API mit Drosselung, S3 und
CloudFront mit erstem Live-Test als USER und ADMIN, zum Schluss der Deploy-Workflow für Versions-Tags und die Doku.
`app` findet die Tabelle und die Rollen über ihre festen Namen, nicht über den State anderer Stacks. Du führst alle
AWS-Schritte aus, jeweils einen pro Nachricht.
