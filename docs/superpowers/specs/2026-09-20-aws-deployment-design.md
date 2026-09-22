# AWS Deployment Design (Phase 4)

Status: in progress, built incrementally as a live teaching session (see Decision Log). Supersedes the
"Tech-Stack & Deployment" section (Section 7) of
[`2026-07-21-value-screener-design.md`](2026-07-21-value-screener-design.md) with concrete, verified
decisions instead of a plan sketch.

## 1. Goals & constraints

- **Security first**, explicitly ranked above cost and above breadth of features.
- **Hard cost cap**: ~20 €/month once on a Paid Plan (currently on the AWS Free Plan, see Section 3).
- **Learning breadth**: the user is preparing for the AWS Developer Associate exam and wants hands-on
  exposure to as many relevant AWS services as the budget/security constraints allow.
- **Functional correctness**: the deployment must still serve the app's actual purpose (see
  [`2026-07-21-value-screener-design.md`](2026-07-21-value-screener-design.md) Section 2), not just be a
  service-collecting exercise.
- **No automatic daily Scheduler needed for v1** (re-discovered 2026-09-22 while auditing the full docs
  tree against this deployment plan): `2026-07-30-screening-cost-redesign-design.md`'s decision log
  already deferred the originally-planned automatic daily EventBridge-driven research run on 2026-08-10,
  specifically because it "incurs real ongoing cost" and was "the single largest remaining chunk of work
  before anything is demoable" — both directly opposed to the clarified primary goal (see next bullet).
  v1 needs only manual trigger paths into the Selection Logic funnel. This directly simplifies Topic 5 of
  this deployment's plan: the Company Research Agent Lambda is still needed, but **no EventBridge
  Scheduler** is needed yet.
- **Primary purpose, restated from the same decision log**: this is first a job-search/freelance
  portfolio piece (Java developer role + freelance clients) and a demonstration of the AWS Developer
  certification in progress, under an explicitly *hard* cost-minimization constraint — personal use as
  an investing tool is real but secondary. This creates a live, ongoing tension with the "learning
  breadth" goal above (more services/deeper exploration costs more time and often more money) that this
  document does not resolve once and for all — it recurs, and each time it does, the choice made and why
  is recorded in the Decision Log rather than silently picked.
- **Region**: `eu-central-1` (Frankfurt) — chosen because the user's own daily use is from Europe (the
  frequent load), an occasional Southeast-Asia showcase demo is the rare/one-off case, and it fits the
  project's existing EU/GDPR-conscious framing (Impressum, Art. 20 MAR). CloudFront's edge network keeps
  the frontend fast globally regardless of backend region.

## 2. Architecture overview

```
                                   INTERNET
                                       |
                 +----------------------+----------------------+
                 |                                              |
          (browser: frontend)                         (browser/API: backend)
                 v                                              v
          CloudFront + S3                     API Gateway (HTTP API) + VPC Link
           (frontend)                                    (ENIs in both private subnets)
                                                                 v
   =================== VPC: value-screener-vpc (10.0.0.0/16) ===================
   |  eu-central-1a: private-1a (10.0.1.0/24)   eu-central-1b: private-1b (10.0.2.0/24)  |
   |    ECS Fargate task (backend)  <---5432--->  rds-sg  -->  RDS PostgreSQL (Single-AZ) |
   |  no Internet Gateway, no NAT Gateway                                                 |
   ===============================================================================

   Company Research Agent: separate Lambda, MCP over Streamable HTTP, stays OUTSIDE the
   VPC (no direct RDS access, no NAT dependency), invoked by the backend via IAM SigV4
   auth (Function URL AuthType=AWS_IAM + resource policy scoped to the backend's task
   role) — no static API key. No automatic EventBridge trigger for v1 (see Section 1).
```

## 3. Account & identity model

