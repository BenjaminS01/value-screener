# Showcase Polish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** English UI, a light global stylesheet with visible status badges, a short project overview on login and
start page, and an English README for recruiters.

**Architecture:** Texts are replaced in place (no i18n framework). One `frontend/src/styles.css` imported in
`main.tsx`, components only get `className`s. A new `ProjectOverview` component is shared by `LoginPage` and
`StartPage`. README rewritten in place.

**Tech Stack:** React 18 + TypeScript + Vite + Vitest; Spring Boot (backend), Quarkus (deep-analysis Lambda).

**Spec:** `docs/superpowers/specs/2026-11-26-showcase-polish-design.md`

## Global Constraints

- Working mode: the user does all git (branch, commit, push, PR, tag); Claude gives one command per message.
- Impressum and Datenschutz page **content** stays German. Only their link labels change.
- CSP in AWS is `style-src 'self'; font-src 'self'; img-src 'self'`: no web fonts, no external images, no CDN
  CSS. Use a system font stack. CSS lives in a file (Vite bundles it), not in `<style>` tags in `index.html`.
- No new npm or Maven dependency.
- Nothing security-relevant in public texts (login page, README): no usernames, account names or counts, no exact
  throttle/lockout values, no header names, bucket names, account ids, API URLs, daily limit values.
- Glossary (use exactly): "In portfolio", "Stale", "Not researched", "Holding", "Watching", "Rejected",
  "No status", "Log in", "Log out", "Loading …", "Legal notice (Impressum)", "Privacy (Datenschutz)",
  "Personal view of the operator, not investment advice.", "AI suggestion", "manual", "yes", "no".
- Dates: `en-GB` with `{ day: '2-digit', month: 'short', year: 'numeric', timeZone: 'UTC' }` (e.g. "26 Nov 2026");
  date-time adds `hour`/`minute` 2-digit. Numbers: `en-US`, same fraction digits as today. `parseDecimal` keeps
  accepting `,` and `.`.

## Review Focus

1. A German string left somewhere a user sees it (error paths, empty states, `aria-label`s): grep for umlauts and
   common German words after Task 2; expected only in `ImpressumPage`, `DatenschutzPage`.
2. Badges must stay readable in both colour and text (no colour-only meaning): badge text is the glossary word.
3. Narrow screens (phone): tables scroll horizontally inside their card instead of breaking the page.
4. Screenshots for the README must not show real ADMIN portfolio values: take them locally with sample data or
   as a USER.
5. Tests that query by German text or by `aria-label` must be updated together with the component, never deleted.

---

### Task 0: Branch (user)

- [ ] User runs: `git switch main && git pull && git switch -c feature/showcase-polish`

### Task 1: Backend and Lambda messages in English

**Files:**
- Modify: `backend/src/main/java/com/valuescreener/deepanalysis/DeepAnalysisExceptionHandler.java:30,37`
- Modify: `backend/src/main/java/com/valuescreener/deepanalysis/LocalStubDeepAnalysisInvoker.java:21-22`
- Modify: `deep-analysis/src/main/java/com/valuescreener/deepanalysis/DeepAnalysisDemoHandler.java:38-40`
- Test: `LocalStubDeepAnalysisInvokerTest`, `LambdaDeepAnalysisInvokerTest`, `DeepAnalysisControllerTest` (backend),
  `DeepAnalysisDemoHandlerTest` (deep-analysis)

Exact copy:
- "Tageslimit für die Tiefenanalyse erreicht" → "Daily limit for deep analysis reached"
- "Tiefenanalyse nicht verfügbar" → "Deep analysis unavailable"
- "Noch nicht implementiert – hier wird künftig eine Tiefenanalyse für {isin} ausgegeben." →
  "Not implemented yet – a deep analysis for {isin} will appear here later."
- "Lokale Simulation – keine AWS-Lambda aufgerufen" → "Local simulation – no AWS Lambda was called"
- "Ungültige ISIN" → "Invalid ISIN"; "Hallo von AWS Lambda" → "Hello from AWS Lambda"

- [ ] Step 1: Change the expected strings in the four tests. Run `cd backend && mvn -q test` and
  `cd deep-analysis && mvn -q test`; expected: the changed assertions FAIL.
