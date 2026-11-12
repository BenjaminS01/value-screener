# Value Screener

[![CI](https://github.com/BenjaminS01/value-screener/actions/workflows/ci.yml/badge.svg)](https://github.com/BenjaminS01/value-screener/actions/workflows/ci.yml)

## In Kürze

Value Screener ist ein **Analysewerkzeug für Value Investing**, ein Ansatz im Stil von Buffett. Die
Anwendung zeigt zu einem Unternehmen geprüfte Kennzahlen und Einschätzungen mit Quelle. Sie liest
seine Quartalsberichte und beantwortet Fragen dazu mit Seitenangabe. Daneben führt sie das eigene
Portfolio.

Sie **handelt nicht und empfiehlt nichts**: Sie beschreibt, die Entscheidung trifft der Mensch.

Das Projekt hat zwei Zwecke:

1. **Vorzeigeprojekt:** Java, AWS, KI/RAG und sicherheitsbewusstes Design, für Bewerbungen und
   Freelancing, zugleich Praxisprojekt für die AWS-Developer-Zertifizierung.
2. **Persönliches Werkzeug** des Betreibers für die eigene Aktienanalyse.

Die laufenden Kosten sollen bei etwa 20 €/Monat bleiben. Die ganze Anwendung ist **nur nach Login**
nutzbar.

> **Stand:** Dieses Dokument beschreibt das Zielbild **v2** (Neuplanung vom 2026-11-03). Was davon
> schon gebaut ist, steht unter [Umsetzungsstand](#umsetzungsstand). Maßgebliche Spec:
> [`docs/superpowers/specs/2026-11-03-app-scope-v2-design.md`](docs/superpowers/specs/2026-11-03-app-scope-v2-design.md).

---

## Die Seiten der Anwendung

Die Oberfläche ist englisch, Impressum und Datenschutzerklärung sind deutsch. Die Skizzen zeigen das
Zielbild, die Beispieldaten sind erfunden.

### Login

Ohne Login sind nur diese Seite und die Rechtstexte erreichbar. Konten werden nicht selbst
registriert, sondern vom Betreiber vergeben.

```text
╔══════════════════════════════════════════════════════════════╗
║ ●  ●  ●    https://value-screener.app/login                  ║
╠══════════════════════════════════════════════════════════════╣
║                                                              ║
║         ╭──────────────────────────────────────────╮         ║
║         │             VALUE SCREENER               │         ║
║         │        Value investing research          │         ║
║         │                                          │         ║
║         │ Username                                 │         ║
║         │ ┌────────────────────────────────────┐   │         ║
║         │ │ alice                              │   │         ║
║         │ └────────────────────────────────────┘   │         ║
║         │ Password                                 │         ║
║         │ ┌────────────────────────────────────┐   │         ║
║         │ │ ••••••••••••••••••                 │   │         ║
║         │ └────────────────────────────────────┘   │         ║
║         │                                          │         ║
║         │ ┌────────────────────────────────────┐   │         ║
║         │ │               Log in               │   │         ║
║         │ └────────────────────────────────────┘   │         ║
║         │                                          │         ║
║         │ ! Invalid username or password           │         ║
║         ╰──────────────────────────────────────────╯         ║
║                                                              ║
║           Impressum  ·  Datenschutz  ·  Disclaimer           ║
║                                                              ║
╚══════════════════════════════════════════════════════════════╝
```

### Startseite

Nach dem Login landet man auf einer Startseite mit der Navigationsleiste. Vorerst steht dort nur der
Titel, der Inhalt folgt später.

```text
╔══════════════════════════════════════════════════════════════════════════════════════════════╗
║ ●  ●  ●    https://value-screener.app/                                                       ║
╠══════════════════════════════════════════════════════════════════════════════════════════════╣
║  ▣ VALUE SCREENER     Portfolio     Research                      benjamin (ADMIN) · Logout  ║
╟──────────────────────────────────────────────────────────────────────────────────────────────╢
║                                                                                              ║
║                                                                                              ║
║                                                                                              ║
║                                                                                              ║
║                               U N S E R E   A N W E N D U N G                                ║
║                                                                                              ║
║                                          ──────────                                          ║
║                                                                                              ║
║                                                                                              ║
║                                                                                              ║
║                                                                                              ║
║                                                                                              ║
║                                                                                              ║
║                                                                                              ║
║  ──────────────────────────────────────────────────────────────────────────────────────────  ║
║                  Disclaimer: no investment advice · Impressum · Datenschutz                  ║
╚══════════════════════════════════════════════════════════════════════════════════════════════╝
```

### Portfolio

Die Positionen des Betreibers, nach Gruppen geordnet. Ein Klick auf eine Position öffnet die Aktie im
Research. So sieht es der **ADMIN**:

```text
╔══════════════════════════════════════════════════════════════════════════════════════════════╗
║ ●  ●  ●    https://value-screener.app/portfolio                                              ║
╠══════════════════════════════════════════════════════════════════════════════════════════════╣
║  ▣ VALUE SCREENER     Portfolio     Research                      benjamin (ADMIN) · Logout  ║
╟──────────────────────────────────────────────────────────────────────────────────────────────╢
║                                                                                              ║
║  Portfolio                                                             [ + Buy position ]    ║
║                                                                                              ║
║  ╭─ Semiconductors · 2 ───────────────────────────────────────────────────────────────────╮  ║
║  │ Company            Ticker     Qty    Ø Entry     Since          Actions                │  ║
║  │ NVIDIA Corp.       NVDA       20     412.50      2025-03-14    Sell  Edit  ✕           │  ║
║  │ ASML Holding       ASML       8      640.00      2024-11-02    Sell  Edit  ✕           │  ║
║  ╰────────────────────────────────────────────────────────────────────────────────────────╯  ║
║  ╭─ Internet Platforms · 1 ───────────────────────────────────────────────────────────────╮  ║
║  │ Company            Ticker     Qty    Ø Entry     Since          Actions                │  ║
║  │ Alphabet Inc.      GOOGL      35     138.20      2024-06-21    Sell  Edit  ✕           │  ║
║  ╰────────────────────────────────────────────────────────────────────────────────────────╯  ║
║  ╭─ Ungrouped · 1 ────────────────────────────────────────────────────────────────────────╮  ║
║  │ Company            Ticker     Qty    Ø Entry     Since          Actions                │  ║
║  │ Allianz SE         ALV        12     268.40      2025-09-30    Sell  Edit  ✕           │  ║
║  ╰────────────────────────────────────────────────────────────────────────────────────────╯  ║
║                                                                                              ║
║  ──────────────────────────────────────────────────────────────────────────────────────────  ║
║                  Disclaimer: no investment advice · Impressum · Datenschutz                  ║
╚══════════════════════════════════════════════════════════════════════════════════════════════╝
```

Ein **USER** sieht dieselbe Liste ohne Stückzahl, Preis, Datum und Buttons:

```text
╭─ Portfolio as USER ──────────────────────────────────────╮
│ Company               Ticker    Group                    │
│ NVIDIA Corp.          NVDA      Semiconductors           │
│ ASML Holding          ASML      Semiconductors           │
│ Alphabet Inc.         GOOGL     Internet Platforms       │
│                                                          │
│ no quantity, no price, no date, no buttons               │
╰──────────────────────────────────────────────────────────╯
```

Beim Kauf („+ Buy position“) kann der ADMIN den Kaufgrund gleich mit festhalten. Er landet in der
Investment-These der Aktie:

```text
╭─ Buy position ───────────────────────────────────────────────────────────╮
│ ISIN                      Ticker          Company                        │
│ ┌──────────────────────┐  ┌────────────┐  ┌────────────────────────────┐ │
│ │ DE0008404005         │  │ ALV        │  │ Allianz SE                 │ │
│ └──────────────────────┘  └────────────┘  └────────────────────────────┘ │
│ Quantity          Entry price       Purchase date                        │
│ ┌──────────────┐  ┌──────────────┐  ┌──────────────────┐                 │
│ │ 12           │  │ 268.40       │  │ 2026-11-04       │                 │
│ └──────────────┘  └──────────────┘  └──────────────────┘                 │
│                                                                          │
│ Why are you buying?  (optional, saved to the investment thesis)          │
│ ┌──────────────────────────────────────────────────────────────────────┐ │
│ │ Market leader in insurance; dividend raised for 10 years in a row.   │ │
│ │                                                                      │ │
│ │                                                                      │ │
│ └──────────────────────────────────────────────────────────────────────┘ │
│                                                                          │
│                                                 [ Cancel ]  [ Buy ]      │
╰──────────────────────────────────────────────────────────────────────────╯
```

### Research

Eine Seite pro Aktie, hier aus Sicht des ADMIN. Ein USER sieht dieselbe Seite, **auch** mit Investment-These
und Abschnitt „Thesis check“ (mit dem Hinweis „Persönliche Einschätzung des Betreibers, keine Anlageempfehlung.“).
Die These bearbeiten, die Gruppe ändern und Berichte hochladen kann er nicht, der Upload-Button ist für ihn ausgegraut.

```text
╔══════════════════════════════════════════════════════════════════════════════════════════════╗
║ ●  ●  ●    https://value-screener.app/research/US02079K3059                                  ║
╠══════════════════════════════════════════════════════════════════════════════════════════════╣
║  ▣ VALUE SCREENER     Portfolio     Research                      benjamin (ADMIN) · Logout  ║
╟──────────────────────────────────────────────────────────────────────────────────────────────╢
║                                                                                              ║
║  ┌────────────────────┐  ┌───────────────────────────────┐  ┌──────────────────────────────┐ ║
║  │ Group: All        ▾│  │ Alphabet Inc. (GOOGL)        ▾│  │  ◆ Owner holds this position │ ║
║  └────────────────────┘  └───────────────────────────────┘  └──────────────────────────────┘ ║
║                                                                                              ║
║  ╭─ Overview ─────────────────────────────────────────────────────────────────────────────╮  ║
║  │ Alphabet Inc. · GOOGL · US02079K3059 · USA     Group: Internet Platforms  [ change ▾ ] │  ║
║  │ Search, YouTube, Android and Google Cloud; revenue mainly from advertising.            │  ║
║  │ Last researched 2026-10-31 · Status: Holding (from portfolio)                          │  ║
║  ╰────────────────────────────────────────────────────────────────────────────────────────╯  ║
║  ╭─ Investment thesis · edit: ADMIN ──────────────────────────────────────────────────────╮  ║
║  │ Status        Holding  (from portfolio)                                                │  ║
║  │ Why           Dominant search/ads business funds Cloud and AI; strong net cash.        │  ║
║  │ Expected      Cloud margin keeps rising; buybacks continue at today's pace.            │  ║
║  │ Sell if       Search share falls clearly, or capex eats free cash flow for 2+ years.   │  ║
║  │               updated 2026-09-12                                       [ Edit thesis ] │  ║
║  ╰────────────────────────────────────────────────────────────────────────────────────────╯  ║
║  ╭─ Fundamentals ─────────────────────────────────────────────────────────────────────────╮  ║
║  │ ┌──────────────────┐ ┌──────────────────┐ ┌──────────────────┐ ┌──────────────────┐    │  ║
║  │ │ P/E              │ │ P/B              │ │ 5y Ø P/E         │ │ ROE              │    │  ║
║  │ │ 24.1             │ │ 6.8              │ │ 26.3             │ │ 31.2 %           │    │  ║
║  │ │ 2026-10-31 · src │ │ 2026-10-31 · src │ │ ! old: 2026-03-02│ │ FY2025 · src     │    │  ║
║  │ └──────────────────┘ └──────────────────┘ └──────────────────┘ └──────────────────┘    │  ║
║  │ + 5y Ø P/B · debt/equity · current ratio · net margin · FCF positive · …               │  ║
║  │ ▾ P/E history:  2026-10-31  24.1   ·   2026-03-02  27.8   ·   2025-09-15  22.4         │  ║
║  ╰────────────────────────────────────────────────────────────────────────────────────────╯  ║
║  ╭─ Qualitative findings ─────────────────────────────────────────────────────────────────╮  ║
║  │ Moat            Network effects in search and ads.         src  ▸ history              │  ║
║  │ Management      Steady buybacks, little dilution.          src  ▸ history              │  ║
║  │ Value trap      not researched                                                         │  ║
║  ╰────────────────────────────────────────────────────────────────────────────────────────╯  ║
║  ╭─ Quarterly reports ────────────────────────────────────────────────────────────────────╮  ║
║  │ Period    File                    Uploaded     Status    Pages                         │  ║
║  │ 2026-Q3   GOOG-10-Q-Q3-2026.pdf   2026-11-10   Indexed   58      [ Open ]              │  ║
║  │ 2026-Q1   GOOG-10-Q-Q1-2026.pdf   2026-11-04   Indexed   54      [ Open ]              │  ║
║  │                                                                                        │  ║
║  │ [ Upload report ]  ADMIN only — greyed out for USERs                                   │  ║
║  ╰────────────────────────────────────────────────────────────────────────────────────────╯  ║
║  ╭─ AI chat ──────────────────────────────────────────────────────────────────────────────╮  ║
║  │ [ Evaluate quarterly reports ]                              Questions left today: 7/10 │  ║
║  │ ╭─ Evaluation · 2026-11-10 · reports Q1 + Q3 2026 ───────────────────────────────────╮ │  ║
║  │ │ Margins stable to rising, free cash flow clearly positive, low debt …              │ │  ║
║  │ │ Thesis check (all roles):  Cloud margin assumption supported [Q3 2026, p. 31];     │ │  ║
║  │ │   capex rising faster than expected [Q3 2026, p. 44].                              │ │  ║
║  │ │ No new quarterly reports — evaluation already available.                           │ │  ║
║  │ ╰────────────────────────────────────────────────────────────────────────────────────╯ │  ║
║  │                                   you: Wie hat sich die operative Marge entwickelt?    │  ║
║  │ AI:  Die operative Marge stieg von 31 % auf 33 % [Report Q3 2026, p. 12].              │  ║
║  │ ┌────────────────────────────────────────────────────────────────────────┐  ┌────────┐ │  ║
║  │ │ Ask a question about Alphabet …                                        │  │ Send   │ │  ║
║  │ └────────────────────────────────────────────────────────────────────────┘  └────────┘ │  ║
║  │ [ New conversation ]   Please don't enter personal data.                               │  ║
║  ╰────────────────────────────────────────────────────────────────────────────────────────╯  ║
║  ╭─ Deep analysis ────────────────────────────────────────────────────────────────────────╮  ║
║  │ [ Run deep analysis ]   → "Lambda deep-analysis-demo invoked at 2026-11-12T14:05:12Z"  │  ║
║  ╰────────────────────────────────────────────────────────────────────────────────────────╯  ║
║                                                                                              ║
║  ──────────────────────────────────────────────────────────────────────────────────────────  ║
║                  Disclaimer: no investment advice · Impressum · Datenschutz                  ║
╚══════════════════════════════════════════════════════════════════════════════════════════════╝
```

Über „Edit thesis“ öffnet sich das Formular für die Investment-These. Es funktioniert für jede Aktie,
auch für eine, die nicht im Portfolio ist. „Holding“ setzt das System selbst, solange die Aktie im
Portfolio ist. Sonst wählt der ADMIN `Watching` oder `Rejected` mit Begründung:

```text
╭─ Edit investment thesis · Intel Corp. · ADMIN ───────────────────────────╮
│ Status                                                                   │
│ ( ) Holding   - set automatically while the stock is in the portfolio    │
│ (•) Watching   ( ) Rejected                                              │
│ Reason for status                                                        │
│ ┌──────────────────────────────────────────────────────────────────────┐ │
│ │ Wait for two quarters of foundry margin data.                        │ │
│ └──────────────────────────────────────────────────────────────────────┘ │
│ Why interesting / why bought                                             │
│ ┌──────────────────────────────────────────────────────────────────────┐ │
│ │ Low P/B versus own 5y average; large asset base.                     │ │
│ └──────────────────────────────────────────────────────────────────────┘ │
│ Expected development                                                     │
│ ┌──────────────────────────────────────────────────────────────────────┐ │
│ │ Foundry losses shrink from 2027 on.                                  │ │
│ └──────────────────────────────────────────────────────────────────────┘ │
│ Sell / drop if                                                           │
│ ┌──────────────────────────────────────────────────────────────────────┐ │
│ │ Dividend cut followed by new share dilution.                         │ │
│ └──────────────────────────────────────────────────────────────────────┘ │
│                                                                          │
│ last updated 2026-10-05                            [ Cancel ]  [ Save ]  │
╰──────────────────────────────────────────────────────────────────────────╯
```

---

## Was die Anwendung fachlich kann

### Portfolio

Hier stehen die tatsächlichen Positionen des Betreibers, nach Gruppen geordnet.

- **Der ADMIN** sieht Stückzahl, durchschnittlichen Einstandspreis und Kaufdatum. Er kann kaufen
  oder aufstocken, teilweise oder ganz verkaufen, eine Position korrigieren und Fehleinträge löschen.
- **Ein USER** sieht nur, *welche* Aktien gehalten werden: Firma, Ticker und Gruppe. Stückzahl,
  Preis und Datum werden nie an den Browser geschickt.

### Research

Hier lassen sich alle recherchierten Unternehmen auswählen, egal ob sie im Portfolio sind oder nicht.
Die Auswahl lässt sich nach Gruppe filtern.

- **Überblick:** Name, Ticker, ISIN, Land, Gruppe und Geschäftsmodell.
- **Kennzahlen:**
  - Bewertung: KGV und KBV, jeweils aktuell und im eigenen 5-Jahres-Schnitt.
  - Profitabilität und Wachstum: Eigenkapitalrendite, Nettomarge, ob der Free Cashflow positiv ist,
    ob der Gewinn gewachsen ist.
  - Finanzierung: Verschuldungsgrad und Liquidität (Current Ratio).
  - Insider-Anteil.
- **Qualitative Einschätzungen:** Entwicklung von Margen und Free Cashflow, Stabilität der Gewinne,
  Zinsdeckung, Burggraben (Moat), Qualität des Managements und das Risiko einer Value Trap. Damit ist
  eine Aktie gemeint, die nur billig aussieht, weil das Geschäft Probleme hat.
- Jede Angabe hat **Quelle und Datum**. Fehlendes wird als „not researched“ angezeigt, damit Lücken
  sichtbar bleiben.
- **Alter der Daten:** Angaben älter als 6 Monate werden als veraltet markiert. Pro Aktie steht
  „Last researched …“, damit niemand mit alten Zahlen entscheidet, die aktuell aussehen.
- **Verlauf:** Neue Recherchen überschreiben alte Werte nicht spurlos. Ältere Stände sind
  aufklappbar, z. B. wie sich das KGV oder die Einschätzung des Burggrabens verändert hat.
- **Interessenkonflikt:** Hält der Betreiber die Aktie, wird das deutlich markiert
  („Owner holds this position“).

### Investment-These (lesen: alle, bearbeiten: nur ADMIN)

Value Investing heißt auch, vorher aufzuschreiben, *warum* man eine Aktie hält und *was* einen zum
Verkauf bringen würde. Der ADMIN hält pro Aktie fest:

- den Kaufgrund,
- die erwartete Entwicklung,
- die Verkaufsgründe,
- einen Status `Watching` oder `Rejected` mit Begründung.

„Holding“ ergibt sich automatisch aus dem Portfolio. Bei der Auswertung neuer Quartalsberichte
prüft ein Abschnitt „Thesis check“, welche Aussagen die Annahmen stützen oder ihnen widersprechen,
mit Seitenangabe. These, Status und Thesis check dürfen **alle angemeldeten Nutzer lesen**, bearbeiten
kann sie **nur der ADMIN**. Die These trägt immer den Hinweis „Persönliche Einschätzung des Betreibers,
keine Anlageempfehlung.“

### Gruppen

Gruppen sind eine gepflegte Liste von 35 Segmenten, z. B. Banks, Insurance, Oil & Gas, Renewable
Energy, Semiconductors, Software, Cybersecurity, Pharma oder Internet Platforms. Jede Aktie gehört
genau einer Gruppe an.

Die KI schlägt die Gruppe bei der Recherche vor, der ADMIN kann sie ändern. Eine Gruppe, die der
ADMIN gesetzt hat, wird von späteren Recherchen nicht überschrieben.

### Woher die Recherchedaten kommen

1. Der Betreiber lässt ein Unternehmen **lokal** von Claude Code recherchieren. Das läuft in einer
   abgeschotteten Sandbox mit dem Skill `research-company`. Die KI füllt 18 feste Kriterien mit
   Quellen und schlägt eine Gruppe vor.
2. Der Betreiber **prüft** das Ergebnis, bevor es gespeichert wird.
3. Er **überträgt** geprüfte Daten selbst ins Live-System, mit einem Skript auf seinem Rechner. Die
   Webrecherche läuft nie in AWS.

### Quartalsberichte und KI-Chat

- **Berichte:** Der ADMIN lädt Quartalsberichte (PDF) pro Unternehmen hoch, korrigiert das Quartal
  oder löscht Berichte. Alle Nutzer können die Berichte öffnen.
- **Chat:** Er beantwortet Fragen auf Deutsch aus den Berichten und nennt Bericht und Seite. Steht
  etwas nicht in den Berichten, sagt er das, statt zu raten. Jeder Nutzer hat seinen eigenen
  Verlauf.
- **„Evaluate quarterly reports“:** Der Button wertet alle Berichte eines Unternehmens entlang
  derselben 18 Kriterien aus. Das Ergebnis wird **gespeichert** und allen angezeigt. Gab es seit der
  letzten Auswertung keine neuen oder geänderten Berichte, erscheint „No new quarterly reports —
  evaluation already available“, und es entstehen keine Kosten. Nur der ADMIN kann eine neue
  Auswertung erzwingen.
- **Tageslimits** pro Konto und eine globale Obergrenze begrenzen die KI-Kosten.

### Tiefenanalyse

Der Button ist vorerst ein **Platzhalter**. Er zeigt mit einem echten Zeitstempel aus AWS, dass der
Ablauf im Hintergrund funktioniert. Die eigentliche Analyse kommt später.

### Rollen

| | ADMIN (Betreiber, genau ein Konto) | USER (wenige persönliche Konten) |
|---|---|---|
| Positionen sehen | ✅ mit Stückzahl, Preis, Datum | ✅ nur Firma, Ticker, Gruppe |
| Portfolio bearbeiten | ✅ | ❌ |
| Recherchedaten und Berichte lesen | ✅ | ✅ |
| Gruppen pflegen, Gruppe zuordnen | ✅ | ❌ |
| Berichte hochladen, ändern, löschen | ✅ | ❌ |
| Chat und Berichtsauswertung | ✅ höheres Tageslimit | ✅ eigenes Tageslimit, eigener Verlauf |
| Auswertung erzwingen | ✅ | ❌ |
| Investment-These und Thesis check lesen | ✅ | ✅ mit Hinweis „Persönliche Einschätzung des Betreibers, keine Anlageempfehlung.“ |
| Investment-These bearbeiten | ✅ | ❌ |

Alle Rechte prüft das Backend. Ausgegraute Buttons im Frontend sind nur ein Hinweis für den Nutzer,
kein Schutz.

### Bewusst nicht enthalten

Kein Handel und keine Broker-Anbindung. Keine Kauf- oder Verkaufsempfehlungen. Kein automatisches
Markt-Screening und keine Alarme. Keine öffentliche Ansicht und keine Selbstregistrierung.

---

## Sicherheit: Schutz des Logins

**Warum:** Die Login-Seite ist öffentlich erreichbar, und jeder kann dort automatisiert Passwörter
ausprobieren. Teure Schutzdienste wie Cognito oder eine WAF werden bewusst nicht eingesetzt, weil das
Budget bei etwa 20 €/Monat liegt. Deshalb schützt sich das Backend selbst, ohne Zusatzkosten, gegen
drei Angriffe:

1. viele Passwörter von einem Rechner aus probieren → **Bremse pro IP**
2. einen Account von vielen Rechnern aus angreifen → **Sperre pro Account**
3. herausfinden, welche Benutzernamen existieren → **gleiche Antwort und gleiche Antwortzeit**

| Baustein | Was er tut |
|---|---|
| `LoginThrottleInterceptor` + `LoginThrottleWebConfig` | Prüft bei jedem Login-Versuch zuerst die Bremse pro IP, noch bevor das Passwort angeschaut wird. Ab dem 6. Versuch pro Minute kommt `429` „Too many login attempts“ mit Wartezeit. Die Wartezeit wächst mit jeder Wiederholung von 1 bis auf 15 Minuten. |
| `LoginAttemptGuard` | Hält die Zähler: Versuche pro IP und Fehlversuche pro Account. Nach 10 Fehlversuchen ist der Account 15 Minuten gesperrt. Unbekannte Benutzernamen werden nicht gezählt, damit niemand mit erfundenen Namen den Speicher füllen kann. |
| `AuthController` | Beachtet beim Login die Sperre. Auch während der Sperre läuft die volle Passwortprüfung, und die Antwort ist gleich („Invalid username or password“). So verraten weder der Text noch die Antwortzeit, ob ein Account existiert oder gesperrt ist. |
| `ClientIpResolver` | Ermittelt die IP-Adresse des Besuchers. Hinter API Gateway steht die echte IP im Header `X-Forwarded-For`. Ob diesem Header vertraut wird, ist eine Einstellung, standardmäßig aus, damit niemand seine IP fälschen kann. |
| `LoginAudit` | Protokolliert jeden Login-Versuch mit Benutzername, IP und Grund, aber nie das Passwort. Steuerzeichen werden entfernt, damit niemand gefälschte Zeilen ins Protokoll schreiben kann. |
| `ClockConfig` | Stellt die Uhrzeit als eigenen Baustein bereit. Dadurch können die Tests die Zeit vorspulen, statt 15 Minuten wirklich zu warten. |
| `application.yml` | Enthält die Einstellung `app.security.trust-forwarded-for: false`. |
| Tests | Prüfen Grenzen und Wartezeiten, die Sperre und ihr Ende, dass parallele Fehlversuche korrekt gezählt werden und dass unbekannte Namen nicht gespeichert werden. Außerdem prüfen sie, dass auch während der Sperre die Passwortprüfung läuft und dass die Bremse in der echten Verdrahtung aktiv ist. |

Dazu kommen:

- **Session-Cookie** statt Basic Auth: `HttpOnly`, `Secure`, `SameSite=Strict`, nach dem Login eine neue Session-ID.
- **CSRF-Schutz** für alle ändernden Anfragen.
- **Schreibrechte standardmäßig nur für ADMIN.** Ein vergessener Endpoint ist damit geschlossen statt offen.
- In AWS zusätzlich die Drosselung der Login-Route durch API Gateway.

**Bewusste Grenze:** Wer einen Benutzernamen kennt, kann diesen Nutzer für 15 Minuten aussperren. Das
wird in Kauf genommen, weil es nur eine Handvoll persönlich vergebener Konten gibt.

## AWS-Infrastruktur (Zielbild, grob)

Region `eu-central-1`.

```
                                   Browser
                                      │  HTTPS
                                      ▼
                    CloudFront ── S3 (Frontend: React-App)
                         │  /api/*
                         ▼
                 API Gateway (HTTP API, Drosselung)
                         │  VPC Link
   ═════════════ VPC (privat, kein Internet-Gateway, kein NAT) ═════════════
   ║  ECS Fargate: Backend (Spring Boot)                                     ║
   ║    Login und Rollen, Tageslimits, Portfolio, Recherchedaten,            ║
   ║    Gruppen, Berichts-Metadaten                                          ║
   ║       │                 │ S3-Gateway-Endpoint      │ DynamoDB-Gateway-  ║
   ║       ▼                 │ (kostenlos)              │ Endpoint (kostenl.)║
   ║  RDS PostgreSQL         │                          │                    ║
   ═════════════════════════════════════════════════════════════════════════
                             ▼                          ▼
               S3: Quartalsberichte (privat)    DynamoDB: Jobs + Chatverlauf
                    │ S3-Events                       │ Stream
                    ▼                                 ▼
   ┌─ Lambdas außerhalb der VPC ────────────────────────────────────────────┐
   │ report-intelligence (Quarkus): ingest · cleanup · evaluate · chat      │
   │   → Bedrock Titan Embeddings · S3 Vectors · Anthropic API (Claude)     │
   │ deep-analysis-demo (klein, ohne KI)                                    │
   └────────────────────────────────────────────────────────────────────────┘

   Rundherum: IAM (MFA, keine Access Keys, OIDC für GitHub Actions) · AWS Budgets mit
   automatischer Kostenbremse · SSM Parameter Store (Konten, Tokens, API-Key) · SQS-DLQs ·
   CloudWatch-Logs · CI/CD: GitHub Actions → ECR → ECS
```

**Warum so:**

- **Kein NAT Gateway:** Das Backend hat bewusst keinen Internetzugang. Es spricht mit S3 und DynamoDB
  nur über die kostenlosen Gateway-Endpoints.
- **KI-Arbeit in Lambdas außerhalb der VPC:** Embeddings, Vektorsuche und Claude laufen dort und
  kosten im Leerlauf fast nichts.
- **Asynchrone Jobs:** Chat und Auswertung laufen über DynamoDB als Jobs, weil API Gateway Anfragen
  nach 30 s abbricht.

Ausführlich:

- [`docs/aws/aws-architecture-overview.md`](docs/aws/aws-architecture-overview.md) (Stand des
  Deployments)
- [`docs/superpowers/specs/2026-09-20-aws-deployment-design.md`](docs/superpowers/specs/2026-09-20-aws-deployment-design.md)
- [`docs/superpowers/specs/2026-11-03-quarterly-report-rag-design.md`](docs/superpowers/specs/2026-11-03-quarterly-report-rag-design.md)

---

## Umsetzungsstand

| Baustein | Stand |
|---|---|
| Portfolio (Grundfunktion), Recherchedaten-Speicherung, Recherche-Skill | ✅ vorhanden, v1-Stand |
| AWS-Basis: Account, Budgets, VPC, RDS, ECS-Backend, API Gateway, CI/CD | ✅ gebaut. ECS, RDS und Interface-Endpoints sind seit 2026-11-03 gestoppt, um Guthaben zu sparen |
| **v2 Phase 1:** Login mit Rollen, Portfolio nach Rolle, lokale Recherche mit Push, Gruppen (Backend) | 🔄 in Arbeit, Plan: [`docs/superpowers/plans/2026-11-04-v2-phase1-backend-login-research-groups.md`](docs/superpowers/plans/2026-11-04-v2-phase1-backend-login-research-groups.md) |
| Frontend für v2 (Login, Portfolio, Research-Seite) | ⏳ geplant |
| Job-Tabelle, Tiefenanalyse-Platzhalter, Tageslimits | ⏳ geplant |
| Quartalsberichte, Auswertung, Chat | ⏳ geplant (Design steht) |
| Company Research Agent (`company-research-agent/`) | ❄️ eingefroren, wird vorerst nicht weiterentwickelt |

Laufender Projektstand: [`PROJECT-STATUS.md`](PROJECT-STATUS.md).

---

# Entwicklung

Die folgenden Abschnitte beschreiben Einrichtung und Abläufe für Entwickler. Die Sandbox und die
Übertragung von Recherchedaten werden im Rahmen von v2 Phase 1 umgestellt.

## Sandbox für Web-Recherche-Skills

Der `.claude/skills/research-company/`-Skill lässt Claude Code selbst mit `WebSearch`/`WebFetch` im
offenen Internet recherchieren und die Ergebnisse per `Bash`/`curl` persistieren. Diese Kombination aus
ungeprüften Web-Inhalten und echter Tool-Ausführung ist ein klassisches Einfallstor für indirekte
Prompt-Injection (eine Webseite enthält Text, der wie eine Anweisung an das Modell aussieht). Der Skill
selbst begrenzt das bereits auf Prompt-Ebene (siehe dessen "Security"-Abschnitt), zusätzlich läuft er
**verpflichtend, nicht optional** in einem Docker-Container, der nur dieses Repository mountet — nicht
das restliche Home-Verzeichnis (der Skill weist in seinem Security-Abschnitt selbst darauf hin und bricht
ab, falls er außerhalb der Sandbox gestartet wird):

```bash
export RESEARCH_INGEST_TOKEN='<token aus MachineTokenGenerator>'
./docker/claude-sandbox/run.sh
```

Was die Sandbox dabei bekommt und was nicht:

- **Kein ADMIN-Passwort.** Die Sandbox erhält nur `RESEARCH_INGEST_TOKEN`, ein Bearer-Token mit genau einem
  Recht: `POST /api/research/ingest/snapshots` am **lokalen** Backend. Es öffnet weder Portfolio noch
  Login-Endpoints noch das Live-System.
- **Das Repo ist read-only gemountet** (`/workspace:ro`). Eine manipulierte Sitzung kann keine Skripte
  ändern, die der Betreiber später auf dem Host ausführt.
- **Der Ingest-Endpoint existiert nur lokal.** Er ist nur mit `app.research-ingest.enabled=true`
  (Profil `local`) registriert, sonst antwortet der Pfad mit `404`. In AWS ist er nie aktiv.
- **Der Skill zeigt vor dem Senden das vollständige JSON** und wartet auf dein ausdrückliches OK.
- **Achtung, `.env` ist aus der Sandbox lesbar** (read-only heißt nicht unlesbar). Deshalb stehen dort nur
  **Hashes** (`ADMIN_PASSWORD_HASH`, `APP_RESEARCH_INGEST_TOKEN_HASH`), nie Klartext-Tokens oder
  -Passwörter. Aus dem gleichen Grund ist ein **starkes ADMIN-Passwort** wichtig: Wer den bcrypt-Hash
  lesen kann, kann offline raten.

Selbst falls die Prompt-Injection-Mitigation versagt, bleibt ein potenzieller Schaden auf das Schreiben
einzelner Snapshots in die lokale Datenbank beschränkt.

### Token erzeugen

Einmalig, analog zum Admin-Passwort-Hash (siehe unten):

```bash
cd backend
mvn -q spring-boot:run -Dspring-boot.run.main-class=com.valuescreener.security.MachineTokenGenerator
```

(Alternativ nach `mvn compile`: `java -cp target/classes com.valuescreener.security.MachineTokenGenerator`.)

Ausgabe: zwei Zeilen `token=<...>` und `sha256=<...>`.

- `sha256` kommt als `APP_RESEARCH_INGEST_TOKEN_HASH=<sha256>` in die `.env` (siehe `.env.example`). Das
  lokale Backend (`dev-up.sh`, Profil `local`) startet nicht ohne diesen Wert.
- `token` wird **nur** in der Shell exportiert, die die Sandbox startet: `export RESEARCH_INGEST_TOKEN='<token>'`.
  Nicht in `.env`, nicht ins Repo.

### Manueller Testablauf für den `research-company`-Skill

1. **Token und Hash erzeugen** (siehe oben) und den Hash als `APP_RESEARCH_INGEST_TOKEN_HASH` in die `.env`
   eintragen. Außerdem muss `ADMIN_PASSWORD_HASH` gesetzt sein (siehe "Admin-Passwort-Hash erzeugen").
2. **Backend starten** (Terminal 1, im Projekt-Root; startet auch Postgres und das Frontend):
   ```bash
   ./dev-up.sh
   ```
   Alternativ nur das Backend: `APP_RESEARCH_INGEST_TOKEN_HASH` und `ADMIN_PASSWORD_HASH` exportieren und
   `cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=local` (ohne Profil `local` ist der Ingest-
   Endpoint aus).
3. **Sandbox starten** (Terminal 2, im Projekt-Root):
   ```bash
   export RESEARCH_INGEST_TOKEN='<token aus Schritt 1>'
   ./docker/claude-sandbox/run.sh
   ```
   Beim allerersten Start fragt Claude Code im Container einmalig nach Login — separat vom Host-Login,
   bleibt danach im benannten Docker-Volume erhalten. Prüfe beim ersten Start, dass Claude Code mit dem
   read-only gemounteten `/workspace` normal startet.
4. **Recherche anstoßen** (im Sandbox-Prompt): z. B. `research AAPL`. Der Skill sollte automatisch
   greifen, mit `WebSearch`/`WebFetch` recherchieren (nie `company-research-agent` aufrufen), dir das
   vollständige JSON zeigen und nach deinem OK per `curl -X POST` an
   `http://host.docker.internal:8080/api/research/ingest/snapshots` persistieren.
5. **Von außen verifizieren** (Terminal 1 oder ein drittes, **nicht** in der Sandbox). Das Lesen läuft über
   die Session des Browsers; im UI unter "Research" erscheint der neue Snapshot mit den Findings, jedes mit
   eigener Quelle (`sourceUrl`), Datum (`asOfDate`) und deskriptiv formuliertem `claim`. Den Ingest-Endpoint
   selbst kannst du so prüfen (Token wird nicht auf der Kommandozeile übergeben):
   ```bash
   curl -i -H @<(printf 'Authorization: Bearer %s\n' "$RESEARCH_INGEST_TOKEN") \
     -H 'Content-Type: application/json' -d '{}' http://localhost:8080/api/research/ingest/snapshots
   ```
   Erwartet: `HTTP/1.1 400` (Token akzeptiert, leeres Payload abgelehnt). Ohne oder mit falschem Token:
   `401` mit `{"error":"Authentication required"}`; ohne Profil `local`: `404`.

### Recherchedaten ins Live-System übertragen

Recherchiert wird nur lokal (Sandbox, Ingest-Token). Ins Live-System gelangen ausschließlich Snapshots, die
du geprüft hast (Gruppe zugewiesen), und nur von deinem Rechner aus: `scripts/push-research.sh <ISIN>` holt den
Export vom lokalen Backend und schickt ihn mit einem **eigenen Sync-Token** an das Live-Backend. Das Skript läuft
auf dem Host, nie in der Sandbox (dort bricht es mit Fehler ab).

1. **Sync-Token erzeugen** (wie im Abschnitt "Token erzeugen"): `MachineTokenGenerator` ausführen. `sha256` ist
   für das Live-System, `token` für deinen Rechner. Das Skript verweigert die Arbeit, wenn Sync- und
   Ingest-Token identisch sind.
2. **Live-System**: nur der Hash geht nach SSM, als `APP_RESEARCH_SYNC_TOKEN_HASH=<sha256>` zusammen mit
   `APP_RESEARCH_SYNC_ENABLED=true`. Ist der Sync nicht aktiviert, antwortet `/api/research/sync/snapshots`
   mit `404`; ist er aktiviert, aber der Hash fehlt oder ist ungültig, startet das Backend nicht.
3. **Zugangsdaten auf dem Rechner**: Datei `~/.config/value-screener/live.env` anlegen (außerhalb des Repos,
   nur für dich lesbar) und mit einem Editor füllen, damit kein Token in der Shell-History landet:
   ```bash
   mkdir -p ~/.config/value-screener && (umask 077; : > ~/.config/value-screener/live.env)
   nano ~/.config/value-screener/live.env
   ```
   Inhalt der Datei (Unix-Zeilenenden, keine Anführungszeichen nötig):
   ```
   LIVE_BACKEND_URL=https://<live-backend>
   RESEARCH_SYNC_TOKEN=<token aus Schritt 1>
   RESEARCH_INGEST_TOKEN=<Ingest-Token des lokalen Backends>
   ```
   Das Skript bricht ab, wenn die Datei fehlt, für Gruppe/Andere lesbar ist oder Windows-Zeilenenden (CRLF)
   hat. Tokens stehen nie auf der Kommandozeile und werden nie ausgegeben; das Skript zeigt nur ISIN und
   HTTP-Status.
4. **Übertragen** (lokales Backend läuft, Standard `http://localhost:8080`, änderbar mit `LOCAL_BACKEND_URL`):
   ```bash
   scripts/push-research.sh US0378331005
   ```
   `LIVE_BACKEND_URL` muss mit `https://` beginnen (nur `http://localhost:<port>` bzw. `http://127.0.0.1:<port>`
   ist für lokale Tests erlaubt).

Hinweise:

- Fehler `409` beim Export: dem Snapshot ist noch keine Gruppe zugewiesen. `422` beim Import: die Gruppe
  existiert live nicht, eine später lokal neu angelegte Gruppe muss also auch im Live-System angelegt werden.
- Eine manuell gesetzte Gruppe (`MANUAL`) wird mit übertragen und live nicht von einem KI-Vorschlag überschrieben.

#### Lokaler End-to-End-Test mit einem zweiten Backend als "Live"

Das "Live"-Backend braucht eine **eigene Datenbank**, sonst beweist der Test nichts:

1. Datenbank anlegen (Postgres läuft per `docker compose up -d`):
   ```bash
   docker compose exec postgres createdb -U value_screener value_screener_live
   ```
2. Zweites Backend starten, **ohne** Profil `local` (Ingest aus, wie live), nur mit dem Hash. Flyway legt die
   Gruppen (V4) auch in dieser Datenbank an; `ADMIN_PASSWORD_HASH` wird wie gewohnt gebraucht:
   ```bash
   cd backend
   DB_URL=jdbc:postgresql://localhost:5432/value_screener_live SERVER_PORT=8081 \
   APP_RESEARCH_SYNC_ENABLED=true APP_RESEARCH_SYNC_TOKEN_HASH=<sha256> \
   mvn spring-boot:run
   ```
3. In `live.env` `LIVE_BACKEND_URL=http://localhost:8081` setzen, dann `scripts/push-research.sh <ISIN>`
   ausführen. Erwartet: zweimal `HTTP 200`.
4. Gegenprobe direkt in der "Live"-Datenbank:
   ```bash
   docker compose exec postgres psql -U value_screener -d value_screener_live \
     -c "select isin, company_name, group_source from company_snapshot"
   ```

### Lokale Postgres-Datenbank starten

```bash
docker compose up -d
```

### Admin-Passwort-Hash erzeugen

```bash
cd backend
mvn -q spring-boot:run \
  -Dspring-boot.run.main-class=com.valuescreener.security.AdminPasswordHashGenerator
```

Das Passwort wird verdeckt abgefragt (zweimal, bei Abweichung Abbruch) und nie als Argument übergeben, damit es nicht in Shell-History oder `ps` landet. Falls `spring-boot:run` keine Konsole bereitstellt, liest der Generator eine Zeile von stdin und warnt auf stderr, dass die Eingabe nicht verdeckt ist. Die Ausgabe des Generators daher nicht umleiten oder pipen (`> datei`, `| clip.exe`, `| tee`), sonst gibt es keine Konsole und die Passworteingabe wird sichtbar.

Den ausgegebenen Hash in `.env` eintragen, **in einfachen Anführungszeichen**, weil `dev-up.sh` die Datei per `source` einliest und die `$` sonst von der Shell expandiert werden (Abbruch mit `$2: unbound variable`):

```bash
ADMIN_PASSWORD_HASH='$2a$10$...'
```

`AdminPasswordHashGenerator` erzeugt auch die Hashes für USER-Konten.

### Weitere Konten (Rolle USER)

Neben dem ADMIN-Konto können weitere Konten mit der Rolle `USER` konfiguriert werden (Index 0, 1, ...):

```bash
export APP_USERS_0_USERNAME=alice
export APP_USERS_0_PASSWORD_HASH='<Hash aus AdminPasswordHashGenerator>'
```

Regeln (werden beim Start geprüft, bei einem Fehler startet die Anwendung nicht):

- Benutzernamen sind nicht case-sensitiv, müssen eindeutig sein (auch gegenüber `ADMIN_USERNAME`), dürfen nicht leer sein; `*` ist reserviert.
- Der Passwort-Hash muss ein gültiger bcrypt-Hash sein (auch für ADMIN, leer ist nicht erlaubt).
- Passwortlänge (nur Dokumentation, aus einem Hash nicht prüfbar): ADMIN mindestens 20 Zeichen, USER mindestens 16 Zeichen. Empfohlen sind lange, zufällige Passwörter (bcrypt verwendet nur die ersten 72 Bytes).

### Backend starten

```bash
cd backend
export ADMIN_USERNAME=admin
export ADMIN_PASSWORD_HASH='<erzeugter-hash>'
mvn spring-boot:run
```

Backend läuft auf http://localhost:8080.

### Backend-Tests ausführen

```bash
cd backend
mvn test
```

### Frontend starten

```bash
cd frontend
npm install
npm run dev
```

Frontend läuft auf http://localhost:5173 und proxyt `/api`-Aufrufe an das Backend.

### Frontend-Tests ausführen

```bash
cd frontend
npm test
```

### Manuelle End-to-End-Prüfung

1. Backend und Frontend wie oben starten.
2. Browser auf http://localhost:5173 öffnen — "Mein Portfolio" ist leer.
3. Über das Anmeldeformular mit Benutzername/Passwort (wie oben gesetzt) anmelden und eine
   Position hinzufügen (z. B. Ticker `AAPL`, Stückzahl `10`, Einstiegspreis `150`, Kaufdatum
   `2026-01-15`).
4. Die Liste zeigt danach `AAPL` — Stückzahl und Einstiegspreis sind nirgends sichtbar.
5. "Impressum" im Menü öffnen (siehe nächste Aufgabe) und prüfen, dass die Platzhalter-Angaben
   vor einem echten Live-Gang durch echte Daten ersetzt werden.
