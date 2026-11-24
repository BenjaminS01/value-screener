# Handoff: part 3 done, next Task 11 (lighter research list) (2026-11-24)

For a new Claude session. This file is the entry point; it replaces `2026-11-24-terraform-ci-handoff.md`.

Suggested opening: *"Projekt value-screener. Lies zuerst
docs/superpowers/plans/2026-11-24-part3-done-handoff.md komplett. Wir machen Task 11 (schlankere Research-Liste)."*

## 1. Read first (in this order)

1. This file.
2. `PROJECT-STATUS.md`, top section "Serverless rewrite part 3 done" and "Backlog for later plans".
3. `docs/aws/aws-architecture-overview.md` (what runs where).
4. For Task 11: `backend/.../research/snapshot/CompanySnapshotController.java` (`GET /api/research/snapshots`),
   `frontend/src/api/researchApi.ts`, `frontend/src/pages/ResearchOverviewPage.tsx` and `PortfolioPage.tsx`
   (both load the full snapshot list).

## 2. Where we stand

- **Live** at `https://<cloudfront_domain>`, tested as USER and ADMIN. Deployed by tag: `v3.0.0` (first CI deploy),
  `v3.1.0` (research sync on). `terraform plan` in `app` shows `No changes`.
- **Research sync live:** the user researches locally (sandbox) and pushes reviewed snapshots with
  `scripts/push-research.sh <ISIN>` on the host. Tokens only in `~/.config/value-screener/live.env` (sync token
  for live, ingest token for the local backend); hashes in SSM and the local `.env`. Fewer than 10 snapshots live.
- **Deploy:** any code change goes live with a new `v*` tag on `main` (`.github/workflows/deploy.yml`). Config
  changes (env, SSM values) need Terraform **and** a tag (the alias is ignored by Terraform).
- The user commits small changes directly on `main` (chosen 2026-11-24); bigger ones on a branch with a PR.

## 3. Next: Task 11, lighter list for the research overview

Why: one overview load reads every snapshot in full (~13 RCU at 10 snapshots, ~128 at 100; the table has 20 RCU).
Must be live **before the live table holds about 30 snapshots**; the user expects about 100 long term.

1. Brainstorm briefly, then a short plan (writing-plans): which fields the overview and the portfolio really need
   (name, ticker, ISIN, group, maybe date), where they are stored (an index item per snapshot avoids reading full
   items), and how existing snapshots get their index item (migration or write on next sync).
2. Backend and frontend test-first; frontend contract changes, so both ship with one tag.
3. Live check: overview and portfolio still show groups and "im Portfolio".

Other backlog (not urgent): see `PROJECT-STATUS.md`, "Backlog for later plans" (cleanup items), the viewer TLS
minimum (needs an own domain) and the CloudFront flat-rate plan with WAF.

## 4. Working mode (binding, unchanged)

- Teaching session in simple German, **exactly one user step per message**, then wait.
- Sandbox (this WSL): Terraform `init -backend=false`/`fmt`/`validate`/`test`, Maven, npm. No AWS CLI, no credentials.
  Claude writes **only inside the project directory**; `~/dev/transfer` only on the user's explicit request.
- Ops environment `aws-ops` (`~/ops/value-screener`, updated with `git pull` on `main`): AWS CLI, `terraform
  init/plan/apply`. Sign-in: `unset AWS_*` + `aws login --profile vs-login`, then
  `eval "$(aws configure export-credentials --profile vs-admin --format env)"` (MFA, ~1 h).
- The user does all git operations; Claude gives the commands (ending with the `Co-Authored-By` line).
- No AWS change without the user's explicit go. Secrets never in chat, on the command line, in the repo or in state.
  Tokens are generated straight into `umask 077` files, never shown. The user masks account id and names in output.

## Deutsche Kurzfassung

Teil 3 ist fertig: Die App läuft live, Deploys laufen per Versions-Tag über GitHub, und geprüfte Research-Snapshots
überträgst du mit `scripts/push-research.sh <ISIN>`. Nächster Schritt ist Task 11: eine schlankere Liste für die
Research-Übersicht, damit sie nicht bei jedem Aufruf alle Snapshots komplett liest. Sie muss live sein, bevor dort
etwa 30 Snapshots liegen. Arbeitsweise unverändert: ein Schritt pro Nachricht, Claude nur im Projektordner, keine
AWS-Änderung ohne dein Go.
