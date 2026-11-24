# Research Sync Live (Part 3, Task 10) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans (teaching session, inline, no
> subagents). Claude writes test-first in the sandbox; the user does every git, AWS and console step, **exactly one
> per message**. No AWS change without the user's explicit go. Steps use checkbox (`- [ ]`) syntax.

**Goal:** locally researched snapshots reach the live app with `scripts/push-research.sh <ISIN>`, one ISIN per call.

**Architecture:** the mechanism exists (`ResearchSyncController` on `POST /api/research/sync/snapshots`, machine
token filter, `push-research.sh`, README "Recherchedaten ins Live-System übertragen"). In AWS it is off. This plan
adds the token hash as an SSM parameter in `data` (write-only placeholder like the user secrets), switches the
endpoint on with a Lambda env var in `app`, and ships a new backend version through the CI workflow (SnapStart reads
SSM at snapshot time; Terraform ignores the alias). No Java or frontend change.

**Tech Stack:** Terraform (`infra/data`, `infra/app`, mock-provider tests), GitHub Actions `deploy.yml` (Task 8).

**Spec:** handoff `docs/superpowers/plans/2026-11-24-terraform-ci-handoff.md` section 3a; parent plan
`docs/superpowers/plans/2026-11-22-terraform-data-app.md` (decisions 3 and 9); design
`docs/superpowers/specs/2026-11-14-serverless-terraform-design.md` (7.4: an SSM change needs a new deploy).

## Global Constraints

- Same as the parent plan: Terraform `>= 1.15`, `aws ~> 6.23`, `eu-central-1`, sandbox checks before each commit:
  `terraform fmt -check -recursive infra`, then `validate` and `test` in the stack.
- Secrets never in chat, on the command line, in the repo or in state. The sync token stays on the user's machine
  (`~/.config/value-screener/live.env`, `chmod 600`); only its SHA-256 hash goes to SSM, entered in the console.
- `app.research-ingest.enabled` stays `false` in AWS (`IngestDisabledInAwsCheck`); only the sync is switched on.
- `push-research.sh` runs on the host, never in the sandbox.

## Decision taken in this plan (please confirm)

**Lighter list endpoint later, not now.** Today fewer than 10 snapshots exist (user, 2026-11-24); one overview load
reads all of them (~13 RCU at 10, table has 20 RCU), which is fine. Growth to ~100 (about 128 RCU per load) would
throttle the overview. So: a separate Task 11 "lighter list for the research overview" (backend + frontend, own
plan) **before the live table holds about 30 snapshots**. Cost if wrong: overview loads get throttled (slow or
error), no data loss.

## Review Focus

1. Sync switched on while SSM still holds the placeholder `CHANGE_ME` → the backend refuses to start (hash must be
   64 hex chars). Expected: live stays on the old version (alias moves only after a version is active). Prevented by
   order: hash in the console **before** the `app` apply and the tag.
2. A snapshot whose group exists only locally → `422` from live. Expected: the script shows the status, nothing is
   written; create the group live first.
3. Wrong or missing token → `401`/`403`, nothing written; the script never prints the token.
4. Sync token equal to the local ingest token → the script refuses (existing check).
5. `terraform plan` in `app` after the deploy shows only the env change before the apply and `No changes` after.

---

### Task 10.1: SSM parameter for the sync token hash (`infra/data`)

**Files:** Modify `infra/data/ssm.tf` (add the name to `local.user_supplied_parameters`),
`infra/data/tests/ssm.tftest.hcl`.

- [ ] Test first: in `one_extra_user_gives_admin_hash_and_both_user_parameters` the expected set gains
  `/value-screener/app.research-sync.token-hash`; `no_extra_user_gives_only_the_admin_hash` expects
  `["/value-screener/app.admin.password-hash", "/value-screener/app.research-sync.token-hash"]` (rename the run to
  `no_extra_user_gives_admin_and_sync_hash`). Run `terraform test` → FAIL.
- [ ] Add the name; same `SecureString`, `value_wo = "CHANGE_ME"`, `value_wo_version = 1`. Sandbox checks → PASS.
- [ ] User (one step each): commit + push → aws-ops `plan` in `infra/data` (expected `1 to add`) → go → `apply`.
- [ ] User generates the sync token with `MachineTokenGenerator` **on the host** (README "Token erzeugen"), enters
  only the `sha256` value in the console (Parameter Store → `/value-screener/app.research-sync.token-hash` → edit),
  keeps the token for `live.env`. Nothing pasted into chat.

### Task 10.2: Switch the endpoint on (`infra/app`) and deploy

**Files:** Modify `infra/app/backend.tf` (env), `infra/app/tests/backend.tftest.hcl`, `README.md` (step 2 of
"Recherchedaten ins Live-System übertragen": in AWS the hash is the SSM parameter above and the switch is the
Terraform env var).

- [ ] Test first: the env assertion expects four variables, adding `APP_RESEARCH_SYNC_ENABLED = "true"`; update its
  error message ("Exactly these four ..."). Run `terraform test` → FAIL.
- [ ] Add the env var with a comment (needs the hash in SSM, else the backend does not start). Sandbox checks → PASS.
- [ ] User (one step each): commit + push → aws-ops `plan` in `infra/app` (expected `1 to change`, only the
  backend env) → go → `apply` → tag `v3.1.0` → workflow green → `terraform plan` in `app` = `No changes`.

### Task 10.3: First live sync

- [ ] User creates `~/.config/value-screener/live.env` on the host as in the README
  (`LIVE_BACKEND_URL=https://<cloudfront_domain>`, both tokens), `chmod 600`.
- [ ] User starts the local backend, runs `scripts/push-research.sh <ISIN>` for one ISIN → two lines `HTTP 200`.
- [ ] Check live: the research overview shows the snapshot in its group; then the remaining ISINs one call each.
- [ ] Result note into the parent plan (Task 8 and Task 10), backlog entry Task 11 in `PROJECT-STATUS.md`.

**Result (2026-11-24):** all three parts done and live; details in the parent plan, "Result Task 10".

## Deutsche Kurzfassung

Der Sync-Mechanismus existiert schon, in AWS ist er nur aus. Wir legen in `data` einen SSM-Parameter für den Hash
des Sync-Tokens an (Platzhalter, du trägst den echten Hash in der Konsole ein), schalten in `app` den Endpunkt per
Umgebungsvariable ein und deployen mit Tag `v3.1.0`. Danach überträgst du von deinem Rechner aus pro Aufruf eine
ISIN. Reihenfolge ist wichtig: erst der Hash in SSM, dann das Einschalten, sonst startet das Backend nicht (live
bleibt dann auf der alten Version). Die schlankere Research-Liste brauchen wir bei unter 10 Snapshots noch nicht;
sie kommt als eigener Task 11, bevor es live etwa 30 Snapshots werden.