- **AWS Free Plan** (chosen deliberately, not a leftover default): new AWS accounts (since July 2025)
  choose Free or Paid at signup. Free Plan cannot incur charges beyond the signup credit — worst case is
  account shutdown, not a bill — but expires after 6 months or when the credit runs out, whichever first.
- **IAM Identity Center was deliberately NOT enabled**: it requires an AWS Organization, and creating one
  on a Free Plan account immediately forces a Paid Plan upgrade and forfeits the remaining signup credit
  right away (verified against current AWS docs). Revisit once a Paid Plan upgrade is happening anyway
  (e.g. near go-live, when the 6-month window ends regardless).
- **Substitute identity model**, built to still avoid any standing admin-level credential and avoid ever
  using Root day to day:
  - Root: MFA active (authenticator app), used only for account-level actions, never day to day.
  - A daily IAM user (name intentionally not written in any doc — treat as sensitive): `PowerUserAccess`
    policy attached directly, own MFA device, **no access keys** — CLI/API access via AWS CloudShell or
    (for CI) GitHub OIDC, never static keys.
  - An assumable `Admin` IAM role (`AdministratorAccess`), trust policy scoped to that specific user's
    ARN (not the account-root ARN) with a Condition requiring `aws:MultiFactorAuthPresent: true` and
    `aws:MultiFactorAuthAge < 1200` (20 minutes) — assuming Admin needs a *fresh* MFA login, not just a
    standing permission.
- **AWS Budgets**: monthly cost budget (~20 €), alert thresholds at 40%/80% via email, plus an
  **automatic** Budget Action at 100% that attaches a Deny policy (`docs/aws/budget-deny-policy.json`) to
  the daily user, blocking further resource-creation actions across the services this project uses plus
  classic AWS cost traps — automatic rather than manual-approval, since the action only blocks new
  creation (non-destructive, reversible via `Admin`) and relying on someone noticing an approval email
  first would reintroduce the exact "might miss it" failure a hard cost cap exists to remove.

## 4. Network design

