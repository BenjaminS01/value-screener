# Terraform Foundations (Part 2) Implementation Plan

> **Execution:** teaching session, inline (superpowers:executing-plans), no subagents. Claude writes each file and
> explains every block in German; **the user runs every `terraform`, `aws` and git command**. No AWS change without
> the user's explicit go per apply. Steps use checkbox (`- [ ]`) syntax.

**Goal:** an S3 state bucket (`infra/bootstrap`) and the account foundation (`infra/foundation`): budget brake,
GitHub OIDC deploy role, backend Lambda role shell, artifact bucket.

**Architecture:** two Terraform stacks with separate state. `bootstrap` uses local state and is applied once;
`foundation` uses the S3 backend in the bootstrap bucket with native locking. Values that should not be in the
public repo (account-specific bucket name, alert e-mail) live in gitignored `backend.hcl` / `terraform.tfvars`
files with committed `*.example` copies.

**Tech Stack:** Terraform ≥ 1.15, AWS provider ≥ 6.23 (`~> 6.23`), AWS CLI ≥ 2.32, region `eu-central-1`.

**Spec:** `docs/superpowers/specs/2026-11-14-serverless-terraform-design.md` (sections 10, 11, 12) and the handoff
`docs/superpowers/plans/2026-11-18-terraform-foundations-handoff.md`.

## Global Constraints

- `required_version = ">= 1.15"`; `aws` provider `version = "~> 6.23"`; region `eu-central-1`.
- Provider `default_tags { tags = { project = "value-screener" } }` in every stack.
- Credentials only via `AWS_PROFILE=vs-admin` (environment), never a `profile` or keys in `.tf` files.
- `.gitignore`: `*.tfstate*`, `.terraform/`, `*.tfvars` (but not `*.tfvars.example`), `backend.hcl`. Commit
  `.terraform.lock.hcl`.
- Budget: monthly, `5` USD; e-mail alerts at `1` USD actual and `3` USD forecast; automatic deny action at `5` USD
  actual. The deny policy is never attached to the `Admin` role.
- Buckets: versioning on, SSE-S3, all four public-access blocks on, bucket policy denies non-TLS access.

## Decisions taken in this plan (please confirm)

1. **Foundation ↔ app connection for the budget action:** `foundation` creates the backend Lambda role
   `value-screener-backend` (trust: `lambda.amazonaws.com`, no permissions yet). Part 3 (`app`) attaches its
   permission policies to that role by name and uses it for the Lambda. So the budget action can name both roles
   at creation time, and `app` never changes a `foundation` resource. Same pattern for the deploy role: `foundation`
   gives it only artifact-bucket rights; `app` attaches the Lambda/frontend/CloudFront rights in part 3.
2. **Extended deny policy** (`value-screener-budget-deny`, managed policy in `foundation`): the old list from
   `docs/aws/budget-deny-policy.json` plus `lambda:InvokeFunction`, `lambda:UpdateFunctionCode`,
   `lambda:PublishVersion`, `lambda:UpdateAlias`, `dynamodb:CreateTable`, `dynamodb:UpdateTable`,
   `cloudfront:CreateInvalidation`, `s3:PutObject`. The JSON file in `docs/aws/` is deleted (Terraform is the source).
3. **Budget action execution role** `value-screener-budget-action`: trust `budgets.amazonaws.com` (condition
   `aws:SourceAccount` = own account), may only `iam:AttachRolePolicy` / `iam:DetachRolePolicy` on the two role ARNs
   with condition `iam:PolicyARN` = the deny policy ARN.
4. **Deploy role trust:** OIDC provider `https://token.actions.githubusercontent.com`, audience `sts.amazonaws.com`;
   `sub` `StringLike` `repo:<github_repository>:ref:refs/tags/v*` (version tags only, no branches, no PRs).
   `github_repository` is a variable, default `BenjaminS01/value-screener` (user verifies).
5. **Bucket names:** `value-screener-tfstate-<account_id>` and `value-screener-artifacts-<account_id>` (account id
   from `aws_caller_identity`, so it is not written in the repo). Artifact bucket: noncurrent versions expire after
   30 days. State bucket: `prevent_destroy`.
