# AWS Deployment Plan (Phase 4)

Companion to [`2026-09-20-aws-deployment-design.md`](../specs/2026-09-20-aws-deployment-design.md). Not a
task-by-task implementation plan in the usual SDD sense (see
[`2026-07-21-phase1-portfolio-foundation.md`](2026-07-21-phase1-portfolio-foundation.md) for that style) —
this is a live, topic-by-topic AWS teaching curriculum, executed directly in the AWS Console by the user
with the controller explaining each concept first. No code review/commit checkpoints per topic in the
usual sense; git commits for repo-tracked artifacts (this doc, the design spec, `docs/aws/`,
`backend/Dockerfile`, future GitHub Actions workflow files) remain the user's own action per the project's
standing git convention.

## Status

| # | Topic | Status |
|---|-------|--------|
| — | Account setup (Root/MFA, IAM user, `Admin` role, AWS Budgets + Budget Action) | ✅ done |
| — | EC2 + Auto Scaling + ALB (side quest, not part of the architecture) | ✅ done, fully torn down |
| 1 | IAM | ✅ done (covered live during account setup) |
| 2 | VPC & Security Groups | ✅ done — `value-screener-vpc`, 2 private subnets, 2 security groups |
| 3 | RDS | ✅ done — `value-screener-postgres` created |
| 4 | Backend compute (ECS Fargate, classic — **not** App Runner, see below) | 🔄 in progress — `Dockerfile` added; next: ECR repo, GitHub OIDC role + workflow, ECS Cluster/Task Definition/Service, API Gateway + VPC Link |
| 5 | Lambda (Company Research Agent, **manual trigger only**, no EventBridge for the real agent) **+ public showcase demo** (DynamoDB, rate-limited Lambda button, EventBridge heartbeat shown in UI — added 2026-09-22) **+ SQS DLQ on every Lambda** (pulled forward from Topic 10, added 2026-09-22) | ⏳ not started |
| 6 | S3, CloudFront, ACM, Route 53 (frontend) | ⏳ not started |
| 7 | Secrets Manager & KMS | ⏳ not started (RDS's Secrets-Manager-managed credential already exists as a head start) |
| 8 | Cognito (replace shared admin Basic Auth) | ⏳ not started |
| 9 | CloudTrail | ⏳ not started |
| 10 | SQS/SNS | 🔄 DLQ part pulled forward into Topic 5 (real reliability need, not worth deferring); remaining SNS content (e.g. notify-on-daily-rate-limit-hit) and a possible SQS producer/consumer demo still open here |
| 11 | X-Ray | ⏳ not started |
| 12 | CodePipeline/CodeBuild/CodeStar Connections | ⏳ not started — note: GitHub Actions + OIDC (originally slotted here) was pulled forward into Topic 4 out of necessity; revisit here whether CodePipeline still adds distinct exam/portfolio value on top of that, or whether it's now redundant |
| 13 | AWS Budgets & Cost Anomaly Detection | ✅ done (done as part of account setup, ahead of the original curriculum order) |
| 14 | AWS CDK (infrastructure as code) | ⏳ not started |
| 15 | WAF/GuardDuty/Config overview (bounded, not continuous) | ⏳ not started |

## 2026-09-22 course correction — read this before resuming Topic 4

Two things changed the plan, both explained in full in the design spec's Decision Log:

1. **App Runner is closed to new AWS customers since 2026-04-30.** This account is newer than that, so
   Topic 4 cannot use App Runner at all — not a Free Plan issue, a hard account-age blocker. Replaced with
   **ECS Fargate, built classically** (not ECS Express Mode — that auto-creates its own ALB, not worth it
   for one service, and building by hand teaches more). EC2 was seriously considered too (cheaper, ~6 vs
   ~10–11 $/month, and reuses the EC2 side-quest's hands-on knowledge) but ECS was chosen for now
   specifically for its higher learning value, with an explicit, recorded intent to reconsider EC2 once
   real Paid-Plan cost pressure hits — see the design spec Section 6 for the full reasoning.
2. **No EventBridge Scheduler needed for v1.** `2026-07-30-screening-cost-redesign-design.md`'s own
   decision log deferred the automatic daily research run on 2026-08-10 — this deployment plan had been
   implicitly ignoring that and still planning Topic 5 as "Lambda + automatic daily trigger." Corrected:
   Topic 5 is now just the Company Research Agent Lambda with a manual trigger path, no EventBridge.

## Topic 4 (backend compute) — remaining steps

1. ~~Write `backend/Dockerfile`~~ — done.
2. Create an ECR repository (`value-screener-backend`).
3. Register GitHub as an OIDC identity provider in IAM (one-time, account-wide).
4. Create an IAM role trusting that OIDC provider, scoped to this repo (and ideally to the `v*` tag ref
   pattern), with permission to push to the ECR repository.
5. Add a GitHub Actions workflow (`.github/workflows/...`) triggered on `v*` tag push: checkout → build
   the Docker image → assume the OIDC role → `docker push` to ECR.
6. Create the ECS Cluster (lightweight — just a logical grouping for Fargate).
7. Create the Task Definition: ECR image, CPU/memory (smallest — 0.25 vCPU/0.5 GB), port mapping, two IAM
   roles (Task Execution Role — pull from ECR, write logs; Task Role — used by the running app, e.g. to
   read the RDS Secrets Manager secret, later to invoke the Company Research Agent Lambda), environment
   configuration pointing at RDS.
8. Create the ECS Service: Fargate launch type, both private subnets, a new security group for the tasks
   (replaces the never-built `value-screener-apprunner-connector-sg` role — same idea, referenced by RDS's
   security group inbound rule instead), desired count 1, Rolling deployment (default).
9. Add the VPC Interface Endpoints needed now that the task has no route out of the VPC (Secrets Manager
   confirmed necessary; Lambda if/when the backend invokes the Company Research Agent directly — confirm
   at Topic 5).
10. Create API Gateway (HTTP API) + VPC Link pointing at the ECS service, as the public ingress — no new
    public subnet needed, the VPC Link's ENIs go in the existing private subnets.
11. Extend the GitHub Actions workflow to trigger a new deployment after pushing the image (ECS has no
    App-Runner-style automatic-redeploy-on-new-image toggle — this must be explicit, e.g.
    `aws ecs update-service --force-new-deployment`).
12. First real end-to-end test: tag a release, watch the pipeline build/push/deploy, confirm the backend
    reaches RDS through the task's security group and is reachable via the API Gateway URL.

## Topic 5 — Lambda & EventBridge, plus the showcase demo (added 2026-09-22)

Full design reasoning in the spec, Section 7. Two independent pieces:

**A) The real Company Research Agent Lambda** — deploy `company-research-agent/`, wire it to the existing
manual/operator-controlled workflow only. No EventBridge Scheduler. Attach an **SQS Dead Letter Queue**
(pulled forward from Topic 10 — a genuine reliability need, not an artificial demo) so failed invocations
are captured and inspectable instead of silently lost.

**B) Public showcase demo** (separate, self-contained, cannot affect the real agent's cost):
1. Create one DynamoDB table (button rate-limit counter + EventBridge heartbeat item).
2. Write a small stub Lambda for the `DeepResearchPlaceholder` button: checks/increments the daily
   counter in DynamoDB, returns the case-study-style English response text (with a live timestamp) or a
   polite rate-limit message once the daily cap is hit.
3. Add an API Gateway (HTTP API) route with direct Lambda integration for that button (same API Gateway
   as Topic 4's ECS ingress, a second integration type on it), with request throttling configured (low
   rate/burst limit).
4. Create the EventBridge rule (`rate(1 day)`) + its target Lambda, which overwrites the DynamoDB
   heartbeat item.
5. Add a second API Gateway route + read-Lambda that returns the current heartbeat; wire the frontend to
   call it on page load and display it near the architecture explanation on the landing page.
6. Wire the (currently purely static) `DeepResearchPlaceholder` frontend component to actually call its
   API Gateway route instead of just showing fixed text.

## Deutsche Kurzfassung

Verfolgt den Fortschritt der laufenden AWS-Schulung (Phase 4). Erledigt: Account-Setup, VPC, RDS,
EC2-Exkurs (Lernübung, kein Teil der Architektur). **Kurskorrektur 2026-09-22:** App Runner nimmt seit
30.04.2026 keine Neukunden mehr an (unser Account ist neuer) — Ersatz ist **ECS Fargate, klassisch selbst
gebaut** (nicht Express Mode, das einen eigenen, für uns unnötig teuren Load Balancer erzwingen würde);
reines EC2 wäre günstiger und wurde bewusst zugunsten des höheren Lernwerts von ECS vertagt, nicht
verworfen. Außerdem entdeckt: der ursprünglich für Thema 5 vorgesehene automatische tägliche
EventBridge-Scheduler war laut einer schon am 2026-08-10 in der Screening-Cost-Redesign-Spec getroffenen
Entscheidung nie für v1 vorgesehen — Thema 5 braucht nur den Company-Research-Agent mit manuellem Trigger.
**Neu ergänzt (2026-09-22):** Thema 5 bekommt zusätzlich eine öffentliche, kostensichere Showcase-Demo —
ein DynamoDB-gestützter, ratenbegrenzter "Tiefenrecherche"-Button (API-Gateway-Throttling + Tages-Limit
in DynamoDB, niemals der echte, teure Agent) mit einem auf Bewerbungen zugeschnittenen Antworttext, plus
ein tägliches EventBridge-Rule, dessen Ergebnis sichtbar auf der Landingpage angezeigt wird — komplett
getrennt vom echten Agenten, kann dessen Kosten nicht beeinflussen.

Aktuell in Arbeit (Thema 4): Dockerfile steht, als Nächstes ECR-Repository, GitHub-OIDC-Rolle,
GitHub-Actions-Workflow, ECS Cluster/Task Definition/Service, VPC-Endpoints, API Gateway + VPC Link als
öffentlicher Zugang. Noch offen: Lambda (nur manueller Trigger), Frontend (S3/CloudFront/ACM/Route 53),
Secrets Manager/KMS, Cognito, CloudTrail, SQS/SNS, X-Ray, CodePipeline (Sinnhaftigkeit neu bewerten), CDK,
sowie ein kurzer, bewusst befristeter WAF/GuardDuty/Config-Überblick.