- Custom VPC `value-screener-vpc` (`10.0.0.0/16`) — chosen over the account's Default VPC specifically so
  that "database is unreachable from the internet" is a **structural** property (no route to any Internet
  Gateway exists in the private subnets) rather than resting solely on a single RDS configuration flag
  that could later be flipped by mistake. Defense in depth: two independent layers (network topology +
  RDS's own public-accessibility setting) instead of one.
- Two private subnets, `private-1a` (`10.0.1.0/24`, `eu-central-1a`) and `private-1b` (`10.0.2.0/24`,
  `eu-central-1b`) — two AZs because RDS requires a DB Subnet Group spanning at least two AZs even for a
  Single-AZ instance (keeps the option to enable Multi-AZ later without a network rebuild).
- **No Internet Gateway, no NAT Gateway** — neither RDS nor the App Runner VPC Connector's outbound path
  needs general internet access (see Section 6 for why this holds even after attaching the connector).
  NAT Gateway is the single most common AWS learner cost trap (~32–35 $/month just for existing); avoiding
  it structurally, not just by discipline, is a deliberate design goal.
- Two security groups, least-privilege and referencing each other by ID rather than by IP/CIDR:
  - `value-screener-apprunner-connector-sg` — empty inbound, default (allow-all) outbound.
  - `value-screener-rds-sg` — inbound TCP/5432 from `value-screener-apprunner-connector-sg` only; default
    outbound.

## 5. Database (RDS)

- Engine PostgreSQL, instance class `db.t4g.micro` (Graviton, cheaper than the `t3` equivalent), Single-AZ
  (Multi-AZ deliberately not enabled — doubles compute cost, not needed for a single-user app).
- Instance identifier `value-screener-postgres`; initial database name `value_screener` (matches
  `docker-compose.yml`'s `POSTGRES_DB` — instance identifier and DB name are separate fields with
  different naming rules, hyphen vs. underscore).
- **Public access: No.** Security group: `value-screener-rds-sg` only.
- Credentials: **"Managed in AWS Secrets Manager"** — RDS generates and stores the master password itself;
  it is never seen or typed by a human, and rotation is AWS-managed.
- Database authentication: **"Password and IAM database authentication"** enabled. The app uses password
  auth today (via the Secrets-Manager-managed secret); IAM token-based auth
  (`rds-db:connect`, 15-minute tokens) is left available as a future upgrade without needing to recreate
  the instance — not wired into the backend yet (would need a Postgres-side `GRANT rds_iam` and a
  token-refreshing datasource).
- Deletion protection: on. PostgreSQL log export to CloudWatch: on.

## 6. Compute — why not App Runner, why ECS (classic) over EC2 or ECS Express Mode

**App Runner is not available: it stopped accepting new customers on 2026-04-30.** This account was
created in September 2026, after that cutoff — confirmed via AWS's own guidance and independent reporting,
not a Free-Plan-specific restriction as first suspected. Existing App Runner customers are unaffected;
new ones (us) simply cannot create a service. AWS's own stated replacement recommendation is **Amazon ECS
Express Mode**.

**ECS Express Mode was considered and rejected in favor of building ECS "classically" (Cluster + Task
Definition + Service by hand).** Two reasons: (1) Express Mode auto-provisions a dedicated Application
Load Balancer per service, and a standalone ALB (~16–17 $/month base, before any usage-based LCU cost) was
judged not worth it for a single service with no ALB-sharing benefit — see the ingress decision below.
Fighting Express Mode's ALB assumption just to replace it would forfeit exactly the wizard's main value.
(2) Building classically, rather than through a wizard, is itself higher learning value — Task
Definition, the Task Role vs. Task Execution Role split (a common exam trap), Service configuration, and
`awsvpc` networking are all directly hand-configured rather than generated and hidden.

**EC2 (plain, the same pattern already exercised in the EC2/ASG/ALB side quest) was also seriously
considered as an alternative to ECS/Fargate**, and is **cheaper** for this specific workload
(`t4g.micro` ≈ 6 $/month vs. Fargate's smallest allocation, 0.25 vCPU/0.5 GB, ≈ 10–11 $/month) — a real,
non-trivial difference against the ~20 €/month cap. ECS/Fargate was chosen anyway for **now**, for
learning-breadth reasons specifically (see Section 1's stated tension): EC2 mechanics were already
covered in depth by the side quest, while ECS introduces genuinely new, exam-relevant concepts (Task
Definition, Task Role/Execution Role split, Service, `awsvpc` mode) that plain EC2 would not. **Deliberate
staged plan, not a one-time choice:** run ECS/Fargate now while the Free Plan signup credit absorbs the
cost delta; revisit switching the compute layer to EC2 specifically when real Paid-Plan cost pressure
arrives (6-month Free Plan window ending, or credit exhausted). This swap is judged low-risk and bounded
in scope — VPC, RDS, Secrets Manager integration, ECR, and the GitHub Actions build pipeline are all
unaffected by which compute layer runs the same ECR image; only the compute layer itself and the ingress
target (a Network Load Balancer would likely be needed for the VPC Link to reach a plain EC2 instance,
where Fargate integrates more directly) would change.

**The VPC Connector egress question (originally an App-Runner-specific finding) generalizes to ECS too:**
an ECS Fargate task placed in a private subnet, same as App Runner-with-connector, has no default route
out of the VPC (no Internet Gateway, no NAT Gateway) — so it needs the same resolution: **VPC Interface
Endpoints**, not a NAT Gateway. Confirmed by reading the actual backend code (not the possibly-stale
earlier design doc): `backend/src/main/java/com/valuescreener/` contains only `portfolio`, `research`
(pure snapshot persistence, not an outbound caller) and `security` — no Spring AI / Anthropic client
anywhere in `backend/`. All Anthropic-calling logic lives in the separately-deployed
`company-research-agent/` module (its own Lambda, stays outside any VPC, unaffected). The backend
therefore never calls a genuine third-party public endpoint directly — only its own RDS (in-VPC) and the
Company Research Agent Lambda (an AWS-internal call). **Conclusion unchanged: VPC Interface Endpoints
only** (Secrets Manager for certain; Lambda if the backend invokes the agent via
`lambda:Invoke`/`InvokeFunctionUrl`) — **no NAT Gateway needed anywhere in this architecture.**

**Ingress: API Gateway (HTTP API) + VPC Link, not an Application Load Balancer.** API Gateway HTTP APIs
are priced per request (≈1 $/million requests) with no fixed hourly charge, versus an ALB's fixed
~16–17 $/month base cost regardless of traffic — at this project's very low personal/showcase traffic
volume, the difference is roughly "a few cents" vs. "a fixed ~17 $" every month. The VPC Link places its
own ENIs directly in the existing private subnets (the same pattern as the App Runner connector would
have used), so **no new public subnet is needed** in `value-screener-vpc`.

**Deployment source: container image via ECR, not any native source-code build.** A `Dockerfile` was
added to `backend/` (multi-stage: Maven + Corretto 21 build stage, minimal Corretto 21 Alpine runtime
stage); the image is pushed to **ECR**, which the ECS Task Definition references directly.

## 7. Lambda & EventBridge — the real agent, plus a public, cost-safe showcase demo

**The real Company Research Agent** (`company-research-agent/`, already implemented and covering the
full Section 5 criteria catalog — see
[`2026-07-24-company-research-agent-design.md`](2026-07-24-company-research-agent-design.md)) deploys as
its own Lambda, invoked only via the existing manual, operator-controlled workflow (local Claude Code
sandbox research → manual upload via `/api/research/snapshots`, per the decision recorded in
`PROJECT-STATUS.md`'s "Auth-Modell für den research-company-Skill" section) — **never** reachable from an
unauthenticated public click. Real Anthropic web-search calls cost up to ~1 $ each; wiring that to a
public button would let any visitor generate unbounded real cost, which is precisely the risk the
manual-trigger workflow was already designed to avoid.

**Separately, added 2026-09-22 for portfolio/showcase value:** the existing `DeepResearchPlaceholder`
button (currently pure static text, no backend call at all) and EventBridge's presence in this
architecture both get made *genuinely live* — with a small, self-contained demo layer that cannot affect
the real agent or its cost profile:

- **One DynamoDB table** — no VPC involvement needed (Lambda has default internet/AWS-API access outside
  any VPC, same reasoning as the Company Research Agent staying outside the VPC). Holds two kinds of
  items: (a) a daily invocation counter for the public button (rate-limiting, below), (b) the latest
  EventBridge heartbeat (timestamp + message).
- **Public button → Lambda, via a dedicated API Gateway (HTTP API) route** (direct Lambda integration,
  not the VPC Link used for the ECS backend — same API Gateway, a second integration type on it).
  Two layers of protection against cost runaway, deliberately not AWS WAF (would reintroduce the ~5–8
  $/month ongoing cost this project has ruled out — see Section 8):
  1. **API Gateway request throttling** (built into HTTP APIs, no extra cost) — a low steady-state
     rate/burst limit (e.g. 2 req/s, burst 5) blocks a rapid request storm before it even reaches Lambda.
  2. **An application-level daily cap enforced by the Lambda itself against the DynamoDB counter** (e.g.
     50/day) — once exceeded, the function returns a polite "try again tomorrow" instead of doing
     anything further. This is the genuine hard ceiling: independent of how cheap any individual
     invocation already is, sustained or distributed abuse still cannot run away, because the
     application logic itself refuses past the daily cap.
  - Response text (English, matching the rest of the UI) is written to read like a short technical case
    study aimed at whoever is evaluating this as a portfolio piece, not a bare "hello": explains what's
    actually happening (real Lambda call, rate-limited, deliberately a placeholder for the cost-sensitive
    real feature) and includes a live server timestamp specifically so it's visibly *not* a string baked
    into the frontend bundle.
- **Dead Letter Queue (SQS), added 2026-09-22** — every Lambda in this architecture (the real Company
  Research Agent and the demo Lambdas alike) gets an SQS queue configured as its DLQ. Chosen deliberately
  over inventing an artificial SQS demo: this is a genuine, standard reliability pattern (a failed
  invocation — Anthropic API error, timeout, a bug — lands in the queue instead of being silently lost,
  inspectable/redrivable later) that serves the app's actual correctness needs, not just "cover another
  service." Cost is effectively zero (SQS free tier covers 1M requests/month; messages only accumulate on
  genuine failures, which should be rare). A second, more purely demonstrative option was considered —
  routing the EventBridge→Lambda heartbeat trigger through an SQS queue instead of invoking directly, to
  show the producer/consumer decoupling pattern live — noted as a legitimate but lower-priority addition,
  not adopted by default.
- **EventBridge rule** (`rate(1 day)`) triggers a Lambda that overwrites the DynamoDB heartbeat item.
  **A second API Gateway route + Lambda reads it back**, called by the frontend on page load, and
  displayed in the UI (near the architecture explanation on the landing page) — e.g. "Last automated
  check-in: [timestamp] — Hello from EventBridge, this is where daily data collection will run in the
  future." Unlike the real Scheduler (Section 1, deferred), this costs nothing meaningful (EventBridge
  rule invocations and Lambda's own free tier both comfortably cover a once-daily trigger) and does not
  reintroduce the cost/scope concern that caused the real automatic research run to be deferred — it
  demonstrates the wiring pattern without doing any real (paid) work.

## 8. CI/CD and deployment strategy

- **Trigger: Git tags matching `v*`** (e.g. `v1.0.0`), not every push to `main`. Chosen deliberately over
  continuous-deployment-on-every-push: gives an explicit, bookkept release boundary and a natural
  version history — judged a better fit for a portfolio/showcase project than the simpler "every commit
  is live" default, at the cost of one extra manual step (creating the tag) per release.
- **Build/publish: GitHub Actions, authenticated via OIDC federation to an IAM role** — not a static AWS
  access key. This was pulled forward from the originally-planned CI/CD topic because it was needed
  immediately: building the Docker image needs real disk space (GitHub-hosted runners have plenty; local
  AWS CLI access wasn't available without either static keys or IAM Identity Center, both deliberately
  avoided per Section 3). On tag push: checkout → build the Docker image → authenticate to ECR via the
  OIDC-federated role → push.
- **Deploy: a new ECS Service deployment**, triggered explicitly (e.g. a workflow step calling
  `aws ecs update-service --force-new-deployment`, or updating the Task Definition to reference the new
  image tag) — unlike App Runner, ECS has no built-in "watch this ECR repo and auto-redeploy" toggle, so
  this step needs to be an explicit part of the GitHub Actions workflow rather than implicit.
- **Deployment strategy actually in use: Rolling**, ECS's default `ECS` deployment controller — replaces
  tasks gradually, requires no extra configuration. Blue/Green and Canary are understood conceptually
  (exam-relevant, and unlike App Runner, ECS *can* do both via the `CODE_DEPLOY` deployment controller/
  AWS CodeDeploy) but not implemented — judged out of scope for a single-task, single-user service.

## 9. Deliberately excluded / only temporary

- **WAF**: not run continuously — CloudFront/Route 53 already include Shield Standard for free; WAF's
  Web ACL cost (~5–8 $/month minimum) would compete with RDS/App Runner for the budget. May be switched
  on briefly to learn, then removed.
- **GuardDuty / AWS Config**: same reasoning — genuine ongoing cost, not compatible with continuous
  operation under the budget cap. Learn in a bounded session, don't leave running.
- **RDS Multi-AZ**: not needed for a single-user app; would double RDS cost.

## Decision log (chronological, session dates)

- 2026-09-11/12: Account created on Free Plan. IAM Identity Center rejected after discovering it forces
  Paid Plan + forfeits signup credit; built the plain-IAM-user + assumable-Admin-role substitute instead,
  including the MFA-freshness condition (added after the user pushed back on "isn't `AdministratorAccess`
  basically Root?" — a materially correct objection that led to the two-permission-set-equivalent split
  and the MFA-age hardening).
- 2026-09-13: AWS Budgets + automatic Budget Action added — pulled forward ahead of any billable resource
  existing, specifically before the VPC/RDS work, so the safety net predates the first real cost.
- 2026-09 (side quest): EC2 + Launch Template + Golden AMI + Target Group + ALB + Auto Scaling Group built
  and fully torn down as a bounded learning exercise (not part of this architecture) — triggered by the
  question "how does every instance get configured identically when scaling," resolved conceptually via
  User Data → Golden AMI/EC2 Image Builder → containers, tying the last one back to why App Runner exists.
- 2026-09-19: Region decided (`eu-central-1`). `value-screener-vpc` and both security groups built. RDS
  instance created. VPC-Connector-egress-forces-all-traffic-through-VPC issue discovered while explaining
  what the connector is, flagged as needing resolution before building App Runner rather than being
  glossed over.
- 2026-09-20: Egress question resolved by reading the actual backend code (no direct Anthropic/third-party
  calls in `backend/`) rather than trusting the older design doc — conclusion: VPC endpoints suffice, no
  NAT Gateway. Container-image-via-ECR path chosen over App Runner's native Java build due to Java-21
  version-support uncertainty. `Dockerfile` added. Local-machine AWS CLI credential gap discovered while
  planning the ECR push (no access keys, no Identity Center → no way to authenticate a local `docker
  push`) — resolved by pulling the GitHub-OIDC CI/CD setup forward from its originally later slot, rather
  than accepting a one-off static key or fighting CloudShell's 1 GB storage limit. Deploy trigger set to
  Git tags (`v*`) rather than push-to-main, deliberately for a bookkept release history over the simpler
  continuous-deploy default.
- 2026-09-22: While trying to create the App Runner service itself, discovered App Runner closed to new
  customers as of 2026-04-30 — not a Free Plan restriction, a hard blocker for this account regardless of
  plan. Re-audited the full `docs/` tree against this deployment plan at the user's request rather than
  patching around the App Runner loss in isolation; found the 2026-08-10 Scheduler-deferral decision in
  the screening-cost-redesign spec, which this deployment plan had been implicitly ignoring (had been
  planning Topic 5 as "Lambda + automatic EventBridge trigger," which is scope the app's own design
  explicitly deferred five weeks before this AWS work started). Evaluated App Runner's replacement path:
  rejected ECS Express Mode (forces its own ALB, not worth it for one service; building classically has
  more learning value anyway) and seriously compared classic ECS/Fargate against plain EC2 (EC2 is
  cheaper, ~6 vs ~10–11 $/month, and reuses the side quest's hands-on knowledge, but ECS introduces more
  new exam-relevant material) — chose ECS/Fargate now for the learning-breadth reason specifically,
  with an explicit, recorded intent to reconsider EC2 once real Paid-Plan cost pressure arrives, judged a
  low-risk deferred swap since VPC/RDS/ECR/CI are all compute-layer-agnostic. Replaced the planned ALB
  ingress with API Gateway (HTTP API) + VPC Link for the same reason (ALB's fixed cost vs. API Gateway's
  near-zero cost at this traffic volume) — this also avoids needing a new public subnet, since the VPC
  Link places ENIs in the existing private subnets exactly like the App Runner connector would have.

---

## Deutsche Zusammenfassung

Diese Spec dokumentiert das AWS-Deployment (Phase 4) von value-screener, aufgebaut als begleitete,
schrittweise Lern-Session (Vorbereitung auf die AWS-Developer-Zertifizierung), mit den drei Leitplanken
Sicherheit zuerst, ~20 €/Monat Kostendeckel, möglichst breite AWS-Funktionsabdeckung.

**Account/Identität**: Free-Plan-Account, bewusst **ohne** IAM Identity Center (würde sofort auf
Pay-as-you-go zwingen und das Startguthaben verfallen lassen), stattdessen ein normaler IAM-User
(`PowerUserAccess`, kein Access Key) plus eine per `AssumeRole` erreichbare `Admin`-Rolle mit
Frisch-MFA-Bedingung (unter 20 Minuten). Budget mit automatischer Deny-Policy-Notbremse bei 100 %.

**Netzwerk**: eigene VPC (`10.0.0.0/16`, `eu-central-1`), zwei private Subnetze über zwei AZs, **kein**
Internet/NAT Gateway — RDS ist damit strukturell unerreichbar aus dem Internet, nicht nur per
Konfigurationsschalter.

**Datenbank**: RDS PostgreSQL, `db.t4g.micro`, Single-AZ, nicht öffentlich, Zugangsdaten von RDS selbst in
Secrets Manager verwaltet.

**Backend-Compute**: **ECS Fargate** (klassisch selbst gebaut, nicht Express Mode), nicht App Runner — App
Runner nimmt seit 30.04.2026 keine Neukunden mehr an, unabhängig vom Kontotyp. Express Mode verworfen
(erzwingt einen eigenen ALB, für einen einzelnen Service nicht lohnend; klassisch gebaut lernt man mehr).
Reines EC2 wäre günstiger (~6 statt ~10–11 $/Monat) und wurde ernsthaft erwogen — bewusst zugunsten des
höheren Lernwerts von ECS/Fargate vertagt, mit der festgehaltenen Absicht, bei echtem Kostendruck (Ende
des Free-Plan-Fensters) auf EC2 zu wechseln; VPC/RDS/ECR/CI-Pipeline sind davon unberührt.
Öffentlicher Zugang über **API Gateway (HTTP API) + VPC Link** statt Load Balancer — deutlich günstiger
bei geringem Traffic, braucht zudem kein neues öffentliches Subnetz. Container-Image aus ECR, per
`Dockerfile`. Dieselbe VPC-Egress-Problematik wie bei App Runner gilt auch für ECS-Tasks — nach
Code-Prüfung (kein direkter Anthropic-Aufruf im Backend) reichen VPC-Endpoints, kein NAT Gateway nötig.

**Wichtige Korrektur am ursprünglichen Plan (2026-09-22):** Der in der Screening-Cost-Redesign-Spec bereits
am 2026-08-10 getroffene Beschluss, den automatischen täglichen Scheduler auf später zu verschieben, war
in dieser Deployment-Planung bisher nicht berücksichtigt — Thema 5 (Lambda) braucht **keinen**
EventBridge-Scheduler für v1, nur den Company-Research-Agent mit manuellem Trigger.

**CI/CD**: GitHub Actions mit OIDC-Föderation (kein Access Key), ausgelöst durch Git-Tags (`v*`) statt bei
jedem Push — bewusste Release-Disziplin statt "jeder Commit ist live". Anders als App Runner deployt ECS
nicht automatisch bei neuem ECR-Image — der Workflow muss das Service-Update explizit auslösen. Rolling
Deployment als Standard; Blue/Green und Canary (bei ECS über CodeDeploy technisch möglich, anders als bei
App Runner) nur konzeptionell behandelt, nicht gebaut.

**Bewusst nur befristet**: WAF, GuardDuty, AWS Config — echte laufende Kosten, nicht mit dem Budget
vereinbar im Dauerbetrieb.
