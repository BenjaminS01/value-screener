# Handoff: Task 11 implemented, release pending (2026-11-26)

For a new Claude session. This file replaces `2026-11-24-part3-done-handoff.md` as entry point.

## Where we stand

- Task 11 (lighter research list) is implemented on branch `feature/lighter-research-list`; details in
  `PROJECT-STATUS.md` (top section) and the result notes of `2026-11-26-lighter-research-list.md`.
- Still open, in this order (user steps, one at a time):
  1. Commit and push the branch, open the PR, merge it.
  2. Tag `v3.2.0` on `main`, push the tag; the deploy workflow must run green.
  3. Live check in the browser: overview shows groups, country view, "veraltet", "im Portfolio"; portfolio opens
     a researched position. CloudWatch shows `Snapshot index backfilled for N snapshots` once. A row showing the
     ISIN as name gets re-pushed with `scripts/push-research.sh <ISIN>`.
- After the live check: mark Task 11 live in `PROJECT-STATUS.md`.

## Next (backlog, not urgent)

The deferred minors of Task 11 (see plan result notes), `PROJECT-STATUS.md` "Backlog for later plans", the viewer
TLS minimum (needs an own domain) and the CloudFront flat-rate plan with WAF.

## Working mode (binding, unchanged)

See section 4 of `2026-11-24-part3-done-handoff.md`: simple German, exactly one user step per message, Claude
writes only inside the project directory, the user does all git operations, no AWS change without an explicit go,
secrets never in chat or repo.

## Deutsche Kurzfassung

Task 11 ist umgesetzt und getestet, aber noch nicht live. Offen: Branch committen und mergen, Tag `v3.2.0`
setzen, danach Live-Check von Übersicht und Portfolio.
