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

Eine Seite pro Aktie, hier aus Sicht des ADMIN. Ein USER sieht dieselbe Seite **ohne** Investment-These
und ohne den Abschnitt „Thesis check“ in der Auswertung. Gruppe ändern und Berichte hochladen kann er
nicht, der Upload-Button ist für ihn ausgegraut.

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
║  ╭─ Investment thesis · ADMIN only ───────────────────────────────────────────────────────╮  ║
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
║  │ │ Thesis check (ADMIN only): Cloud margin assumption supported [Q3 2026, p. 31];     │ │  ║
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
╭─ Edit investment thesis · Intel Corp. · ADMIN only ──────────────────────╮
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

### Investment-These (nur ADMIN)

Value Investing heißt auch, vorher aufzuschreiben, *warum* man eine Aktie hält und *was* einen zum
Verkauf bringen würde. Der ADMIN hält pro Aktie fest:

- den Kaufgrund,
- die erwartete Entwicklung,
- die Verkaufsgründe,
- einen Status `Watching` oder `Rejected` mit Begründung.

„Holding“ ergibt sich automatisch aus dem Portfolio. Bei der Auswertung neuer Quartalsberichte
prüft ein Abschnitt „Thesis check“, welche Aussagen die Annahmen stützen oder ihnen widersprechen,
mit Seitenangabe. These, Status und Thesis check sind **nur für den ADMIN** sichtbar.

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
| Investment-These und Thesis check | ✅ | ❌ nicht sichtbar |

Alle Rechte prüft das Backend. Ausgegraute Buttons im Frontend sind nur ein Hinweis für den Nutzer,
kein Schutz.

### Bewusst nicht enthalten

Kein Handel und keine Broker-Anbindung. Keine Kauf- oder Verkaufsempfehlungen. Kein automatisches
Markt-Screening und keine Alarme. Keine öffentliche Ansicht und keine Selbstregistrierung.

---

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
export ADMIN_USERNAME=admin
export ADMIN_PASSWORD=<dein-klartext-passwort>
./docker/claude-sandbox/run.sh
```

Selbst falls die Prompt-Injection-Mitigation versagt, bleibt ein potenzieller Schaden auf das gemountete
Repo beschränkt statt auf den restlichen Rechner überzugreifen.

### Manueller Testablauf für den `research-company`-Skill

Es gibt ein Passwort, das an zwei Stellen in zwei Formen gebraucht wird: das Backend speichert nur den
bcrypt-**Hash** (`ADMIN_PASSWORD_HASH`), der Skill braucht für HTTP Basic Auth das **Klartext-Passwort**
(`ADMIN_PASSWORD`). Beide müssen zum selben Passwort gehören.

1. **Hash erzeugen** (einmalig, z. B. per `AdminPasswordHashGenerator`, siehe oben "Admin-Passwort-Hash
   erzeugen", oder extern per beliebigem bcrypt-Tool).
2. **Backend starten** (Terminal 1):
   ```bash
   cd backend
   export ADMIN_USERNAME=admin
   export ADMIN_PASSWORD_HASH='<Hash aus Schritt 1>'
   mvn spring-boot:run
   ```
   Falls Verbindungsfehler zu Postgres: `docker compose up -d` (siehe oben) zuerst ausführen.
3. **Sandbox starten** (Terminal 2, im Projekt-Root):
   ```bash
   export ADMIN_USERNAME=admin
   export ADMIN_PASSWORD='<Klartext-Passwort aus Schritt 1>'
   ./docker/claude-sandbox/run.sh
   ```
   Beim allerersten Start fragt Claude Code im Container einmalig nach Login — separat vom Host-Login,
   bleibt danach im benannten Docker-Volume erhalten.
4. **Recherche anstoßen** (im Sandbox-Prompt): z. B. `research AAPL`. Der Skill sollte automatisch
   greifen, mit `WebSearch`/`WebFetch` recherchieren (nie `company-research-agent` aufrufen) und am Ende
   selbst per `curl -X POST` an `http://host.docker.internal:8080/api/research/snapshots` persistieren.
5. **Von außen verifizieren** (Terminal 1 oder ein drittes, **nicht** in der Sandbox —
   `ADMIN_USERNAME`/`ADMIN_PASSWORD` müssen dort erneut exportiert sein):
   ```bash
   curl -i -u "$ADMIN_USERNAME:$ADMIN_PASSWORD" http://localhost:8080/api/research/snapshots/<ISIN>
   ```
   Erwartet: `HTTP/1.1 200` und die recherchierten Findings als JSON, jedes mit eigener Quelle
   (`sourceUrl`), Datum (`asOfDate`) und deskriptiv formuliertem `claim`.

## Phase 1: Projekt-Grundgerüst + Portfolio-Grundfunktion

### Voraussetzungen

- Java 21
- Maven 3.9+
- Node.js 20+
- Docker (für die lokale Postgres-Instanz)

### Lokale Postgres-Datenbank starten

```bash
docker compose up -d
```

### Admin-Passwort-Hash erzeugen

```bash
cd backend
mvn -q spring-boot:run \
  -Dspring-boot.run.main-class=com.valuescreener.security.AdminPasswordHashGenerator \
  -Dspring-boot.run.arguments=<dein-passwort>
```

Den ausgegebenen Hash als `ADMIN_PASSWORD_HASH` setzen (siehe unten).

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
