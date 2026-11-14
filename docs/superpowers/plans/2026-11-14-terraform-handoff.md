# Handoff: start of the Terraform phase (2026-11-14)

> **Superseded (2026-11-14, same day):** the open questions below were decided in
> [`../specs/2026-11-14-serverless-terraform-design.md`](../specs/2026-11-14-serverless-terraform-design.md)
> (DynamoDB + Lambda + CloudFront, everything new in Terraform). Next work:
> [`2026-11-14-serverless-backend-rewrite.md`](2026-11-14-serverless-backend-rewrite.md). Section 4
> (working mode) still applies.

For a new Claude session. Read this file completely, then `PROJECT-STATUS.md` (top section, v2 phase 3).
This file collects facts and open questions. **The user decides with the new session how to proceed**;
nothing below is decided unless it says so.

Suggested opening for the new session: *"Projekt value-screener, Branch main. Lies zuerst
docs/superpowers/plans/2026-11-14-terraform-handoff.md komplett, dann PROJECT-STATUS.md. Wir starten das
Terraform-Thema."*

## 1. Where we stand

**Application (all on `main`, merge `93d90bb`, PR #4):**
- v2 phases 1, 2 and 3 are done in code: login with ADMIN + USER accounts, portfolio, research area,
  investment thesis, history, and the deep-analysis demo (phase 3).
- Phase 3 = "Tiefenanalyse starten" button → backend `POST /api/research/snapshots/{isin}/deep-analysis`
  → atomic daily limits in Postgres (5 USER, 20 ADMIN, 100 global; migration V7) → synchronous
  `LambdaClient.invoke` (AWS SDK 2.58.0, region fixed `eu-central-1`, 10 s timeout, **no SDK retries**)
  of the Quarkus Lambda `deep-analysis-demo` (module `deep-analysis/`). Locally (profile `local`) a
  labelled stub answers instead.
- Specs: [`2026-11-12-v2-phase3-deep-analysis-lambda-design.md`](../specs/2026-11-12-v2-phase3-deep-analysis-lambda-design.md)
  (section "Later: Terraform" and the protection table list what Terraform must add),
  scope [`2026-11-03-app-scope-v2-design.md`](../specs/2026-11-03-app-scope-v2-design.md).

**AWS (built by hand in the console, live teaching sessions, 2026-09/10):**
- Details: [`2026-09-20-aws-deployment-design.md`](../specs/2026-09-20-aws-deployment-design.md),
  [`2026-09-20-aws-deployment-plan.md`](2026-09-20-aws-deployment-plan.md), `docs/aws/aws-architecture-overview.md`.
- Account on the Free Plan, `Admin` role via AssumeRole with fresh MFA, no IAM Identity Center (would
  force a plan upgrade). AWS Budgets with an automatic cost brake (`docs/aws/budget-deny-policy.json`).
  Cost cap about 20 €/month.
- `value-screener-vpc`: private only, two private subnets, no Internet/NAT gateway.
- RDS PostgreSQL `value-screener-postgres` (not public; credentials managed by RDS in Secrets Manager).
- ECR, ECS Fargate (`value-screener-cluster`, service `value-screener-backend-service`, task definition
  `value-screener-backend`, container `backend`), Cloud Map (private DNS), VPC Link v2, HTTP API
  (API Gateway) as public entry.
- VPC endpoints: **four interface endpoints in one AZ only** (Secrets Manager, `ecr.api`, `ecr.dkr`,
  CloudWatch Logs; cost reason, ≈7.30 $/endpoint/AZ/month) plus the free S3 gateway endpoint. Security
  group `value-screener-vpc-endpoints-sg`.
- GitHub Actions `build-and-push-backend.yml`: a **tag push** runs tests → image → ECR → new task
  definition revision (copied from the *current* one, only the image changes) → ECS deploy.
- **The backend running in AWS is the old tag `v0.1.0`** (before scope v2). Phases 1–3 were never
  deployed. The frontend is not in AWS at all (topic 6, S3/CloudFront, not started).

## 2. Facts the new session must know before touching AWS

1. **Do not push a new version tag yet.** The pipeline would deploy the v2 backend with the old task
   definition environment. v2 needs at least: `ADMIN_USERNAME`, `ADMIN_PASSWORD_HASH` (bcrypt; startup
   is refused without a valid hash), optional `APP_USERS_<n>_USERNAME/_PASSWORD_HASH`, `DB_URL`,
   `DB_USERNAME`, `DB_PASSWORD`; Flyway then migrates RDS through V1–V7. Never set
   `APP_RESEARCH_INGEST_*` in AWS (the ingest endpoint must not exist there). See `.env.example` and
   `backend/src/main/resources/application.yml`.
2. **Without the Lambda, `mode: lambda` (default) fails per click:** about 10 s, then 502, and one quota
   unit is used (no cost). Until the Lambda exists, set `APP_DEEP_ANALYSIS_MODE=stub` in the task
   definition, or create the Lambda first.
3. **The backend reaches AWS APIs only through VPC endpoints** (no NAT). Calling Lambda from ECS needs a
   way out: e.g. a Lambda interface endpoint (≈7–8 $/month per AZ, would be the fifth endpoint) or an
   alternative. Not decided; check against the 20 € cap.
4. **Protections still to build in AWS** (from the phase 3 spec): reserved concurrency (e.g. 2), Lambda
   function timeout ≤ 10 s, small memory, ECS task role with only `lambda:InvokeFunction` on
   `deep-analysis-demo`, log retention. The app-side limits already exist.
5. **Old plan text is superseded:** deployment plan topic 5 part B (DynamoDB counter, public API Gateway
   route to a stub Lambda, EventBridge heartbeat on the landing page) was replaced by v2 phase 3
   (decision 2026-11-12: limits in RDS, call via backend + AWS SDK, no DynamoDB yet; the job table comes
   with chat/summary). Topic 5 part A (real Company Research Agent) stays frozen.
6. **Curriculum topic 14 says "AWS CDK".** The user chose Terraform for infrastructure as code
   (phase 3 discussion, 2026-11-12). Whether CDK is still covered is open.

## 3. Open questions for the user (to discuss in the new session)

- Terraform scope: only new resources (Lambda, IAM, endpoint) or also bring the hand-built resources
  under Terraform (`terraform import` / `import` blocks), and in which order?
- Terraform state: where (local file vs. S3 backend with locking) and how this fits the Free Plan and
  the security rules.
- Note: the daily IAM user deliberately has **no access keys**; CLI access was planned via AWS
  CloudShell. Relevant for where Terraform runs.
- Who runs `terraform apply` and with which credentials (user via `Admin` role with MFA, or GitHub
  Actions via OIDC)? How does the Lambda artifact (Quarkus `function.zip`) get built and uploaded?
- Order: deploy the v2 backend first (with `mode: stub`), or Lambda first?
- Lambda access from the private VPC (see 2.3) and the cost check.
- Frontend hosting (topic 6) now or later.

## 4. Working mode (binding, unchanged)

- Answers in **German**, short and clear. If the user asks for "nur die Befehle", give only commands.
- **The user does all git operations.** Claude and subagents never run state-changing git; Claude gives
  the commands (short message, ending with the `Co-Authored-By` line from the system reminder).
- **Do not install tools** (this includes Terraform itself and the AWS CLI on the user's machine —
  ask the user; they install).
- Tokens and passwords never on the command line, never in the repo. `.env` holds only hashes. Never
  echo or probe secret environment variables. The sandbox never gets the ADMIN password.
- Never create, change or delete AWS resources without the user's explicit go. AWS work so far was a
  teaching format: explain each concept first, the user acts. Design approval is not a go to start.
- For code: subagent-driven development (implementer on Sonnet, reviewer, fix rounds, final review,
  the user commits after each task), with a German concept explanation before each task. The user is
  new to React; explain frontend concepts.
- Environment: WSL; Java 21 via
  `export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64; export PATH=$JAVA_HOME/bin:$PATH`. The user's
  IDE writes CRLF; review with `git diff --ignore-cr-at-eol`.
- Deferred to the very end of the project (do not re-suggest earlier): token renaming and the live
  research transfer test, see [`2026-11-08-v2-phase1-handoff.md`](2026-11-08-v2-phase1-handoff.md)
  section 3a.
- New project docs: English body + German summary.

## Deutsche Kurzfassung

Übergabe für eine neue Session zum Terraform-Thema. Die Anwendung ist bis Phase 3 fertig und auf `main`
gemergt: Login, Portfolio, Research und die Tiefenanalyse-Demo mit Tageslimits und Lambda-Aufruf per
AWS SDK. In AWS ist alles bisher von Hand gebaut: VPC ohne NAT, RDS, ECR, ECS Fargate, VPC Link, API
Gateway, vier Interface-Endpoints in einer AZ. Dort läuft noch das alte Backend `v0.1.0`, das Frontend
ist noch gar nicht in AWS. **Kein neues Versions-Tag pushen**, bevor die Task-Definition die
v2-Variablen hat. Ohne Lambda dort `APP_DEEP_ANALYSIS_MODE=stub` setzen. Der alte Topic-5-Plan
(DynamoDB, öffentliche Stub-Route, EventBridge) ist überholt. Offen und mit dem Nutzer zu besprechen:
was Terraform verwaltet (nur Neues oder auch Import), Ablage des States, wer `apply` ausführt, wie die
Lambda gebaut und hochgeladen wird, Reihenfolge, Weg aus dem privaten VPC zur Lambda samt Kosten,
Frontend-Hosting. Arbeitsweise wie bisher: Deutsch, git nur durch den Nutzer, nichts installieren,
keine AWS-Ressourcen ohne ausdrückliches Go.
