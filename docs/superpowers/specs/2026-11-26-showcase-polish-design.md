# Design: showcase polish (English UI, light styling, project overview, README)

Date: 2026-11-26. Status: design, awaiting user approval. Implements the intent of v2 phase 8 ("login page design
and project description for showcase visitors", `2026-11-03-app-scope-v2-design.md`, Section 12).

## Intent and why

The app should work better as a showcase for recruiters and be nicer to use for the owner's own stock analysis.
Recruiters read the GitHub README and, without an account, only see the login page. **Small scope:** the app stays
as it is, only clearer, nicer and in English. No new features.

## Goals

1. **English everywhere** (the international audience; matches the 2026-08-20 decision "UI texts English"):
   all UI texts, error messages, the footer disclaimer and the fixed thesis note, plus the German strings in the
   backend (`DeepAnalysisExceptionHandler`, `LocalStubDeepAnalysisInvoker`) and the `deep-analysis` Lambda
   (`DeepAnalysisDemoHandler`). `index.html` gets `lang="en"`. Tests follow the new texts.
2. **Nicer design** with one global stylesheet: calm color palette, header bar, consistent spacing between rows
   and columns, readable tables, cards for sections, clear buttons and form fields. Status words such as
   "In portfolio", "Stale", "Holding", "Watching", "Rejected", "Not researched" become visible **badges**.
3. **Short project overview** as one shared component, shown on the login page (for visitors without an account)
   and on the start page after login (replaces "Unsere Anwendung"): one sentence on what the app does, three tiles
   (Portfolio, Research, Tech), link to the GitHub repo.
4. **README updated in place**, in English: top part for recruiters (what it does, why, features, architecture,
   security and cost approach, learning notes in `docs/learning/`), clearly split into **Built** and **Roadmap**
   (quarterly reports, AI chat and report evaluation are planned, not built). Illustrations: Mermaid diagrams
   (GitHub renders them) and 2–3 screenshots taken by the user after the new design. The developer part stays in
   the README below, translated and shortened where it repeats itself.

## Non-goals

- No UI library, no Tailwind, no i18n framework (only one language), no new pages or features.
- No change to layout logic or component structure beyond adding `className`s and small wrappers.
- Impressum and Datenschutz stay **German** (German operator); only their links get English labels
  ("Legal notice (Impressum)", "Privacy (Datenschutz)").
- `PROJECT-STATUS.md`, specs, plans: unchanged (history stays German/English as is).

## Anti-goals

- Revealing anything that helps an attacker: no usernames, account names or counts, no exact login-throttle
  values, no internal header names, bucket names, account ids, API addresses or daily limits — neither on the
  public login page nor in the README. The README security section gets reworded to the general mechanism
  ("rate-limited login with temporary lockout").
- Describing planned features as if they were built.
- A time-consuming redesign.

## Approaches

| Approach | Trade-off | Outcome |
|---|---|---|
| **A. One global `styles.css`** (CSS variables, element styles plus a few classes like `.badge`, `.card`) | no new dependency, tiny diff per component, works with the strict CSP | **chosen** |
| B. Tailwind or a component library | nicer defaults, but new dependency and touches every component | rejected: too much effort |
| C. CSS modules per component | clean isolation, but many new files for a small app | rejected |

## Order of work (one commit each, on a feature branch with PR)

1. English texts (frontend, backend, Lambda, tests); delete the unused, outdated `LandingPage` and its test.
2. `styles.css` + `className`s + badges.
3. Shared project overview on login page and start page.
4. README rewrite (user adds the screenshots afterwards; README references `docs/images/*.png`).

## Left to the builder

Exact colors and spacing values, wording of the overview and README, which Mermaid diagrams (suggested:
architecture, research data flow, roles).

## Deutsche Kurzfassung

Ziel: als Vorzeigeprojekt besser wirken und angenehmer selbst nutzbar sein, mit wenig Aufwand. Alles wird
englisch, nur Impressum und Datenschutz bleiben deutsch. Eine einzige CSS-Datei sorgt für ein ruhiges Design mit
klaren Abständen, und Status-Wörter wie „In portfolio“ werden farbige Badges. Eine kurze Projektübersicht
erscheint auf der Login-Seite und auf der Startseite. Die README wird auf Englisch überarbeitet, trennt Gebautes
von Geplantem und bekommt Mermaid-Diagramme und Screenshots. Nichts Sicherheitsrelevantes wird öffentlich genannt.
Vier Commits auf einem Branch.