- [ ] Step 2: Change the strings in the three classes.
- [ ] Step 3: Both test runs pass.
- [ ] Step 4: User commits: `git add backend deep-analysis && git commit -m "feat: english messages in backend and deep-analysis lambda"`

### Task 2: Frontend texts in English

**Files:**
- Modify: `frontend/index.html` (`lang="en"`), `App.tsx`, `api/errorText.ts`, `components/*.tsx`,
  `pages/LoginPage.tsx`, `PortfolioPage.tsx`, `ResearchOverviewPage.tsx`, `ResearchDetailPage.tsx`,
  `research/criteria.ts`, `research/grouping.ts`, `research/format.ts`, `components/Footer.tsx`
- Delete: `pages/LandingPage.tsx`, `pages/LandingPage.test.tsx` (unused, outdated)
- Test: every `*.test.tsx` / `*.test.ts` that asserts on a changed text

Translation is straightforward; fixed copy beyond the glossary:
- Criteria labels: "P/E ratio", "P/B ratio", "5-year average P/E", "5-year average P/B", "Return on equity",
  "Debt to equity", "Current ratio", "Net margin (current year)", "Free cash flow positive (current year)",
  "Net income grew (current year)", "Insider ownership", "Margin trend", "Free cash flow trend",
  "Profit stability", "Interest coverage", "Moat", "Management quality", "Value trap assessment".
- Detail page sections: "A. Overview", "B. Key figures", "C. Qualitative findings", "D. Quarterly reports",
  "E. AI chat", "F. Deep analysis"; placeholder "Coming in a later phase"; held note "The operator holds this
  position"; "Last researched {date}" plus " – consider re-research" when stale.
- Footer disclaimer: "This application is a technical demo project. All content shown is automatically generated
  analysis based on publicly available data and is not investment advice or a recommendation. It does not take
  individual investment goals or financial circumstances into account. Every decision is the user's own
  responsibility."
- Session note: "Session expired, please log in again". Legal back button: "Back to login".
- Grouping fallbacks: "No group", "No country". Portfolio: "Holdings", "Buy position", "Partial sale".

- [ ] Step 1: Update test expectations to the English copy (same assertions, new strings). Run
  `cd frontend && npm test`; expected: the updated tests FAIL.
- [ ] Step 2: Translate the components, `format.ts` locales per Global Constraints, delete `LandingPage`.
- [ ] Step 3: `npm test` passes, `npm run build` succeeds.
- [ ] Step 4: `grep -rnE "[äöüÄÖÜß]|\b(nicht|bitte|keine|Lade|Gruppe)\b" frontend/src --include=*.ts --include=*.tsx`
  shows only `ImpressumPage`/`DatenschutzPage` (and their tests).
- [ ] Step 5: User commits: `git add -A frontend && git commit -m "feat(frontend): english ui texts"`

### Task 3: Global stylesheet and badges

**Files:**
- Create: `frontend/src/styles.css`
- Modify: `frontend/src/main.tsx` (`import './styles.css'`), `App.tsx` (header/main/footer classes),
  pages and components (class names only, plus a wrapper `div.table-wrap` around each table)
- Test: `ResearchOverviewPage.test.tsx`, `ResearchDetailPage.test.tsx`, `InvestmentThesisSection.test.tsx`

**Interfaces (produces):** CSS classes `.app-header`, `.app-main`, `.app-footer`, `.card`, `.table-wrap`,
`.badge` with modifiers `.badge--portfolio`, `.badge--stale`, `.badge--holding`, `.badge--watching`,
`.badge--rejected`, `.badge--muted`; `.btn-link` for text-style buttons (nav, footer links); `.notice`
(info line), `.error` (alerts).

