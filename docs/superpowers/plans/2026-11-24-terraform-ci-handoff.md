# Handoff: part 3, Tasks 8–9 (CI deploy workflow, docs) (2026-11-24)

For a new Claude session. This file is the entry point; it replaces the 2026-11-21 handoff.

Suggested opening: *"Projekt value-screener. Lies zuerst
docs/superpowers/plans/2026-11-24-terraform-ci-handoff.md komplett. Wir machen Task 8 (CI-Deploy-Workflow)."*

## 1. Read first (in this order)

1. This file.
2. Plan `docs/superpowers/plans/2026-11-22-terraform-data-app.md`: decisions 4, 8, 9; Task 8 and 9; the
   "Result" notes after each task (they record every deviation from the plan).
3. `PROJECT-STATUS.md`, section "Serverless rewrite part 3: app live".
4. `infra/app/*.tf` (what exists) and `.github/workflows/` (`ci.yml` stays, `build-and-push-backend.yml` is replaced).

## 2. Where we stand

- **Live** at `https://<cloudfront_domain>` (output of `infra/app`), tested as USER and ADMIN on 2026-11-24.
- Stacks applied: `bootstrap`, `foundation`, `data` (table `value-screener`, SSM user secrets with write-only
  placeholder), `app` (both Lambdas with alias `live`, HTTP API payload **1.0**, rate 5 / burst 10, frontend bucket,
  CloudFront with origin secret and security headers).
- Deployed versions: backend **2** (payload 1.0 fix), deep-analysis **1**, frontend from commit `1f5ce7a`.
- Branch `feature/terraform-data-app` (PR to `main`). **Do not push a `v*` tag** until the new workflow exists: the old
  `build-and-push-backend.yml` still runs on `v*` tags (ECR/ECS, would fail).

## 3. Task 8 (from the plan, plus what the manual deploys taught us)

- `infra/app/deploy_role.tf`: inline policy on `value-screener-github-deploy` (decision 8), test first.
- `.github/workflows/deploy.yml` on `push: tags: ['v*']`: tests (backend, frontend, deep-analysis) → build zips →
  `aws s3 cp` to `backend/backend.zip` and `deep-analysis/deep-analysis.zip` → per function
  `update-function-code` → `aws lambda wait function-updated` → `publish-version` →
  `aws lambda wait published-version-active` (SnapStart takes minutes) → `update-alias live` → frontend
  `npm ci && npm run build` → `s3 sync dist/ --delete` → `create-invalidation --paths '/*'`.
  Every AWS CLI call needs `--region eu-central-1` (or `AWS_REGION` in the job env).
- Role ARN, bucket names, distribution id as GitHub repository **variables** (not secrets, not in the file).
- Delete `build-and-push-backend.yml`.
- First tag `v3.0.0`; afterwards `terraform plan` in `app` must show `No changes` (alias and code are ignored).

## 3a. After Task 8: research sync live (Task 10, user request 2026-11-24)

The user researched data locally and wants it in the live system. The mechanism exists (`ResearchSyncController`,
`scripts/push-research.sh`, README "Recherchedaten ins Live-System übertragen"); in AWS it is off. Order decided:
Task 8 first, so the sync deploys (and a possible frontend change) go out with one tag instead of manual deploys.
The user may swap the order if the data is urgent and the snapshot count is small.

1. Clarify first: how many snapshots exist locally? Backlog item "lighter list endpoint for the research overview"
   (the overview reads all snapshots; about 128 RCU at 100 snapshots vs. 20 RCU) is needed before the sync at larger
   counts and changes backend + frontend.
2. `infra/data`: SSM parameter `/value-screener/app.research-sync.token-hash` (write-only placeholder like the user
   secrets); the user generates the token with `MachineTokenGenerator` and enters only the hash in the console.
3. `infra/app`: Lambda env `APP_RESEARCH_SYNC_ENABLED=true` (the role already reads `/value-screener/*`).
4. Deploy a new backend version (SnapStart reads SSM at snapshot time; Terraform ignores the alias).
5. The user creates `~/.config/value-screener/live.env` (`LIVE_BACKEND_URL=https://<cloudfront_domain>`, tokens;
   never in chat or on the command line) and runs `scripts/push-research.sh <ISIN>` on the host, never in the
   sandbox. Rate limit 5/s is enough for one ISIN per call.
6. Check: overview shows the groups with snapshots; `terraform plan` in `app` shows only the intended change.

Short plan first (writing-plans), then test-first as usual.

## 4. Working mode (binding, unchanged)

- Teaching session in simple German, **exactly one user step per message**, then wait.
- Sandbox (this WSL): Terraform `init -backend=false`/`fmt`/`validate`/`test`, Maven, npm. No AWS CLI, no credentials.
  Claude writes **only inside the project directory**; `~/dev/transfer` only on the user's explicit request.
- Ops environment `aws-ops`: AWS CLI, `terraform init/plan/apply`. Sign-in: `unset AWS_*` + `aws login --profile
  vs-login`, then `eval "$(aws configure export-credentials --profile vs-admin --format env)"` (MFA, ~1 h).
- Artifacts reach aws-ops only via the transfer folder (`~/dev/transfer` = `/home/ops/transfer`, read-only there),
  compared with `sha256sum` on both sides; then `aws s3 cp` to the artifact bucket.
- The user does all git operations; Claude gives the commands (ending with the `Co-Authored-By` line).
- No AWS change without the user's explicit go. Secrets never on the command line, in the repo or in state (exception:
  origin secret, design 7.3). The user masks account id and names in pasted output.

## Deutsche Kurzfassung

Die App läuft live über CloudFront und ist als USER und ADMIN getestet. Offen sind Task 8, der GitHub-Workflow,
der bei einem Versions-Tag testet, baut und deployt, genau die Schritte, die wir von Hand gemacht haben. Danach
Task 10, der Research-Sync: lokal recherchierte Daten ins Live-System bringen (Mechanismus existiert, in AWS noch
aus; zuerst klären, ob die schlankere Research-Liste nötig ist). Zum Schluss Task 9, die Doku. Bis der neue Workflow steht, keinen `v…`-Tag pushen. Arbeitsweise unverändert: ein Schritt pro Nachricht,
Claude nur im Projektordner, Dateien nach aws-ops nur über den Transferordner.
