# Handoff: showcase polish (2026-11-27)

For a new Claude session. This file replaces `2026-11-26-task11-done-handoff.md` as entry point.

## Where we stand

- Showcase polish is done on branch `feature/showcase-polish` (English UI, global stylesheet with badges, project
  overview on login and start page, English README with screenshots). Details: `PROJECT-STATUS.md` (top section).
- The user pushes and opens the PR. After merge: deploy with tag `v3.3.0`, then a short live check (login page
  overview, English texts, badges, research list on a narrow screen).

## Next (backlog, user picks)

- Deferred minors of the showcase polish (see `PROJECT-STATUS.md`).
- Cleanup plan (persistence/groups), viewer TLS minimum (needs an own domain), CloudFront flat-rate plan with WAF.
- Later features: quarterly reports, AI chat, in-app deep analysis replacing the local research skill.

## Working mode (binding, unchanged)

See section 4 of `2026-11-24-part3-done-handoff.md`: simple German, exactly one user step per message, Claude
writes only inside the project directory, the user does all git operations (push and PR on GitHub by themselves),
no AWS change without an explicit go, secrets never in chat or repo. Public texts (README, login page) never name
usernames, exact limits or internal names.

## Deutsche Kurzfassung

Der Showcase-Feinschliff ist fertig: alles englisch, schöneres Design, Projektübersicht, neue README. Du pushst und
öffnest den PR, nach dem Merge deployt `v3.3.0`. Danach wählst du aus dem Backlog.