Style direction (builder's choice within it): CSS variables on `:root`, system font stack, light background with
white cards, one accent colour, max content width ~1100 px with 16 px side padding, table cells with vertical
padding ≥ 8 px and column gap ≥ 16 px, zebra or row borders, research list rows as clickable rows with
name · ticker · ISIN · country/group spaced in columns and badges at the end. Badges: small rounded pill, tinted
background, dark text, `margin-left` so they never touch the preceding word.

- [ ] Step 1: Add tests: `expect(screen.getByText('In portfolio')).toHaveClass('badge', 'badge--portfolio')` and
  `expect(screen.getByText('Stale')).toHaveClass('badge', 'badge--stale')` (overview); on the detail page
  `Not researched` → `badge--muted`, `Stale` → `badge--stale`; in the thesis section `Holding` → `badge--holding`,
  `Watching` → `badge--watching`. Run `npm test`; expected FAIL.
- [ ] Step 2: Write `styles.css`, import it, add class names.
- [ ] Step 3: `npm test` and `npm run build` pass.
- [ ] Step 4: User checks the look locally (`./dev-up.sh` or backend + `npm run dev`): login, portfolio, research
  list, detail page, at full width and at phone width.
- [ ] Step 5: User commits: `git add -A frontend && git commit -m "feat(frontend): global stylesheet and status badges"`

### Task 4: Project overview on login and start page

**Files:**
- Create: `frontend/src/components/ProjectOverview.tsx`, `ProjectOverview.test.tsx`
- Modify: `pages/LoginPage.tsx` (overview beside/above the form), `pages/StartPage.tsx` (overview only)
- Test: `LoginPage.test.tsx`, new `StartPage.test.tsx`

**Interfaces (produces):** `export function ProjectOverview(): JSX.Element` (no props).

Content: heading "Value Screener", one sentence ("A research tool for value investing: checked key figures and
assessments with sources, an investment thesis per stock, and the owner's portfolio. It does not trade and does
not recommend."), three tiles **Portfolio**, **Research**, **Tech** (Java 21 · Spring Boot · React · AWS
serverless with Terraform · CI/CD via GitHub Actions), and a link "Source code on GitHub" to
`https://github.com/BenjaminS01/value-screener` with `target="_blank" rel="noopener noreferrer"`. Only general
wording, see Global Constraints.

- [ ] Step 1: Tests: overview renders heading and the three tile headings; the GitHub link has the exact `href`
  and `rel`; `LoginPage` shows the overview and the login form; `StartPage` shows the overview. Run; expected FAIL.
- [ ] Step 2: Implement, style tiles with `.card` in a responsive grid (1 column on phone, 3 on desktop).
- [ ] Step 3: `npm test` and `npm run build` pass.
- [ ] Step 4: User commits: `git add -A frontend && git commit -m "feat(frontend): project overview on login and start page"`

### Task 5: README in English

**Files:**
- Modify: `README.md` (rewrite in place)
- Create: `docs/images/` (screenshots added by the user: `research-overview.png`, `research-detail.png`,
  `login.png`)

Structure: badge · one-paragraph pitch · screenshots · **What it does** (portfolio, research with 18 criteria,
source and date per figure, staleness, history, investment thesis, groups, roles table) · **How research data
gets in** (Mermaid flow: local Claude Code skill in sandbox → owner reviews → sync script → live) ·
**Architecture** (Mermaid: browser → CloudFront → S3 / API Gateway → backend Lambda → DynamoDB, SSM,
deep-analysis Lambda; plus CI/CD line) with "why so" bullets (near-zero idle cost, one origin, data separate from
app) · **Security** (general mechanisms only) · **Built / Roadmap** table (roadmap: quarterly reports, AI chat,
report evaluation, real deep analysis) · **Learning notes** (link `docs/learning/`) · **Development** (current
dev section, translated, outdated steps removed, e.g. the old "Mein Portfolio" manual check) · **Disclaimer**.
Remove the ASCII page sketches (replaced by screenshots).

- [ ] Step 1: Write the README.
- [ ] Step 2: Check: every relative link resolves (`ls` each path); no value from the anti-goal list appears
  (`grep -nE "X-Origin|[0-9]{12}|lockout after|per minute|value-screener-(frontend|artifacts)" README.md` is empty).
- [ ] Step 3: User takes the three screenshots (locally, sample data or USER view) into `docs/images/`.
- [ ] Step 4: User commits: `git add README.md docs/images && git commit -m "docs: english showcase readme"`

### Task 6: Finish

- [ ] Final review of the whole branch, then user pushes and opens the PR; after merge a `v3.3.0` tag deploys.
- [ ] Update `PROJECT-STATUS.md` top section and a new handoff file; update the memory entry point.
