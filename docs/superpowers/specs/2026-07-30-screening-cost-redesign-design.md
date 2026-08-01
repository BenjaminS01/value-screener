# Screening & Research Cost Redesign — Design

Last updated: 2026-07-31
Status: Design approved by the user. Implementation not started; reconciliation steps 1 (Stage 1
mechanism) and 2 (Company Research Agent design spec) of the agreed sequence are done — see the last
Decision log entry. Step 3 (rewrite `ResearchPromptBuilder`/`CompanyResearchResult` against the
updated agent spec) is next.

**Relationship to earlier specs:** this document replaces the *approach* behind Sections 3–5 and
Risks 1/3 of `2026-07-21-value-screener-design.md` (the Screening Engine, the Data Provider Client,
and the quantitative half of the AI Assessor). It does not replace the Company Research Agent
itself (`2026-07-24-company-research-agent-design.md`) — that component is kept and extended
(Section 6 below), not rebuilt. Written in English per the project's docs-language policy for new
specs; the two earlier specs remain in German.

## Deutsche Zusammenfassung

Das ursprüngliche Design (2026-07-21) wollte eine Screener-API für marktweites Screening nutzen und
pro Top-Kandidat einen Claude-Aufruf mit Websuche für die Geschäftsmodell-/Burggraben-Bewertung.
Beides ist in der Praxis gescheitert: der Screener-Endpunkt des geprüften Fundamentaldaten-Anbieters
ist kostenpflichtig (nur Einzelabfragen sind frei), und ein einzelner Recherche-Call kostete real ca.
$1 (Websuche mit bis zu 7 Suchrunden bei mehrdeutigen Quartalsangaben). Dieses Dokument ersetzt beide
Komponenten durch **einen einzigen, KI-getriebenen Mechanismus ohne externen Fundamentaldaten-Anbieter**:

- **Universum:** alle über Gettex handelbaren Aktien (breit, international), dedupliziert nach
  Unternehmen, gruppiert nach Branche/Sitzland.
- **Dreistufiger Recherche-Trichter** pro Kandidat: Stufe 0 (kein Websuche, Trainingswissen, fast
  kostenlos) → Stufe 1 (ein knapp gedeckelter Suchschritt) → Stufe 2 (volle Tiefenrecherche, jetzt mit
  vier bereits dokumentierten, aber nie umgesetzten Kostenmaßnahmen: Domain-Einschränkung auf
  Primärquellen, fester Suchrunden-Deckel, zugeschnittener Prompt, festes Denkbudget).
- **Eigene wachsende Wissensdatenbank:** jedes Rechercheergebnis (Pass *und* Fail) wird dauerhaft
  gespeichert — Recherche ist eine Investition in Marktabdeckung, keine wiederkehrende Kostenlast.
- **Bewertungsprüfung** ohne teure Peer-Recherche: absolute Notbremse + Vergleich mit der eigenen
  Historie der Firma + einmalig/quartalsweise gecachte Branchen-KGV-Referenztabelle (kostenlos
  verfügbar, siehe Quellen im Entscheidungsverlauf).
