# Handoff: part 3 of the serverless rebuild, Terraform data + app (2026-11-21)

For a new Claude session. This file is the entry point; it replaces the 2026-11-18 handoff.

Suggested opening: *"Projekt value-screener. Lies zuerst
docs/superpowers/plans/2026-11-21-terraform-app-handoff.md komplett. Wir starten Teil 3 (Terraform data und app)."*

## 1. Read first (in this order)

1. This file.
2. `PROJECT-STATUS.md`: sections "Serverless rewrite part 2 done", "Backlog for later plans", "Serverless rewrite
   part 1 done" (handler, SSM names, alias, CloudFront header requirements).
3. Design `docs/superpowers/specs/2026-11-14-serverless-terraform-design.md`, sections 6–12 (section 10 has the
   part 2 decisions).
4. `infra/foundation/*.tf` (what exists; outputs are the interface for part 3).

## 2. Where we stand

- Part 2 is applied in AWS (see status). Stacks: `infra/bootstrap` (local state), `infra/foundation` (S3 backend,
  key `foundation/terraform.tfstate`). Tests: `terraform test` with `mock_provider` per stack.
- `foundation` outputs: `artifact_bucket_name`, `github_deploy_role_arn`, `backend_role_name`, `backend_role_arn`.
- `.github/workflows/build-and-push-backend.yml` (old ECR/ECS deploy) still exists; replaced in part 3. Do not push
  a version tag before the new workflow exists (the deploy role trusts `refs/tags/v*`).

## 3. Scope of part 3 (design section 12)

1. **First task: session hardening** (backlog list in `PROJECT-STATUS.md`), before anything goes live.
2. `infra/data`: DynamoDB table(s), PITR, deletion protection, `prevent_destroy`; SSM parameters with placeholder
   and `ignore_changes = [value]` (user enters real values in the console).
3. `infra/app`: both Lambdas + alias `live` + log groups (14 days), HTTP API with throttling, frontend bucket,
   CloudFront (origin-verify header, `CloudFront-Viewer-Address`), permissions attached to the existing
   `value-screener-backend` and `value-screener-github-deploy` roles. **No reserved concurrency** (quota 10,
   user decision). The plan decides how `app` reads `data`/`foundation` (remote state or data sources).
4. First manual deploy, secrets in the console, live smoke test as USER and ADMIN; then the CI deploy workflow.

Part 3 needs its own short plan first (writing-plans skill).

## 4. Working mode (binding)

- **Teaching session, one step at a time:** Claude writes each Terraform file test-first and explains it in simple
  German; for user actions Claude gives **exactly one step per message** and waits.
- **Two environments:** Claude works in the sandbox (this WSL: Terraform only, no AWS CLI, no credentials) and may
  run `terraform init -backend=false`, `fmt`, `validate`, `test`. The user runs AWS CLI, `init` with backend,
  `plan`, `apply` in the ops environment and sends output (e-mail replaced by `xxx`).
- **Sign-in in the ops environment:** `aws login --profile vs-login`, then within 20 minutes
  `eval "$(aws configure export-credentials --profile vs-admin --format env)"` (MFA code); credentials last about
  1 h. Afterwards `unset AWS_ACCESS_KEY_ID AWS_SECRET_ACCESS_KEY AWS_SESSION_TOKEN AWS_CREDENTIAL_EXPIRATION`,
  `aws logout --profile vs-login`, `rm -rf ~/.aws/cli/cache`.
- Per stack in the ops environment: `backend.hcl` (gitignored, `bucket = "value-screener-tfstate-<account>"`),
  `terraform.tfvars` (gitignored) where needed.
- **The user does all git operations.** Claude gives the commands (ending with the `Co-Authored-By` line).
- **Do not install tools.** Never create, change or delete AWS resources without the user's explicit go.
- Secrets never on the command line, in the repo or (if avoidable) in state.
- Keep changes small and on topic; later things go into the plan or the status backlog.
- Answers in German; new project docs: English body + German summary.

## Deutsche Kurzfassung

Einstieg für Teil 3 (Terraform `data` und `app`). Teil 2 steht in AWS: State-Bucket, Artefakt-Bucket, GitHub-Zugang
mit Deploy-Rolle, leere Backend-Rolle, Budget mit Sperre. Erster Task von Teil 3 ist die Session-Härtung, dann
`data`, `app`, erster Deploy und der CI-Workflow. Keine festen Lambda-Obergrenzen (Quote 10). Arbeitsweise: Claude
schreibt testgetrieben in der Sandbox, du führst in der Ops-Umgebung aus, immer ein Schritt nach dem anderen.
