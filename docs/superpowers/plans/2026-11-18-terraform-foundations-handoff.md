# Handoff: part 2 of the serverless rebuild, Terraform foundations (2026-11-18)

For a new Claude session. This file is the entry point; it replaces the 2026-11-14 handoff (whose section 4 is
repeated and updated below).

Suggested opening: *"Projekt value-screener, Branch `feature/terraform-foundations`. Lies zuerst
docs/superpowers/plans/2026-11-18-terraform-foundations-handoff.md komplett. Wir starten Teil 2
(Terraform-Grundlagen)."*

## 1. Read first (in this order)

1. This file.
2. `PROJECT-STATUS.md`: top sections "Small improvements", "Backlog for later plans", "Serverless rewrite part 1
   done" (Terraform values needed in part 3 are listed there).
3. Design `docs/superpowers/specs/2026-11-14-serverless-terraform-design.md`, especially sections 10
   (AWS protections), 11 (Terraform layout and operation), 12 (order of work), 13 (cost).
4. `docs/aws/budget-deny-policy.json` (old deny policy; the design extends it, see section 10 there).

## 2. Where we stand

- Part 1 (backend on DynamoDB, Lambda-ready) is done and merged (PR #5); package cleanup (PR #6) and small
  improvements (PR #7) are merged. `main` is clean; backend 537 tests, frontend 175 tests, all green.
- **AWS account is empty:** the hand-built environment (VPC, RDS, ECS, ECR, endpoints, API Gateway) was deleted.
  Everything is created new with Terraform; nothing is imported.
- No `infra/` folder exists yet; `.gitignore` has no Terraform entries yet.
- `.github/workflows/build-and-push-backend.yml` (old ECR/ECS deploy) still exists; it is replaced in part 3,
  not in part 2. Do not push a version tag.

## 3. Scope of part 2 (from design section 12)

1. **Tools (the user installs):** AWS CLI ≥ 2.32, Terraform ≥ 1.15, AWS provider ≥ 6.23 (pinned in
   `required_version` / `required_providers`). Ask the user for `aws --version` and `terraform version`.
   Version facts come from third-party sources: verify at setup.
2. **Sign-in:** `aws login --profile vs-login` (browser, MFA, temporary credentials), profile `vs-admin` assumes
   the `Admin` role (`source_profile = vs-login`, `mfa_serial`). Test whether the console MFA satisfies the
   role's fresh-MFA condition or the AssumeRole MFA prompt is needed. `aws logout` when done.
3. **Checks in the console before the first apply (user):**
   - Service Quotas → Lambda "Concurrent executions". If it is 10, reserved concurrency is impossible: request
     an increase (free) or drop reserved concurrency; **the user decides**.
   - No GitHub OIDC provider, budget or old deploy role left (anything found is deleted by hand, not imported).
4. **`infra/bootstrap/`:** state bucket (S3, versioned, SSE, public access blocked), local state, applied once.
5. **`infra/foundation/`:** budget 5 $ with e-mail alerts (1 $ actual, 3 $ forecast) and automatic deny action at
   5 $ (extended deny policy attached to the backend Lambda role and the GitHub deploy role, never to `Admin`);
   GitHub OIDC provider + deploy role; artifact bucket for Lambda zips. S3 backend with native locking
   (`use_lockfile = true`), tags `project = value-screener`, region `eu-central-1`.
6. `.gitignore`: `*.tfstate*`, `.terraform/`; commit `.terraform.lock.hcl`.

Part 2 needs its own short plan first (writing-plans skill). Note: the deny action targets the backend Lambda
role, which is created in part 3 (`app`); the plan must decide how foundation and app connect here (e.g. the
policy is created in foundation and attached in app).

## 4. Working mode (binding)

- **Teaching session:** Claude writes each Terraform file and explains every block in German before the user
  runs anything; the user runs all `terraform` and `aws` commands. **Claude never runs `terraform` or `aws`**
  with these credentials. No subagents for parts 2 and 3 unless the user asks.
- Answers in **German**, short and clear. If the user asks for "nur die Befehle", give only commands.
- **The user does all git operations.** Claude never runs state-changing git; Claude gives the commands (short
  message, ending with the `Co-Authored-By` line from the system reminder).
- **Do not install tools.** The user installs.
- **Never create, change or delete AWS resources without the user's explicit go.** Design or plan approval is
  not a go to apply.
- Tokens and passwords never on the command line, never in the repo, never in Terraform state if avoidable
  (secrets go into SSM by hand in the console, part 3). Never echo or probe secret environment variables.
- Keep changes small and on topic. "Tiny" means tiny: things for later go into the plan or the status backlog,
  not into the current change (lesson from 2026-11-18).
- Environment: WSL. Java 21 via
  `export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64; export PATH=$JAVA_HOME/bin:$PATH`; Node 20; Docker is
  rootless (`DOCKER_HOST=unix:///run/user/1001/docker.sock` for Testcontainers). The user's IDE writes CRLF;
  review with `git diff --ignore-cr-at-eol`.
- Deferred to the very end of the project (do not re-suggest earlier): token renaming and the live research
  transfer test.
- New project docs: English body + German summary.

## Deutsche Kurzfassung

Einstieg für Teil 2 (Terraform-Grundlagen) in einer neuen Session. Teil 1 und die Aufräumarbeiten sind gemergt,
das AWS-Konto ist leer, `infra/` gibt es noch nicht. Teil 2: Werkzeuge (installiert der Nutzer), `aws login` mit
MFA, Prüfung der Lambda-Quote und von Altresten in der Konsole, dann `infra/bootstrap` (State-Bucket) und
`infra/foundation` (Budget 5 $ mit automatischer Sperre, GitHub-OIDC und Deploy-Rolle, Artefakt-Bucket). Zuerst
ein kurzer Plan. Lern-Session: Claude schreibt und erklärt, der Nutzer führt alle `terraform`- und
`aws`-Befehle aus. Git nur durch den Nutzer, nichts installieren, keine AWS-Ressourcen ohne ausdrückliches Go.