6. Bootstrap state stays local (`infra/bootstrap/terraform.tfstate`, gitignored); the user keeps a copy outside the
   repo. Losing it is harmless (the bucket stays, `prevent_destroy`; re-import possible).

## Review Focus

- A forked repo or a PR workflow tries to assume the deploy role → denied (trust only matches `refs/tags/v*` of the
  one repository). Check: trust policy shown in `terraform plan` of Task 5.
- The budget action could attach any policy, or attach to `Admin` → execution role is limited to the deny policy ARN
  and the two role ARNs. Check: plan of Task 6 and IAM policy simulator.
- Personal data in the public repo (e-mail, account id) → only in gitignored files. Check: `git status` before each
  commit shows no `*.tfvars`, `backend.hcl`, `*.tfstate*`.
- `terraform destroy` or a rename on the state bucket → blocked by `prevent_destroy`. Check: plan never shows
  `destroy` for `aws_s3_bucket.state`.
- Credentials expire during an apply → a stale lock in the S3 bucket (`.tflock` object). Expected handling:
  log in again, `terraform force-unlock <id>` only after checking nobody else runs.

---

### Task 1: Branch, tools, `.gitignore`

**Files:** Modify: `.gitignore`

- [ ] User: `git switch -c feature/terraform-foundations`
- [ ] User sends output of `aws --version` and `terraform version`. Expected: aws-cli ≥ 2.32, Terraform ≥ 1.15.
- [ ] Claude adds the Terraform entries from Global Constraints to `.gitignore` (with `!*.tfvars.example`).
- [ ] User commits (`chore: ignore terraform state and local config`).

### Task 2: Sign-in and console checks