- **Auslösung:** täglicher automatischer Zufalls-Tageslauf (1–2 Firmen, Rotation nach "zuletzt
  geprüft", über Branchen gestreut) **plus** zwei manuelle, nur dem eingeloggten Betreiber
  vorbehaltene Wege (gezielte Firma aus der Gettex-Liste; gefilterte Zufallswahl nach Branche/Sitzland)
  — alle drei zusammen zählen gegen **ein gemeinsames** Tages-/Monatsbudget, damit der Kostendeckel
  real bleibt. Öffentliche Besucher lösen nie neue Kosten aus, sie sehen nur bereits fertige
  Ergebnisse.
- **Bewusst zurückgestellt:** eine spätere, kostenlose Kurs-Anreicherung bereits recherchierter Firmen
  (um "wurde inzwischen günstig" ohne neue KI-Kosten zu erkennen) — erst bei Bedarf, wenn die reale
  Trefferquote der Zufallsrotation sich als zu niedrig erweist.
- **Ergänzung (2026-07-30, Produktstrategie-Review):** Zweck geschärft — die App soll keinen fertigen
  Anlage-Verdikt liefern, sondern einen Hinweis, der die eigene, tiefere Recherche des Nutzers wert
  ist (Abschnitt 1). Daraus folgt: kein Tracking realisierter Rendite und keine periodische
  Frische-Neuprüfung (beides erwogen, bewusst nicht umgesetzt — zu aufwändig für den eigentlichen
  Zweck). Stattdessen zeigt das Dashboard pro Kriterium, auf welcher Stufe (0/1/2) und wann es geprüft
  wurde (Abschnitt 7/9) — nutzt nur bereits vorhandene Daten, keine neuen Kosten.
- **Weitere Ergänzung (gleiches Review):** Marktkapitalisierungs-Untergrenze für den Universe Provider
  wieder aufgenommen (aus dem verworfenen Data-Provider-Entwurf übernommen, sonst geht Stage-2-Budget
  an nicht recherchierbare Mikro-Caps verloren) und eine offene Lücke im Company-Research-Agent-Prompt
  geschlossen: die Bewertungsfrage (`valueTrapAssessment`) wartete auf eine Kennzahl aus dem inzwischen
  entfallenen Data Provider Client — diese Abhängigkeit wäre nie aufgelöst worden. Stattdessen liefert
  jetzt Stage 1s ohnehin berechnetes KGV/KBV genau diese Zahl in den Stage-2-Prompt.
- **Ergänzung (2026-07-31, Produktstrategie-Review):** Stage 1 fragt jetzt zusätzlich zu KGV/KBV/ROE/
  Verschuldungsgrad auch die *aktuellen Einzelwerte* (nicht den Trend) von Marge, FCF-Vorzeichen und
  Gewinn-ggü.-Vorjahr ab — kostenlos, da meist auf derselben Kennzahlen-Seite. Dient als früher
  Ablehnungsfilter: ein Kandidat, dessen aktuelles Jahr schon offensichtlich schlecht ist, verbraucht
  nicht erst das knappe Stage-2-Budget. Die eigentliche Mehrjahres-Trendbestätigung bleibt weiterhin
  ausschließlich Aufgabe von Stage 2.
- **Ergänzung (2026-07-31, fachliche Prüfung):** Zinsdeckungsgrad (EBIT / Zinsaufwand) als neues
  Stage-2-Kriterium aufgenommen — Verschuldungsgrad allein zeigt nur die Bilanzstruktur, nicht ob die
  laufenden Zinsen aus dem operativen Ergebnis leicht bedienbar sind. **Ausdrücklich als kostenseitig
  ungeprüft markiert:** anders als die übrigen Stage-2-Kriterien steht diese Kennzahl nicht zuverlässig
  auf einer Standard-Kennzahlen-Seite; ob die Beschaffung teuer wird (zusätzliche Suchrunden), muss die
  Umsetzung noch klären — genau die Art von unvorhersehbaren Mehrkosten, die dieses Redesign eigentlich
  vermeiden soll (Abschnitt 3, Risiko 3).
- **Ergänzung (2026-07-31, Speicherung & Anzeige):** Jeder `ResearchRecord` speichert jetzt zusätzlich
  pro Kriterium den **Quellenverweis** und den bereits vom Company-Research-Agent erzeugten
  **Value-Trap-Assessment-/Low-Confidence-Text** (beides bisher erzeugt, aber nie gespeichert) sowie bei
  Ablehnung, **welches konkrete Kriterium** den Fail ausgelöst hat. Im Dashboard zeigen Vorschläge (Pass)
  jetzt auch den Quellenverweis pro Kriterium — der Nutzer kann seine eigene Recherche genau dort
  fortsetzen, wo die KI aufgehört hat. Neu: eine **öffentliche Liste abgelehnter Kandidaten** (nicht nur
  intern) — auf ausdrücklichen Wunsch des Nutzers **ohne** die technische Stufe/das Kriterium zu zeigen,
  nur Firma, Datum und eine neutrale Kategorie (z. B. "Bewertung", "finanzielle Stabilität"). Die vollen
  technischen Details bleiben unbeschränkt in `ResearchRecord`/Wissensdatenbank für Betreiber und
  Rotationslogik erhalten.
- **Ergänzung (2026-07-31, Managementqualität):** neues qualitatives Stage-2-Kriterium
  **Managementqualität/Kapitalallokation** (Aktienrückkäufe vs. Verwässerung, disziplinierte
  Übernahmen) — läuft in derselben Tiefenrecherche wie die Burggraben-Einschätzung mit, kein separater
  Aufwand. Ergänzt um **Insider-/Gründeranteil** als billigen Stage-1-Wert (gleiche Kennzahlen-Seite wie
  KGV/KBV) als Proxy für eigenes investiertes Kapital des Managements. Kundenkonzentration,
  regulatorische Risiken und aktuelle Negativschlagzeilen bewusst nicht als eigene Kriterien aufgenommen
  — fließen bereits in die bestehende qualitative Einschätzung und den Value-Trap-Check ein.
- **Ergänzung (2026-07-31, Darstellung & Branchen):** Dashboard neu als **Screener-Übersicht mit
  Detailseite** statt flacher Liste — sortierbare/filterbare Tabelle (Bewertungsabstand zu eigener
  Historie/Branchenschnitt, neuer **Verifizierungstiefe-Indikator**, z. B. "5/8 Kriterien auf Stage 2
  bestätigt", berechnet aus bereits vorhandenen Daten, keine neue Speicherung) plus Detailseite pro
  Vorschlag mit allen Kriterien einzeln inkl. Quellenverweis. Auf der Ablehnungsliste wird die Gruppe
  "nur an Bewertung gescheitert" (fundamental gut, aktuell nur zu teuer) hervorgehoben — praktisch eine
  kostenlose Watchlist. Außerdem geklärt: **Branchenschema** — die 11 GICS-typischen Obersektoren
  (Energie, Grundstoffe, Industrie, Nicht-Basiskonsumgüter, Basiskonsumgüter, Gesundheitswesen,
  Finanzen, Informationstechnologie, Kommunikationsdienste, Versorger, Immobilien), nicht das volle
  lizenzierte GICS-System — passend zu dem, was Finanzseiten ohnehin ausweisen, keine eigene
  Klassifizierungsarbeit nötig.
- **Ergänzung (2026-07-31, Coverage Map & Watchlist-Nachprüfung):** die tägliche automatische
  Ziehung gewichtet jetzt zusätzlich zur "am längsten nicht geprüft"-Regel aktiv nach
  Branchen-Unterrepräsentation (aus der Wissensdatenbank), auf Wunsch des Nutzers, statt die Verteilung
  nur passiv in einer Coverage Map anzuzeigen — die Karte zeigt genau dieses Signal, das die Ziehung
  ohnehin schon nutzt. Neu: ein vierter, betreibernur zugänglicher Auslöseweg **Watchlist-Nachprüfung**
  — auf Wunsch des Nutzers manuell statt automatisch/periodisch: der Betreiber wählt einen Eintrag aus
  der "nur an Bewertung gescheitert"-Watchlist, es wird nur Stage 1 erneut ausgeführt (nie Stage 2),
  berührt das gemeinsame Budget also gar nicht. Eine volle Stage-2-Nachprüfung bleibt eine separate,
  bewusste Entscheidung über den bestehenden Ticker-Auslöseweg.
- **Ergänzung (2026-07-31, Code-Abgleich):** bestehender Code gegen dieses Redesign geprüft, damit die
  Umsetzung nicht in die falsche Richtung läuft. Ergebnis: Phase 1 (`backend/`, Portfolio/Auth) ist
  komplett unberührt. Die Company-Research-Agent-Infrastruktur (Tasks 1–7) bleibt nutzbar, aber ihr
  Prompt und Ausgabemodell decken noch den alten, schmalen Scope vom 24.07. ab — nicht den vollen
  Section-6-Kriterienkatalog. Alle übrigen Redesign-Komponenten (Stage 0/1, Universe Provider,
  Selection Logic, Knowledge Base, Dashboard, Sector Benchmark Cache, Scheduler) existieren im Code
  noch gar nicht — dort besteht kein Konfliktrisiko. Vereinbarte Reihenfolge (Details im
  Entscheidungsverlauf unten): erst Stage-1-Mechanismus entscheiden, dann Company-Research-Agent-Spec
  und -Code (Prompt/Modell) auf den vollen Kriterienkatalog umschreiben, dann erster echter Live-Call
  gegen die neue Version, erst danach der breite Implementierungsplan für die restlichen, komplett
  neuen Komponenten.
- **Ergänzung (2026-07-31, Machbarkeitsprüfung):** Plan als grundsätzlich umsetzbar bewertet, mit einer
  offenen zentralen Wette: das Kostenziel (niedrige einstellige Euro/Monat bei 1–2 Stage-2-Läufen/Tag)
  ist bisher nur geschätzt, nie an einem echten erfolgreichen Call bestätigt — der einzige reale Call
  landete beim Worst Case (~$1, plus Fehler). Extern bestätigt: die Websuche-Tool-Gebühr selbst ist mit
  $0.01/Suche trivial: der eigentliche Kostentreiber ist, dass Websuche-Inhalte bei **jeder weiteren
  Gesprächsrunde erneut als Input-Tokens abgerechnet werden** — Kosten wachsen also mit der Anzahl
  Suchrunden überproportional, nicht linear. Damit ist der Suchrunden-Deckel (Maßnahme 2) der wichtigste
  Hebel, wichtiger als Domain-Einschränkung. Der Suchrunden-Deckel selbst tauscht aber Kostenrisiko gegen
  Korrektheitsrisiko (AAPL-Simulation brauchte 7 Runden, Deckel liegt bei 5) — bekanntes, akzeptiertes
  Risiko, hier nochmal explizit verortet. Für den Universe Provider wurde ein plausibler kostenloser
  Datenweg gefunden (Börse-München-Pflichtdatenfeed für verzögerte Vor-/Nachhandelsdaten, EU-
  Transparenzpflicht) — vorher nur eine offene Frage, jetzt ein konkreter Ansatzpunkt, Details bleiben
  Umsetzungsarbeit. Neu benannt, noch nicht im Architektur-Abschnitt festgehalten: der tägliche
  Scheduler-Lauf braucht einen MCP-Client vom Backend zum separat deploybaren Company-Research-Agent
  (Lambda) übers Netz — bisher stillschweigend vorausgesetzt, nicht explizit entschieden.
- **Ergänzung (2026-07-31, Stage-1-Mechanismus final entschieden):** Stage 1 nutzt denselben
  Agent-Mechanismus wie Stage 2 (gleiche Websuche-/Zitat-/Timeout-Guardrails), aber als **zwei getrennte
  MCP-Tools mit zwei getrennten Ausgabetypen** — `research_company` (Stage 2, voller Kriterienkatalog)
  und `quick_research_company` (Stage 1, nur die numerische Momentaufnahme) — statt einem Tool mit
  Modus-Flag. Schärfer als die ursprüngliche Notiz vom 30.07., weil die Auswahl der Stufe von
  deterministischem Backend-Code getroffen wird, nicht von einem LLM, das zwischen Tools wählt; zwei
  saubere Typen passen außerdem direkt auf die ohnehin unterschiedlichen Felder von `ResearchRecord`
  pro Stufe (Section 9). Löst den seit 30.07. offenen Punkt, Schritt 1 der Umsetzungsreihenfolge ist
  damit erledigt.
- **Ergänzung (2026-07-31, Reconciliation Schritt 2 erledigt):** die Company-Research-Agent-Spec
  (`2026-07-24-company-research-agent-design.md`) wurde auf den vollen Kriterienkatalog und die zwei
  MCP-Tools umgeschrieben. Dabei zwei echte Widersprüche zur alten Agent-Spec gefunden und aufgelöst
  (nicht nur die bereits bekannte Kriterienkatalog-Lücke): die alte Spec schloss automatische
  Auslösung explizit aus, und Kapitalallokation war dort explizit "considered, not adopted" — beides
  wird jetzt umgedreht, da das Redesign genau das Gegenteil vorsieht. Guardrail B (Fakten-Abgleich,
  verwies auf den entfallenen Data Provider Client) wurde nach Rückfrage beim Nutzer zu einer
  Stage-1-vs-Stage-2-Konsistenzprüfung umdefiniert. Noch nicht angefasst: der eigentliche Code
  (Schritt 3).

## 1. Purpose

Deliver the same product goal as the original design — regularly surfacing new stock ideas that are
fundamentally strong, fairly valued, and crisis-resistant — without depending on either a paid
market-wide screener endpoint or an unpredictable, expensive per-candidate AI web-search call. The
guiding cost target: AI research spend in the low single-digit euros per month, fully predictable,
never spiking on a single call or a single visitor.

**What "surfacing" means (clarified 2026-07-30):** the goal is not for the app to deliver a finished
verdict the user acts on directly — it's a lead-generation tool. A Suggestion only needs to be
promising enough to justify the user's own, deeper manual research before any real decision; it does
not need to be a fully self-validated pick. This directly shapes Section 6/7/9 below: the app's job is
to be honest about *how confidently* each criterion was established, not to hide that behind a binary
pass/fail.

## 2. Scope

### In scope
- A broad, international universe of Gettex-tradable equities as the search space (not limited to a
  single index), deduplicated by company/ISIN rather than by exchange listing
- A three-tier cost funnel per research candidate (Section 5)
- A self-built, permanently growing knowledge base of research outcomes (pass and fail alike)
- One daily automatic batch run, plus two operator-only manual trigger paths, all sharing one budget
  cap (Section 7–8)
- A free, periodically-cached sector P/E benchmark table for cheap relative-valuation context
  (Section 6)
- Reuse of the same Stage 2 mechanism for existing portfolio positions (small, fixed volume — not a
  cost concern)

### Explicitly out of scope (unchanged from the original design)
Trading/order execution, broker integration, multi-user accounts, CSV/statement import, email
notifications, a free-form chat interface.

### Deliberately deferred, not part of this design iteration
- Live/cheap price enrichment of already-researched companies to detect "became cheap since last
  check" without new AI cost (called "Approach B" in the decision log) — revisit only if the real
  hit rate under this design turns out too low.
- The concrete technical mechanics of acquiring the Gettex universe list and the sector benchmark
  table (web search/scrape vs. another method) — an implementation-level task, not a design decision.
- Exact numeric thresholds for Stage 0/1 pass/fail — calibrated during implementation, not fixed
  here.

## 3. Why the original design had to change

Two of the "open risks" flagged in Section 8 of the 2026-07-21 design materialized during
implementation, not just as theoretical risk:

- **Risk 1 (screener access):** the evaluated fundamentals provider does not expose its market-wide
  screener endpoint on the free tier — only single-ticker lookups are free. A market-wide daily
  screen as originally designed is not available without a paid plan.
- **Risk 3 (AI cost):** a real Company Research Agent call (Claude + hosted web search) was reported
  costing approximately $1. A static, offline cost analysis in the Company Research Agent spec's
  decision log (2026-07-26) estimated $0.06 (low) to $0.85 (worst case) per call — driven by
  unpredictable multi-round web search. A manual AAPL simulation needed 7 search rounds just to
  disambiguate analyst fiscal-quarter commentary from the actually filed 10-Q.

Net effect: neither half of the original pipeline ("get a broad candidate list" and "AI-assess the
top candidates") could be relied on to be both broad and cheap under the original architecture.

## 4. Core idea

Drop the idea of a separate, purpose-built numeric fundamentals provider entirely. Use AI research
— with web search used only where and to the extent it earns its cost — as the single source of
both quantitative facts and qualitative judgment, and treat every research outcome (not just
successful ones) as a permanent addition to a self-owned knowledge base. Market coverage grows over
time as an asset instead of being re-purchased on every check.

## 5. Three-tier research funnel

Applied to every candidate, regardless of whether it was selected by the automatic daily rotation or
by an operator's manual trigger (Section 7):

| Tier | What it does | Cost | Outcomes |
|---|---|---|---|
| **Stage 0** | LLM judgment using only its trained knowledge, no tool calls, against a small set of hard disqualifying criteria | near-zero | *clearly bad* → reject, no further cost; *looks promising* → advance; *uncertain / company not well known to the model* → **also advance** (never rejected for cost reasons alone — a lesser-known company being unknown to the model is not evidence it's a bad company) |
| **Stage 1** | One tightly bounded web search step (current quote + 1–2 headline figures from a reliable source) to ground/validate Stage 0's read | low | confirms reject, or confirms enough potential to justify the expensive tier |
| **Stage 2** | Full deep research via the existing Company Research Agent — now tightened per Section 6 | the deliberately budgeted, capped cost | full structured fundamentals + qualitative moat/business-model write-up + final pass/fail against the complete criteria set (Section 6) |

Every outcome at every tier — not only Stage-2 passes — is written to the knowledge base with a
timestamp, so the rotation logic (Section 7) always knows what was checked, when, and how far it
got.

## 6. Criteria — what's checked, at which tier, and how it's sourced cheaply

| Criterion | Cheap source | Effort | Tier |
|---|---|---|---|
| Understandable business model | Basic "what does this company do" description | very low | Stage 0 |
| Recognizable moat (rough read) | Often inferable from general/trained knowledge for known companies | low | Stage 0 (rough), deepened in Stage 2 |
| Management quality / capital allocation (added 2026-07-31) | Qualitative read on buyback-vs-dilution history and M&A discipline, alongside the moat write-up in the same deep research pass | low incremental (same research pass as moat) | Stage 2 |
| Insider/founder ownership share (added 2026-07-31) | Standard key-statistics figure on most finance pages, supports the management-quality read above | low, same search hit as P/E/P/B | Stage 1 |
| Current P/E, P/B | Standard key-statistics figure on almost any finance page | low, one search hit | Stage 1 |
| Current ROE, debt/equity | Same standard key-statistics page as above | low | Stage 1 |
| Current ratio | Sometimes on the same page, sometimes needs a balance-sheet detail | medium | Stage 1 if available, else Stage 2 |
| Current-year net margin (single point, not the trend) | Same key-statistics page as P/E/ROE | low, same search hit | Stage 1 (reject filter only) |
| Current-year FCF sign (positive/negative, not the trend) | Often on the same page's cash-flow summary line | low, same search hit | Stage 1 (reject filter only) |
| Current-year net income vs. prior year (single point, not the trend) | Often on the same page's earnings summary | low, same search hit | Stage 1 (reject filter only) |
| Margin trend (stable/growing, multi-year) | Needs multi-year history | high | Stage 2 |
| Free cash flow positive & growing (multi-year) | Cash-flow-statement-specific, multi-period | high | Stage 2 |
| No strong profit decline over 5–10 years (multi-year) | Multi-year history — the most expensive criterion to verify | highest | Stage 2 |
| Interest coverage (EBIT / interest expense) | Not on standard key-statistics pages — needs income-statement line items (EBIT, interest expense), **cost to obtain not yet confirmed cheap, see caveat below** | unknown, likely medium–high | Stage 2 |

**New criterion, cost not yet confirmed (added 2026-07-31):** debt/equity (already checked, Stage 1)
only shows balance-sheet structure, not whether current earnings comfortably cover the interest actually
owed — a company can look safe on D/E and still be tight on debt service if its debt is short-term or
variable-rate. Interest coverage catches that. Unlike the Stage-1 snapshot values above, this is *not*
reliably on a single standard key-statistics page — EBIT and interest expense are often income-statement
detail. **Before this is treated as a standard, always-on Stage 2 check, implementation must confirm it
doesn't quietly reintroduce the same kind of per-candidate search-cost blowup this whole redesign exists
to avoid (Section 3, Risk 3)** — if the agent needs extra search rounds specifically to dig this figure
out, it competes with the fixed round ceiling and effort budget (Section 7) and its actual cost must be
checked against those, not assumed free just because the other multi-year criteria already share Stage
2's cost.

**Management quality / capital allocation (added 2026-07-31):** moat and business model describe
whether the business itself is good; they say nothing about whether the people running it allocate its
capital well. Buffett weighs this almost as heavily as the moat itself — a good business can still be a
bad investment under management that dilutes shareholders or chases empire-building acquisitions
instead of sensible buybacks/reinvestment. Folded into the same Stage-2 deep-research pass that already
produces the moat/business-model write-up (low incremental cost, same research call), covering
buyback-vs-dilution history and capital-allocation discipline (sensible M&A vs. growth-for-its-own-sake).
Supported by **insider/founder ownership share** as a cheap Stage-1 snapshot value (same
key-statistics page as P/E/P/B) — meaningful insider ownership is a proxy for management having its own
capital at stake alongside outside shareholders ("skin in the game"). Deliberately **not** added as
separate formal criteria: customer concentration, regulatory/legal red flags, recent negative news —
these are judged to already surface naturally within the existing qualitative write-up and the
`valueTrapAssessment` guardrail when material, and formalizing them as standalone checks would pad the
criteria list without adding real signal.

**Stage 1 as an early reject filter for Stage 2's criteria (added 2026-07-31):** margin trend, FCF
growth, and multi-year profit stability are Stage-2-only criteria because they need *trend* data — but
each one's *current single-year value* (this year's margin, this year's FCF sign, this year's net
income vs. last year) is typically visible on the same key-statistics page Stage 1 already visits for
P/E/P/B/ROE/D-E, at no extra search cost. Stage 1 now captures these current-year values too, purely as
a reject filter — a candidate whose current year is already clearly bad (e.g. FCF negative, profit down
sharply) is rejected before the scarce Stage-2 budget (Section 10) is spent on it. This does not replace
Stage 2's job: confirming *stability/growth over 5–10 years* still requires the full multi-year lookup,
which only Stage 2 does. A candidate can still fail at Stage 2 even after passing this Stage-1 filter,
if the trend turns out weak despite a fine current-year snapshot.

**Valuation check (industry-relative, but without live peer research):** the original design computed
an industry median for free because it was a side effect of the paid screener's market-wide result
set. Without that, a true live peer comparison would require researching 2–3 comparable companies per
candidate — exactly the kind of extra cost this redesign avoids. Instead, valuation is judged by
combining three signals, only one of which costs anything per candidate:

1. **Absolute floor (Stage 0, free):** negative or extreme P/E (e.g. > 50) is rejected outright,
   regardless of everything else.
2. **Own historical range (Stage 1, one search hit):** current P/E/P/B vs. the company's own 5-year
   average — commonly published directly on standard finance pages, no extra computation needed.
3. **Cached sector benchmark (Stage 1, effectively free per candidate):** current P/E vs. a small
   (~10–20 sector) reference table of average sector P/E ratios. Multiple providers publish this table
   for free (confirmed via research: Siblis Research, GuruFocus, FullRatio, Basis Report). The table
   is fetched and cached **once, refreshed only periodically (e.g. quarterly)** — not researched per
   candidate — then every candidate's own P/E (already fetched in step 2) is compared against it via a
   simple lookup.

A candidate passes the valuation check only if it clears the absolute floor, sits at or below its own
historical average, and isn't materially above its sector's cached benchmark.

## 7. Architecture / components

- **Universe Provider** — acquires and periodically refreshes the list of Gettex-tradable equities,
  deduplicated by company/ISIN (a single company often has multiple Gettex listings), grouped by
  sector and country of headquarters. Acquisition mechanics are an implementation detail (Section 2).
  Carries a **market-cap floor to exclude micro-caps** (revived from the superseded data-provider-
  client draft's own Section 2, where it was adopted for risk reasons and never actually dropped, just
  never re-stated here) — Gettex lists many thinly traded, data-sparse secondary listings, and every
  candidate the Selection Logic draws that turns out un-researchable in Stage 1/2 for lack of
  available information is wasted budget against the shared cap (Section 8/10). No upper cutoff —
  large, well-known opportunities should still surface. **Sector taxonomy (decided 2026-07-31):** the
  fixed set of 11 top-level GICS-style sector names (Energy, Materials, Industrials, Consumer
  Discretionary, Consumer Staples, Health Care, Financials, Information Technology, Communication
  Services, Utilities, Real Estate) — not the full licensed GICS structure (industry groups/
  sub-industries), just the well-known top-level names as informal categories. Chosen because almost
  every finance page already reports a company's sector using this scheme or something close enough
  for the researching LLM to normalize into it during the Stage 0/1/2 search it's already doing — no
  separate classification research or maintained mapping table needed. A finer breakdown (GICS industry
  groups, ~24) was considered and rejected: at 1–2 Stage-2 executions/day, candidates accumulate too
  slowly per group for a finer split to be useful for filtering.
- **Sector Benchmark Cache** — small reference table of sector-average P/E ratios, refreshed
  periodically (e.g. quarterly) from free public sources, used for the valuation check in Section 6.
  Keyed by the same 11-sector taxonomy as the Universe Provider above, so a candidate's sector (already
  captured during research) maps directly to its benchmark row with no separate lookup logic.
- **Selection Logic** — the entry point into the funnel. Normally draws candidates weighted toward
  "longest since last checked" (the automatic daily path), but accepts four trigger modes:
  1. automatic daily draw (weighted-random)
  2. operator-supplied filters (sector and/or country of headquarters) for an on-demand filtered
     random draw
  3. operator-supplied specific ticker (must be in the Universe Provider's Gettex list)
  4. **operator-triggered watchlist re-check (added 2026-07-31)** — see the Watchlist Re-check bullet
     below; distinct from modes 1–3 because it doesn't enter the three-tier funnel from Stage 0, it
     re-runs Stage 1 only on an existing candidate.

  Modes 1–3 all feed the *same* three-tier funnel (Section 5) — there is no separate code path for
  manual vs. automatic new-candidate draws. **Sector balancing (added 2026-07-31, sharpened from
  "stratified across sectors"):** the automatic daily draw's weighting is not just recency-based — it
  also actively weights toward sectors that are currently under-represented in the Knowledge Base
  relative to the others (the same 11-sector taxonomy used throughout, Section 7 above), so the slow
  1–2/day rotation doesn't drift toward covering a few sectors deeply while others stay empty. This is
  the same coverage data the Coverage Map (below) displays — the map is the visible readout of the
  exact signal already driving the draw, not a separate calculation.
- **Company Research Agent** (existing component, extended) — Stage 0 and Stage 1 are new,
  lightweight steps added ahead of the existing agent. **Stage 1 mechanism, decided 2026-07-31:**
  Stage 1 reuses the same underlying agent mechanism as Stage 2 (Claude + Anthropic's hosted
  `web_search` tool, same citation-cross-check/prompt-injection/timeout/usage-logging guardrails), just
  bounded differently (`maxUses(1)` instead of `5`, a narrowly scoped prompt asking only for the
  handful of Stage-1 figures in Section 6). Concretely: **two separate MCP tools with two separate
  output records** — `research_company` (Stage 2, full Section 6 criteria set) and
  `quick_research_company` (Stage 1, just the numeric snapshot) — rather than one tool with a mode
  flag and a single result type with mode-dependent empty fields. Rejected: a wholly separate,
  simpler search mechanism outside the agent (would reopen the "which fixed provider" question this
  redesign deliberately avoids, Section 4, and duplicate infrastructure for the same underlying task).
  Chosen over a single-tool/mode-flag variant because Selection Logic (deterministic backend code, not
  an LLM choosing between tools) gets no benefit from one self-describing tool, while two clearly-typed
  results map directly onto `ResearchRecord`'s (Section 9) already-different Stage-1-vs-Stage-2 field
  sets instead of fighting them. Stage 2 itself (the existing agent) is
  tightened with four cost measures already identified but not yet implemented in its own spec's
  decision log. **Code-vs-spec gap confirmed 2026-07-31:** the currently committed
  `ResearchPromptBuilder`/`CompanyResearchResult` (Tasks 3/4 of the agent's own plan) still implement
  only the original 2026-07-24 scope (`summary` + `valueTrapAssessment` + `sources`) — none of the
  Section 6 criteria below are in the prompt or output model yet. This needs a rewrite against an
  updated agent spec, not an incremental extension (see Decision log).
  1. Web search restricted to primary sources first (e.g. SEC EDGAR, official investor-relations
     pages) instead of open web search — directly targets the fiscal-quarter-disambiguation cost
     driver found in the AAPL simulation.
  2. A fixed, tight ceiling on search rounds instead of "search until satisfied."
  3. A prompt scoped narrowly to the specific criteria in Section 6, not an open-ended "tell me
     everything about this company."
  4. An explicit, bounded thinking/effort budget instead of unconstrained reasoning.
  5. **Stage 1's valuation figures (current P/E, P/B, Section 6) passed into the Stage 2 prompt as
     given context**, resolving a previously-open item from the agent's own spec: its
     `valueTrapAssessment` was left deliberately unable to judge "is the valuation explained by
     fundamentals" because the prompt was never given an actual multiple, with the fix explicitly
     deferred until a Data Provider Client / `FundamentalSnapshot` existed to supply one (see that
     spec's decision log, 2026-07-26). That component is now permanently out of scope under this
     redesign (Section 4) — the dependency would otherwise never resolve. Stage 1 already computes the
     same figure for free, immediately before Stage 2 runs, and is now the fix's actual source instead.
- **Knowledge Base** — persists every research outcome from every tier (pass and fail), with
  timestamp and tier reached. Powers the recency-weighted **and now sector-balanced** rotation (see
  Selection Logic above) and, over time, lets the real hit rate be measured (Section 11).
- **Coverage Map (added 2026-07-31)** — operator-facing view showing how many candidates have been
  checked so far per sector (the 11-sector taxonomy) and country, drawn directly from the Knowledge
  Base — the same counts already used to drive the Selection Logic's sector-balancing weight above,
  just made visible. Purpose: lets the operator aim the filtered-random and specific-ticker manual
  trigger paths at genuinely under-covered areas instead of guessing, and shows concretely how the
  accepted "coverage speed sacrificed for budget discipline" trade-off (Section 11, risk 1) is playing
  out over time. No new research or cost — pure read of existing data.
- **Scheduler** — triggers one automatic daily batch via the Selection Logic. Per the user's explicit
  budget/coverage-speed trade-off (Section 11), the daily figure is deliberately small — on the order
  of 1–2 Stage-2 executions per day — favoring cost discipline over how fast the universe gets
  covered. **Invocation topology gap (found 2026-07-31, feasibility review):** the Company Research
  Agent is a separately deployed module (Lambda, MCP over Streamable HTTP, per its own spec), while the
  Scheduler/Selection Logic live in `backend/` (App Runner) — so the daily run needs an **MCP client
  from `backend/` to the Company Research Agent's Lambda over the network**, not a direct in-process
  call. This was implicitly assumed, not yet explicitly designed; needs to be named as its own item in
  the implementation plan (reconciliation sequence step 5 above), and ties back into the originally
  paused MCP learning thread (see `docs/superpowers/specs/2026-07-21-value-screener-design.md` Risks
  4/5).
- **Manual trigger paths** — all operator-only, protected by the existing single-user login: (a) pick a
  specific Gettex ticker, (b) request a filtered random draw (sector/country), (c) **trigger a Watchlist
  Re-check (added 2026-07-31, see below)**. Public visitors never trigger new research of any kind —
  they only ever read already-completed results.
- **Watchlist Re-check (added 2026-07-31)** — an operator-only manual trigger, separate from the
  three-tier funnel entry points above: picks an entry from the "valuation-only" rejected-candidates
  sub-group (Section 7 Rejected-candidates list) and re-runs **Stage 1 only** to refresh its current
  P/E/P/B against the same historical/sector-benchmark check (Section 6). Deliberately **not** a Stage 2
  re-run and **not** automatic/scheduled — a candidate that already passed every fundamental check and
  only failed on price doesn't need its fundamentals re-verified, just a cheap current-price read,
  triggered when the operator is specifically curious. Doesn't touch the shared Stage-2 budget cap
  (Section 10) at all, since it never invokes Stage 2. If the refreshed valuation now clears the bar,
  the result is logged as a new `ResearchRecord` entry (the existing append-only, never-overwrite
  pattern, Section 5); a full Stage 2 re-run is then a separate, deliberate decision via the existing
  specific-ticker manual trigger — this re-check only produces a stronger signal, it doesn't
  auto-cascade into spending the capped budget.
- **Dashboard** — restructured as a **screener overview + drill-down** (decided 2026-07-31), not a flat
  list or a feed, so a growing knowledge base stays scannable and comparable across many candidates:
  - **Overview table**, one row per Suggestion, sortable/filterable: ticker, company, sector (the
    11-sector taxonomy above), country, valuation delta (current P/E vs. the company's own historical
    average *and* vs. the cached sector benchmark — the actual buy-signal information, not a raw
    multiple), a **verification-depth indicator** (e.g. "5/8 criteria confirmed at Stage 2"), and the
    as-of date. Verification depth is a new derived summary computed from the per-criterion
    tier-of-origin data already captured in `ResearchRecord` (Section 9) — no new research or cost, just
    an aggregate view of data already there. Sortable by valuation delta (surfaces the most compelling
    value candidates first), verification depth, or recency.
  - **Detail page per Suggestion** (drill-down from a table row): every criterion individually, with
    value, tier, as-of date, and **source reference** (URL/page the figure came from) — the Company
    Research Agent already produces this per its own guardrails (source-reference, not verbatim quote),
    it was just never persisted or displayed before; showing it directly serves Section 1's purpose,
    since the user's own follow-up research can start exactly where the AI's did instead of re-finding
    the same figures. Also shows the qualitative moat/business-model/management-capital-allocation text
    in full, plus any value-trap or low-confidence caveats — not hidden behind a binary pass.
  
  This turns the dashboard from a binary pass/fail into a map of where a candidate is well-verified vs.
  where it's still an untested read, which is exactly what the user needs to judge whether it's worth
  spending their own research time on (Section 1).
- **Rejected-candidates list (added 2026-07-31, public)** — a second public list, alongside
  Suggestions, of candidates that did **not** pass, at any tier. Unlike Suggestions, this list
  deliberately does **not** show which internal tier/criterion caused the rejection (that's
  implementation detail, not user-facing information) — a Stage-0 no-search LLM judgment and a full
  Stage-2 research fail are very different in reliability, and showing the raw tier alongside a
  negative claim about a named company risks implying more confidence than a shallow tier actually
  earned. Instead, each entry shows the company name/ticker, the as-of date, and **one neutral category**
  from a small fixed set (e.g. "valuation", "financial stability", "profitability trend", "business
  clarity") describing which pillar didn't clear the bar — derived from the already-stored per-criterion
  fail reason (Section 9) but translated away from raw pipeline detail. Phrasing follows the same
  descriptive-not-imperative wording policy already established for all AI-generated text in the
  original design (`2026-07-21-value-screener-design.md`, Section 9) — e.g. "valuation currently above
  historical range" rather than any stronger claim. The full technical detail (exact tier, criterion,
  values) remains in the underlying `ResearchRecord`, unrestricted, for the operator's own use and for
  rotation logic (Section 7 Knowledge Base) — only the public list view is simplified. **Highlighted
  sub-group (added 2026-07-31): "valuation-only" rejects** — candidates whose *only* failing category
  is "valuation" (i.e. fundamentally sound, just currently priced above their own history/sector
  benchmark) are surfaced as their own group at the top of the list, not mixed in with fundamentally
  weak rejects. For a value investor these are effectively a free watchlist — "good business, wrong
  price, right now" — derived purely from the category field already stored, no new research or cost.

## 8. Data flow

```
Trigger (any of a-c enters the funnel below; d is separate, see note):
  (a) Scheduler (daily, automatic, weighted-random: recency + sector-balance)
  (b) Operator: filtered random draw (sector/country)
  (c) Operator: specific Gettex ticker
  (d) Operator: Watchlist Re-check — bypasses the funnel below entirely, re-runs Stage 1 only on
      an existing "valuation-only" rejected candidate, no Stage-2 budget touched (Section 7)
        │
        ▼
  Selection Logic picks one candidate (or, for (c), uses the given one)
        │
        ▼
  Stage 0 (no search) ──clearly bad──┐
        │ promising / uncertain      │
        ▼                            │
  Stage 1 (bounded search) ──confirmed bad──┤
        │ confirmed potential        │
        ▼                            │
  Stage 2 (full research, tightened per Section 7) ──fail──┤
        │ pass                       │
        ▼                            ▼
  Suggestion visible on        Rejected candidate: logged in Knowledge Base (full detail,
  public Dashboard (full       any tier) + shown on public Rejected-candidates list
  detail, incl. source refs)   (name/ticker + date + neutral category only, no tier/
                                criterion detail)
```

All three trigger modes draw against **one shared daily/monthly budget** for Stage 2 (Section 9) —
manual use on a given day reduces what's left for the automatic run that day, not an addition to it.

Portfolio monitoring (existing positions) reuses the same Stage 2 mechanism directly (no Stage 0/1
gating needed — the candidate set is small and fixed), run at a low, infrequent cadence (e.g. monthly
per position) since the position count is small enough not to be a cost concern.

## 9. Data model

- **UniverseEntry** — ticker(s), ISIN, company name, sector, country of headquarters, listing
  venue(s) on Gettex. **`sector` is one of the 11 fixed GICS-style top-level names (Section 7)**, not a
  free-text field — keeps grouping/filtering and the `SectorBenchmark` lookup consistent.
- **SectorBenchmark** — sector label (same 11-value fixed set as `UniverseEntry.sector`), cached
  average P/E, date last refreshed.
- **ResearchRecord** (the knowledge base entry) — ticker/ISIN, date, tier reached, per-tier
  outcome/reason, extracted structured fundamentals (P/E, P/B, ROE, margins, debt/equity, current
  ratio, current-year FCF sign, current-year net income vs. prior year, interest coverage,
  insider/founder ownership share, FCF trend) where obtained, qualitative business-model/moat/
  management-capital-allocation text, final pass/fail. Each per-criterion value
  (Section 6) carries its own **tier-of-origin and as-of date**, not just the record-level tier reached
  — e.g. a Stage-2 record can still have a moat read that was only ever confirmed at Stage 0, and the
  dashboard (Section 7) needs that per-criterion granularity, not the record's overall tier, to show it.
  **Added 2026-07-31, to support the dashboard changes in Section 7:** each per-criterion value also
  carries a **source reference** (URL/page it was read from) where obtained by search (Stage 1/2 only —
  Stage 0 has no source, it's trained knowledge); the record also carries the qualitative
  **value-trap-assessment and low-confidence-flag** text the Company Research Agent already produces
  per its own spec's guardrails (previously not persisted); and, for a "fail" outcome, **which specific
  criterion caused it**, not just the final fail verdict — needed both to derive the public
  Rejected-candidates list's neutral category (Section 7) and to let the funnel's thresholds be tuned
  later (Section 11, risk 2).
- **Suggestion** — a view over `ResearchRecord` entries where the final outcome is "pass"; drives the
  public dashboard's overview table and full-detail listing, including source references. The overview
  table's **verification-depth indicator** (Section 7) is computed on the fly from this record's
  per-criterion tier-of-origin data — not a separately stored field.
- **RejectedCandidate (added 2026-07-31)** — a view over `ResearchRecord` entries where the final
  outcome is "fail", at any tier; exposes only company identity, as-of date, and one neutral category
  (Section 7) derived from the record's per-criterion fail reason — deliberately narrower than
  `ResearchRecord` itself, which keeps the full tier/criterion/value detail for the operator and the
  rotation logic (Section 7 Knowledge Base).
- **PortfolioPosition** — unchanged from the 2026-07-21 design.
- **FundamentalAlert** — same concept as the original design, but now sourced from a fresh
  `ResearchRecord` on a held position rather than a snapshot diff against provider data.

## 10. Cost control

- One shared daily (and derived monthly) cap on **Stage 2** executions — the only tier with
  meaningful per-call cost — covering the automatic scheduler run and both manual trigger paths
  combined. Set to roughly 1–2 Stage-2 executions per day, chosen to keep total AI research spend in
  the low single-digit euros per month.
- Stage 0/1 have no hard cap (their cost is negligible) but their volume should still be logged, to
  support future tuning of the funnel's thresholds.
- The four Stage-2 cost measures in Section 7 (domain-restricted search, search-round ceiling,
  criteria-scoped prompt, explicit effort budget) are mandatory parts of this design, not optional
  polish — they were already identified as needed in the existing Company Research Agent spec's
  decision log but never implemented.

## 11. Open risks / deliberately accepted trade-offs

1. **Coverage speed is deliberately sacrificed for budget discipline.** At 1–2 Stage-2 executions per
   day, full rotation through a universe of thousands of Gettex-tradable companies takes a very long
   time. The user explicitly accepted this trade-off ("Tempo ist mir egal") rather than raising the
   budget or shrinking the universe.
2. **Unvalidated hit rate.** Whether random rotation across a huge universe regularly surfaces good
   candidates, without any pre-filter beyond Stage 0/1, is an accepted bet rather than a proven fact.
   The Knowledge Base (Section 9) is what will make the real hit rate measurable once the system runs.
3. **No live revaluation of past Suggestions.** A company shown once as a "pass" is not automatically
   re-checked when its price moves; it's only re-evaluated if the rotation happens to draw it again.
   The "Approach B" price-enrichment idea (Section 2, deferred) exists as the fallback if this proves
   too stale in practice.
4. **Stage 0 reliability.** A no-search LLM check can be wrong or stale, especially for lesser-known
   companies. Mitigated, not eliminated, by never auto-rejecting "uncertain" verdicts.
5. **Universe and sector-benchmark acquisition are still open implementation questions.** Both are
   "fetch a public list periodically" problems, not yet solved technically — deliberately left to the
   implementation plan rather than fixed in this design.
6. **Valuation comparison is now approximate**, using a cached sector-median lookup instead of a true
   live peer computation from the same screening run (as the original design had, for free, as a side
   effect of the paid screener). This is an accepted precision-for-cost trade-off.
7. **Interest coverage's actual search cost is unconfirmed (Section 6).** It was added to the Stage-2
   criteria set on financial merit alone; unlike the other Stage-2 criteria it isn't known to sit on a
   standard page, so it must be verified during implementation not to reintroduce the same
   unpredictable multi-round search cost this redesign exists to eliminate (Section 3, Risk 3). If it
   turns out expensive to obtain reliably, it should be dropped or downgraded to "best effort" rather
   than kept as a mandatory pass/fail criterion.
8. **The Section 10 cost target is still an estimate, not an empirical result (added 2026-07-31,
   feasibility review).** The only real Company Research Agent call to date landed at the worst-case
   end of the original cost analysis (~$1, and it errored) — the low/typical estimates the 1–2/day
   budget target relies on have never been confirmed with a real successful call against the *tightened*
   prompt. Externally confirmed during this review: the web-search tool's own per-search fee is trivial
   ($0.01/search), so the real cost driver is that search-result content is re-billed as input tokens on
   every subsequent conversation turn — cost compounds with search-round count rather than growing
   linearly with it. This makes the search-round ceiling (Section 7, cost measure 2) the single most
   load-bearing lever of the four, more so than domain restriction, and means the reconciliation
   sequence's step 4 live call (see Decision log above) is the actual test of whether the Section 10
   target is achievable at all, not just a smoke test of the plumbing.
9. **Stage 0's realistic filtering yield may be lower than the funnel table implies (added 2026-07-31,
   feasibility review).** Stage 0 must advance any "uncertain" verdict rather than reject it (Section 5),
   and a broad Gettex universe includes many companies an LLM's trained knowledge covers only thinly. In
   practice this likely means most candidates cascade to Stage 1 regardless of Stage 0, with Stage 0
   mainly catching clearly-bad, well-known companies for free. Not a design flaw — the "never reject
   uncertain for cost reasons" rule is correct — but the near-zero-cost tier should not be expected to do
   most of the funnel's actual filtering work once real volume is measured (Section 9, Knowledge Base).

## Decision log

- 2026-07-30: Replaced the paid market-wide screener + separate AI Assessor pair with a single
  AI-research-driven pipeline, after confirming in this session that the screener endpoint is
  paid-tier only and that per-candidate AI research cost is the other real cost driver (not just a
  theoretical risk).
- Considered and rejected building a conventional quantitative pre-filter (external fundamentals DB)
  ahead of AI research — no free/cheap source for it exists given the paywalled screener; a
  from-scratch numeric database was judged not worth the added complexity for this project's scale.
- Considered pure random rotation with no pre-filter at all (user's initial proposal, defensible given
  a fixed budget and a very large universe) — refined instead into the three-tier funnel (Stage 0/1)
  once it became clear a *free, AI-only* pre-filter was achievable without any external data source.
- Rejected true live industry-peer valuation research per candidate (too costly); adopted the
  three-signal valuation check (absolute floor + own history + cached sector benchmark table) instead,
  after confirming free sector-P/E benchmark sources exist (Siblis Research, GuruFocus, FullRatio,
  Basis Report).
- Decided the daily automatic run and both operator-only manual trigger paths (specific ticker;
  filtered random draw by sector/country) share one budget cap, so manual use cannot silently blow
  past the cost target.
- Explicitly deferred: live/cheap price-refresh enrichment of already-researched companies (until the
  real hit rate is known to need it).
- **2026-07-30, discovered after this design was drafted:** a parallel, uncommitted draft
  (`docs/superpowers/specs/superseded/2026-07-29-data-provider-client-design.md`) had researched
  and partially designed a genuinely free numeric-fundamentals path — SEC EDGAR bulk data for US
  fundamentals + a free-tier commercial provider used *only* for daily price, with KGV/KBV/market cap
  computed in-app, USA-only for now (Germany/Switzerland confirmed to have no free bulk source at
  all). This directly undercuts this design's founding premise that no free numeric source exists.
  Considered and explicitly not adopted: the user decided this document's AI-only approach stands
  as the current direction, specifically to keep one coherent design rather than merging two
  differently-shaped solutions to the same problem. The prior draft's per-country provider research
  (Section 4 of that document: SEC EDGAR, EDINET, Companies House/UK-SEF, `filings.xbrl.org`, EODHD/
  FMP/Finnhub/Twelve Data cost and ToS findings) remains valid, reusable research if a future session
  revisits combining a free quantitative pre-filter with this design's AI research funnel — it was not
  wrong, it simply arrived a day earlier than this session's own conclusion and the two were not
  reconciled before this design was finalized.
- **2026-07-30, product-strategy review:** evaluated three candidate gaps against the actual product
  goal (clarified in this same review, Section 1: a lead worth the user's own research time, not a
  self-validated final verdict) — realized-return outcome tracking, staleness re-checks on already-
  passed Suggestions, and per-criterion source confidence. Realized-return tracking was considered and
  explicitly **not adopted now**: it only earns its cost if the app itself has to be trusted as the
  decision-maker, which it deliberately isn't, so it's deferred to "later, if curious how the funnel
  performs" rather than a design blocker. A full periodic staleness re-check pipeline (reusing Stage 1,
  discussed and initially favored earlier in the same review) was also **not adopted**, in favor of
  the far cheaper fix of simply displaying each Suggestion's existing `ResearchRecord` date — since the
  user reviews every lead manually before acting, a visible age is enough, no new research cost or
  pipeline needed. **Adopted:** per-criterion tier-of-origin and as-of-date, surfaced on the dashboard
  (Section 7) from data the model already captures (Section 9) — this was judged the one gap that
  actually serves "is this worth my research time," at zero additional cost. Considered rejecting a
  live global price-data API (e.g. EODHD, per the superseded data-provider-client draft's own findings
  that no free option exists internationally) as the mechanism for any of this — correctly avoided,
  since none of the adopted fixes need one; reusing Stage 1's AI web search or plain existing
  timestamps was sufficient in both cases considered.
- **2026-07-30, product-strategy review, continued:** two further gaps identified, both adopted, both
  zero-cost. (1) The Universe Provider had silently dropped the market-cap floor the superseded
  data-provider-client draft had adopted for risk reasons — re-added, since an un-researchable
  micro-cap draw wastes the same scarce Stage-2 budget this whole redesign exists to protect. (2) The
  Company Research Agent's `valueTrapAssessment` gap (its spec's own decision log, 2026-07-26: prompt
  never sources an actual valuation multiple, fix explicitly deferred until a Data Provider Client /
  `FundamentalSnapshot` supplies one) was left pointing at a dependency that this redesign permanently
  removes (Section 4) — it would never have resolved. Fixed by wiring Stage 1's already-computed P/E/
  P/B (Section 6) into the Stage 2 prompt as context instead, added as cost/quality measure 5 in
  Section 7.
- **2026-07-31, product-strategy review:** examined which specific values Stage 1 and Stage 2 should
  each search for, given the fixed constraints (Stage 1 = one bounded search hit, Stage 2 = the scarce
  1–2/day budget). Found that margin trend, FCF growth, and multi-year profit stability — all
  Stage-2-only criteria — each have a *current single-year value* that's typically on the same
  key-statistics page Stage 1 already visits, at no extra cost. Adopted: Stage 1 now also captures these
  current-year values as an early reject filter (Section 6), so candidates already obviously bad this
  year don't consume Stage-2 budget just to have that confirmed the expensive way. Stage 2 keeps sole
  responsibility for confirming the actual multi-year trend/stability — this change only adds an earlier
  rejection path, it does not shift any trend-confirmation work out of Stage 2.
- **2026-07-31, continued:** reviewed the full criteria set from first principles (quality, moat,
  safety, valuation) against Section 6, to check nothing substantively important was missing from a
  value-investing standpoint. Found one genuine gap: **interest coverage (EBIT / interest expense)** —
  debt/equity alone shows balance-sheet structure but not whether current earnings comfortably service
  the debt actually owed. Adopted as a Stage-2 criterion, but explicitly flagged as **cost-unconfirmed**
  (Section 6, Section 11 risk 7) — unlike the other Stage-2 criteria, it isn't known to sit on a
  standard finance page, and this redesign exists specifically to avoid reintroducing the kind of
  unpredictable multi-round search cost that motivated it in the first place (Section 3, Risk 3); its
  actual cost must be checked during implementation, not assumed. Other candidate additions (buyback/
  dilution trend, revenue growth, FCF yield as its own metric) were considered and explicitly **not
  adopted** — judged as nice-to-have signals that would pad the criteria list without changing the core
  "cheap, good, safe company" verdict, in line with keeping the list short and disciplined rather than
  redundant.
- **2026-07-31, continued:** reviewed what should be stored per research outcome vs. what the user
  should actually see. Adopted: `ResearchRecord` now also persists a **source reference** per
  criterion (the Company Research Agent already produces one per its own guardrails, it was just never
  kept), the **value-trap-assessment/low-confidence-flag** text (also already produced, also not
  previously persisted), and **which specific criterion caused a fail**, not just the final verdict —
  all at zero extra research cost, purely a matter of not discarding data already generated. On the
  dashboard: Suggestions (passes) now also surface the source reference per criterion, directly serving
  the "start your own research where the AI's left off" goal (Section 1). Also adopted, after asking the
  user directly: a **public Rejected-candidates list**, not just an operator-only view as first proposed
  — the user considers it publicly useful. To keep it safe given the existing descriptive-not-imperative
  wording policy (`2026-07-21-value-screener-design.md`, Section 9), the user explicitly asked that it
  **not** expose which internal tier/criterion caused a rejection (Section 7) — only company identity,
  date, and one neutral category from a small fixed set. The full technical detail stays in
  `ResearchRecord`/Knowledge Base, unrestricted, for the operator and rotation logic.
- **2026-07-31, continued:** asked whether anything beyond the standard ratios was worth capturing.
  Adopted **management quality / capital allocation** (buyback-vs-dilution history, M&A discipline) as
  a new Stage-2 qualitative criterion, folded into the same research pass as the moat/business-model
  write-up — Buffett weighs this nearly as heavily as the moat itself, and it was entirely absent from
  the criteria set until now. Supported by **insider/founder ownership share** as a cheap Stage-1
  snapshot value (same key-statistics page as P/E/P/B), used as a proxy for management having its own
  capital at stake. Considered and explicitly **not adopted** as separate formal criteria: customer
  concentration, regulatory/legal red flags, recent negative news — judged to already surface naturally
  within the existing qualitative write-up and `valueTrapAssessment` guardrail when material, so
  formalizing them would pad the criteria list without adding real signal.
- **2026-07-31, continued:** designed how results are actually presented, from the standpoint of "can
  the user efficiently scan many candidates and get analysis ideas" (Section 1). Adopted a
  **screener-overview + drill-down** dashboard structure (Section 7) over a flat list or feed: a
  sortable/filterable overview table (valuation delta vs. own history/sector benchmark, a new
  **verification-depth indicator** derived on the fly from existing per-criterion tier data, no new
  storage) plus a detail page per Suggestion with full per-criterion breakdown and source references.
  On the Rejected-candidates list, adopted highlighting a **"valuation-only" sub-group** — candidates
  that failed *only* on valuation are fundamentally sound and just priced above their historical/sector
  norm right now, effectively a free watchlist, surfaced separately from fundamentally weak rejects.
  Also resolved an open modeling gap: **sector grouping had no defined taxonomy.** Adopted the 11
  top-level GICS-style sector names (not the full licensed GICS structure, just the well-known top-level
  labels) as the fixed scheme for `UniverseEntry.sector`, the `SectorBenchmark` table, and dashboard
  filtering — chosen because it's what most finance pages already report (so the researching LLM
  normalizes into it during search it's already doing, no separate classification step) and matches the
  granularity the already-found free sector-P/E sources report at. A finer GICS industry-group split
  (~24) was considered and rejected — at 1–2 Stage-2 executions/day, candidates would accumulate too
  slowly per group to make a finer split useful.
- **2026-07-31, continued:** asked for further product ideas; two were proposed and both refined by
  the user before adoption. (1) **Coverage Map** — proposed as a passive transparency view; the user
  asked to also actively keep sectors balanced, not just display the imbalance. Adopted: the automatic
  daily draw's weighting (Selection Logic, Section 7) now factors in sector under-representation from
  the Knowledge Base directly, not just recency; the Coverage Map is the visible readout of that same
  signal, not a separate calculation. (2) **Targeted cheap re-check for "valuation-only" watchlist
  entries** — proposed as a periodic/automatic re-check; the user asked for it to be an
  operator-triggered manual action instead. Adopted as a fourth trigger mode, **Watchlist Re-check**
  (Section 7): operator picks a watchlist entry, re-runs Stage 1 only (never Stage 2, never touches the
  shared budget cap), logged as a new `ResearchRecord` entry per the existing append-only pattern; a
  full Stage 2 re-run remains a separate, deliberate decision via the existing specific-ticker trigger,
  not an automatic cascade.
- **2026-07-31, code-vs-spec reconciliation session:** with a written design now stable and an
  unfinished, already-started codebase underneath it (Phase 1 portfolio foundation + Company Research
  Agent Tasks 1–7), explicitly compared the two to make sure resuming implementation doesn't run in
  the wrong direction or leave anything half-reconciled. Findings: Phase 1 (`backend/`, portfolio CRUD
  + auth) is fully unaffected — `PortfolioPosition` is unchanged per Section 9, no rework needed. The
  Company Research Agent's infrastructure (Spring AI wiring, MCP tool exposure, timeout handling,
  citation cross-check guardrail, prompt-injection resistance, usage logging) remains reusable — it was
  never coupled to the now-removed Data Provider Client. Its prompt (`ResearchPromptBuilder`) and
  output model (`CompanyResearchResult`), however, still implement only the narrow original
  2026-07-24 scope — none of this design's Section 6 criteria (P/E, P/B, ROE, D/E, current ratio,
  margin trend, FCF, interest coverage, moat, management quality, insider ownership) are represented.
  Everything else in this redesign (Stage 0/1, Universe Provider, Selection Logic, Knowledge Base,
  Dashboard, Sector Benchmark Cache, Scheduler) has no code yet, so carries no reconciliation risk.

  **Agreed reconciliation sequence, to execute before writing the full implementation plan:**
  1. ~~Resolve the still-open Stage 1 mechanism decision~~ **Done, 2026-07-31** — see the "Stage 1
     mechanism, decided 2026-07-31" note in Section 7 above (two MCP tools/output records sharing one
     underlying agent mechanism, not a mode flag; not a separate search path either).
  2. ~~Update the Company Research Agent's own design spec~~ **Done, 2026-07-31** — its Stage 2
     output contract is now the full Section 6 criteria set, its scope covers the new
     `quick_research_company` tool (Stage 1), and the four cost measures plus cost/quality measure 5
     are written in as mandatory (see that spec's Section 5.4). Along the way, two genuine
     contradictions were found between the old agent spec and this redesign (automatic triggering had
     been explicitly excluded there; capital allocation had been explicitly "not adopted") and
     resolved by reversing both — see that spec's Decision log, 2026-07-31 entry, for detail.
  3. Rewrite `ResearchPromptBuilder` and `CompanyResearchResult` (plus their tests) against that
     updated spec, and add the new Stage-1 prompt builder + output record for
     `quick_research_company` — this supersedes Tasks 3/4 of the agent's plan, rather than extending
     them.
  4. Only then attempt the first real end-to-end live call, against the tightened, properly-scoped
     version. Rationale for this order over testing the current prompt first: the current prompt has
     no search-round cap or domain restriction — it is the same shape that already produced the
     ~$1/error outcome (Section 3) — so validating it live now would likely repeat that known failure
     mode without learning anything about the actual target architecture, whereas rewriting first
     means the first live test is meaningful and tests the thing that will actually ship.
     **Explicit checkpoint (added 2026-07-31, feasibility review):** this call is also the real test
     of the cost target in Section 10 (1–2 Stage-2 executions/day, low single-digit euros/month), which
     has never been empirically confirmed — see the feasibility review Decision log entry below. If the
     real per-call cost lands materially above roughly $0.05–0.10, the daily frequency and/or the
     search-round ceiling need to be revisited before step 5, not silently assumed to average out.
  5. Write the implementation plan for the remaining, entirely greenfield redesign components
     (Universe Provider, Selection Logic, Knowledge Base, Dashboard, Scheduler, Stage 0/1 wiring) only
     after step 4 succeeds.
- **2026-07-31, feasibility review session:** asked directly whether this plan is realistically
  implementable, separate from whether it's internally coherent (the reconciliation session above).
  Verified rather than just asserted several load-bearing assumptions:
  - **Confirmed low-risk:** the four Stage-2 cost measures (Section 7) are not just a plan — their APIs
    (`allowedDomains(...)`, `.effort(...)`, `.thinkingAdaptive()`/`.thinkingDisabled()` on
    `AnthropicChatOptions`/`AnthropicWebSearchTool`) were already confirmed present via `javap` in the
    Company Research Agent spec's own decision log, just unused. Same for `maxUses(...)`, already live
    in production code — meaning Stage 1 Option A (Section 7's paused mechanism question) is a trivial
    config/prompt change on the existing builder, reinforcing it as the right call once made.
  - **Confirmed low-risk:** the Sector Benchmark Cache's free sources were already researched and
    confirmed in an earlier session (Siblis Research, GuruFocus, FullRatio, Basis Report) — no new risk
    found here.
  - **De-risked:** the Universe Provider's data source, previously an open implementation question
    (Section 2), now has a concrete, plausible free candidate — Börse München publishes a free delayed
    pre-/post-trade data file feed for its Regulated Market (MUNA) and Free Trading (MUNB, i.e. gettex)
    segments, required under EU market-transparency rules. Exact format/parsing remains implementation
    work, but the premise that a free source exists is now evidenced, not just assumed.
  - **New risk found:** confirmed via external research that Anthropic's hosted web-search tool charges
    a flat $0.01/search (trivial on its own — 5 searches = $0.05), but that search-result content is
    **re-billed as input tokens on every subsequent conversation turn**, so real cost compounds with
    search-round count rather than scaling linearly with it. This reframes cost measure 2
    (search-round ceiling) as the single most load-bearing of the four cost measures, ahead of domain
    restriction — worth prioritizing first if the Task 3/4 rewrite (reconciliation step 3) is staged.
    Written into Section 11, risk 8.
  - **New risk found:** the Section 10 cost target (1–2 Stage-2 executions/day, low single-digit
    euros/month) has never been empirically validated — the only real call to date hit the worst-case
    end of the original estimate (~$1) and errored. The reconciliation sequence's step 4 live call is
    therefore not just an infrastructure smoke test but the actual test of this target; an explicit
    checkpoint (~$0.05–0.10/call) was added to that step above and to Section 11, risk 8.
  - **New risk found:** Stage 0's realistic filtering yield is likely lower in practice than the funnel
    table (Section 5) implies, since "uncertain" verdicts must always advance and a broad Gettex
    universe includes many companies an LLM knows only thinly. Not a flaw in the rule itself — recorded
    as Section 11, risk 9, to set expectations once real volume is measured.
  - **New gap found:** the daily Scheduler (`backend/`, App Runner) invoking the Company Research Agent
    (a separately deployed Lambda, per its own spec) requires an MCP client over the network — implicit
    in the architecture but never explicitly named as a component to build. Added to the Scheduler
    bullet in Section 7 and flagged as an implementation-plan item (reconciliation step 5).
  - **Scope acknowledged, not a blocker:** the remaining greenfield surface (Universe Provider,
    Selection Logic, Knowledge Base, Sector Benchmark Cache, Scheduler, budget-cap enforcement across
    three trigger paths, and a multi-view Dashboard) is substantial for a solo side project. Nothing in
    it is individually hard, and the user has already explicitly accepted slow progress ("Tempo ist mir
    egal," Section 11 risk 1) — noted here only so the total build size is explicit, not a surprise.
  - **Overall verdict:** no finding here invalidates the design's approach; the reconciliation sequence
    already agreed (previous entry) remains correct, with its step 4 now carrying explicit checkpoint
    criteria rather than being an open-ended "try it and see."
- **2026-07-31, Stage 1 mechanism decided (reconciliation step 1, closes the item paused since
  2026-07-30):** confirmed Option A over Option B — Stage 1 reuses the Company Research Agent's
  underlying mechanism (Claude + hosted `web_search`, same guardrails) rather than a separate, simpler
  search path; Option B was rejected again for the same reasons as when first raised (reopens the
  "which fixed provider" question, duplicates infrastructure). Refined further, beyond what was
  tentatively noted on 2026-07-30: rather than one `research_company` tool taking a mode flag and
  returning one result type with mode-dependent empty fields, adopted **two separate MCP tools with two
  separate output records** — `research_company` (Stage 2) and `quick_research_company` (Stage 1) —
  sharing the same internal agent call machinery (web-search options, citation cross-check, timeout,
  usage logging) but with independently typed, independently scoped prompts and results. Rationale:
  Selection Logic decides which stage to run as deterministic backend code, not an LLM choosing between
  tool descriptions, so a single "self-describing" tool buys nothing here, while two clean types map
  directly onto `ResearchRecord`'s (Section 9) already-different Stage-1 vs. Stage-2 field sets. Written
  into Section 7 (Company Research Agent bullet) and reconciliation step 1/2 above. Unblocks
  reconciliation step 2 (updating the Company Research Agent's own design spec).
- **2026-07-31, reconciliation step 2 done (Company Research Agent spec rewrite):** rewrote
  `2026-07-24-company-research-agent-design.md`'s scope, interface, output contract (Sections 5.1/5.2),
  triggering (Section 6), and cost sections to match this redesign, per the sequence agreed above.
  Beyond the already-known criteria-catalogue gap, found and resolved **two genuine contradictions**
  between the two documents that reconciliation was specifically meant to catch:
  - The old agent spec's Section 2 explicitly excluded scheduler-driven automatic triggering ("stays
    deliberately manual/cost-incurring") — decided when the agent was envisioned as a rare,
    quarterly, button-only feature. This redesign's automatic daily Scheduler run is now the funnel's
    core mechanism, not an edge case. Resolved by reversing the exclusion in the agent spec.
  - The old agent spec's Section 2 listed capital allocation as "considered, not adopted for now."
    This redesign's 2026-07-31 product-strategy review adopted management quality/capital allocation
    as a Stage 2 criterion. Resolved by reversing the exclusion there too.

  Also resolved, after asking the user directly: **Guardrail B** (the agent spec's fact-check
  guardrail) referenced the now-removed Data Provider Client's `FundamentalSnapshot` — a dependency
  that no longer exists anywhere in either design. Offered three options (redefine as a Stage-1-vs-
  Stage-2 consistency check; drop it; user's own alternative); the user chose the redefinition. Stage
  2 now receives Stage 1's snapshot values as prompt context (cost/quality measure 5, unchanged from
  the 2026-07-30 decision), and Guardrail B flags material divergence between what Stage 2 was given
  and what it found — still a simple rule-based check in the main application, not a new AI call,
  preserving the guardrail's original architectural principle.

  This closes reconciliation step 2. **Not done in this session:** step 3 (rewriting
  `ResearchPromptBuilder`/`CompanyResearchResult`, plus a new Stage 1 prompt builder/output record, in
  actual code) — the agent spec now describes a materially different contract than the currently
  committed code implements; `CURRENT_PROMPT_VERSION` still reads `"research-v1"`. Step 3 is next.