**Files:** none in the repo (`~/.aws/config` on the user's machine, written by the user).

- [ ] Claude gives the `~/.aws/config` snippet: `[profile vs-login]` (region `eu-central-1`, used by `aws login`) and
  `[profile vs-admin]` with `role_arn = arn:aws:iam::<account>:role/Admin`, `source_profile = vs-login`,
  `mfa_serial = <MFA device ARN>`, `region = eu-central-1`.
- [ ] User: `aws login --profile vs-login`, then `aws sts get-caller-identity --profile vs-admin`.
  Expected: ARN `...:assumed-role/Admin/...`. Note whether an MFA code was asked (fresh-MFA test from the design).
- [ ] User checks in the console: Service Quotas → Lambda → "Concurrent executions" (value noted; if 10, the user
  decides: request increase or drop reserved concurrency in part 3). IAM → Identity providers: no
  `token.actions.githubusercontent.com`. Billing → Budgets: none. IAM → Roles: no old deploy role. Found leftovers are
  deleted by hand.
- [ ] Result (quota value, MFA behaviour) goes into `PROJECT-STATUS.md` in Task 7.

### Task 3: `infra/bootstrap` (state bucket)

**Files:** Create: `infra/bootstrap/versions.tf` (terraform + provider blocks), `infra/bootstrap/main.tf`
(`aws_caller_identity`, `aws_s3_bucket.state` with `prevent_destroy`, versioning, SSE `AES256`, public access block,
TLS-only bucket policy), `infra/bootstrap/outputs.tf` (`state_bucket_name`).

- [ ] Claude writes and explains the files.
- [ ] User: `cd infra/bootstrap && terraform init && terraform fmt -check && terraform validate`. Expected: no output
  from fmt, "Success! The configuration is valid."
- [ ] User: `terraform plan`. Expected: `5 to add, 0 to change, 0 to destroy` (bucket, versioning, encryption,
  public access block, policy). Review together.
- [ ] User gives the go and runs `terraform apply`. Then
  `aws s3api get-bucket-versioning --bucket <name> --profile vs-admin` → `"Status": "Enabled"`.
- [ ] User copies `terraform.tfstate` outside the repo; commits `infra/bootstrap/*.tf` and `.terraform.lock.hcl`.

### Task 4: `infra/foundation` skeleton + artifact bucket

**Files:** Create: `infra/foundation/versions.tf` (with `backend "s3" { key = "foundation/terraform.tfstate",
region = "eu-central-1", encrypt = true, use_lockfile = true }`, bucket from `backend.hcl`),
`infra/foundation/backend.hcl.example`, `infra/foundation/variables.tf` (`alert_email`, `github_repository`),
`infra/foundation/terraform.tfvars.example`, `infra/foundation/artifacts.tf` (bucket like bootstrap without
`prevent_destroy`, plus lifecycle rule noncurrent 30 days), `infra/foundation/outputs.tf` (`artifact_bucket_name`).

- [ ] Claude writes and explains; user creates `backend.hcl` and `terraform.tfvars` from the examples.
- [ ] User: `terraform init -backend-config=backend.hcl`, `fmt -check`, `validate`, `plan`. Expected: 6 to add.
- [ ] Go + `terraform apply`. Check: `aws s3 ls s3://<state bucket>/foundation/` shows `terraform.tfstate`.
- [ ] Commit `*.tf`, `*.example`, lock file.

### Task 5: GitHub OIDC, deploy role, backend role shell

**Files:** Create: `infra/foundation/github_oidc.tf` (`aws_iam_openid_connect_provider.github`,
`aws_iam_role.github_deploy` = `value-screener-github-deploy`, inline policy: `s3:PutObject`, `s3:GetObject` on
`<artifact bucket>/*`, `s3:ListBucket` on the bucket), `infra/foundation/backend_role.tf`
(`aws_iam_role.backend` = `value-screener-backend`, trust `lambda.amazonaws.com`, no policies). Outputs:
`github_deploy_role_arn`, `backend_role_name`, `backend_role_arn`.

- [ ] Claude writes and explains (decisions 1 and 4).
- [ ] User: `fmt -check`, `validate`, `plan` (review trust policies), go, `apply`.
- [ ] Commit.

### Task 6: Budget with deny action

**Files:** Create: `infra/foundation/budget.tf` (`aws_iam_policy.budget_deny`, `aws_iam_role.budget_action` +
inline policy, `aws_budgets_budget.monthly` = `value-screener-monthly` with two notifications,
`aws_budgets_budget_action.deny` with `approval_model = "AUTOMATIC"`, `notification_type = "ACTUAL"`,
threshold `5` `ABSOLUTE_VALUE`, `iam_action_definition` on both role names, subscriber `alert_email`).
Delete: `docs/aws/budget-deny-policy.json`.

- [ ] Claude writes and explains (decisions 2 and 3).
- [ ] User: `fmt -check`, `validate`, `plan`, go, `apply`.
- [ ] Check in the console: Budgets shows `value-screener-monthly` with 2 alerts and 1 action ("Standby");
  IAM policy simulator: role `value-screener-backend` with the deny policy → `lambda:InvokeFunction` denied.
  The e-mail subscription confirmation (if AWS sends one) is clicked by the user.
- [ ] Commit.

### Task 7: Docs and handoff for part 3

**Files:** Modify: `PROJECT-STATUS.md` (new top section with English body + German summary: what exists, quota
value, MFA result, outputs part 3 needs), design section 11 note on decision 1. Create: handoff for part 3
`docs/superpowers/plans/<date>-terraform-app-handoff.md`; update memory `project-entry-point`.

- [ ] Claude writes; user reviews; user commits and opens the PR; `aws logout`.

## Deutsche Kurzfassung

Plan für Teil 2 in sieben Schritten: Branch und `.gitignore`, Anmeldung mit `aws login` und Prüfungen in der
Konsole (Lambda-Quote, Altreste), dann `infra/bootstrap` (State-Bucket, lokaler State) und `infra/foundation` in drei
Schritten (Artefakt-Bucket, GitHub-OIDC mit Deploy-Rolle plus leere Backend-Lambda-Rolle, Budget 5 $ mit
automatischer Sperre), zuletzt Doku und Übergabe. Wichtigste Entscheidung: Die Backend-Lambda-Rolle entsteht schon in
`foundation`, damit die Budget-Sperre sie von Anfang an kennt; Teil 3 hängt nur noch Rechte an. E-Mail und
kontobezogene Namen stehen nur in ignorierten Dateien. Du führst alle Befehle aus, jedes `apply` erst nach deinem Go.
