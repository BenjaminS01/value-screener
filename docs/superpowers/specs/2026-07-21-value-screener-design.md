# Value Screener — Design

Letztes Update: 2026-07-21
Status: Design abgeschlossen und vom Nutzer freigegeben, Implementierung steht noch aus.

**Wichtig:** Die Anwendung ist bewusst öffentlich erreichbar (Freelancer-Vorzeigeprojekt), wird aber vorerst nur vom Nutzer selbst zur eigenen Anlageentscheidung verwendet. Siehe Abschnitt 9 für die daraus folgenden Vorgaben (Formulierung, Disclaimer, Datensichtbarkeit).

## 1. Zweck

Ein persönliches Tool, das (a) das eigene Aktienportfolio auf fundamentale Veränderungen überwacht und (b) automatisch neue Aktienideen vorschlägt, die einem Buffett-artigen Value-Investing-Muster entsprechen: günstig bewertet, finanziell solide, gutes und verständliches Geschäftsmodell.

Dient gleichzeitig als:
- praktischer Nutzen für die eigene Anlagestrategie
- Portfolio-/Vorzeigeprojekt
- AWS- und KI-Lernvehikel (gleicher Stack wie das parallele Alpine-Guide-Projekt in `/mnt/c/Users/PCUser/dev/berg`)

Reines Analyse-/Hinweistool — **kein** Trading-Tool, keine Kauf-/Verkaufsausführung, keine Broker-Anbindung.

## 2. Scope

### Im MVP-Scope
- Manuelle Portfolio-Erfassung (Ticker, Stückzahl, Einstiegspreis, Kaufdatum)
- Täglicher automatischer Marktscreen über eine Screener-API mit branchenrelativen Buffett-Kriterien
- KI-Bewertung (Claude) der Top-Kandidaten: Geschäftsmodell-/Burggraben-Einschätzung
- Snapshot-basierte Änderungserkennung für bestehende Portfolio-Positionen (z. B. Marge fällt, Verschuldung steigt) inkl. KI-Erklärung der Änderung in Klartext
- Dedup-Logik: keine wiederholten Vorschläge, solange sich an einem bereits gezeigten Kandidaten nichts Wesentliches ändert
- Dashboard (React) mit zwei Ansichten: "Mein Portfolio" und "Vorschläge" — reine Anzeige, keine aktive Benachrichtigung

### Bewusst außerhalb des MVP-Scope
- CSV-/Kontoauszug-Import vom Broker (spätere Erweiterung; MVP ist manuelle Eingabe)
- E-Mail-Benachrichtigungen
- Freies Chat-Interface für Rückfragen zu Aktien/Portfolio
- Kauf-/Verkaufsausführung oder Broker-Anbindung
- Mehrbenutzer-/Accountsystem (nur ein Nutzer/Betreiber; die App ist zwar öffentlich *lesbar*, aber kein Mehrbenutzer-Produkt — siehe Abschnitt 9 zur Sichtbarkeit)

## 3. Buffett-Kriterien-Set (Startpunkt, konfigurierbar)

Kombination aus quantitativem Filter und qualitativer KI-Bewertung — beide zusammen entscheiden, ob eine Aktie vorgeschlagen wird.

**Quantitativ (branchenrelativ, nicht absolut):**
- Bewertung: KGV und KBV niedrig **im Vergleich zum Branchen-Median** der Screening-Ergebnisse
- Profitabilität: ROE > 15 %, stabile oder wachsende operative/Netto-Marge
- Cashflow: Free Cashflow positiv und wachsend
- Finanzielle Stabilität: niedriger Verschuldungsgrad (Debt/Equity), Current Ratio > 1.5
- Konsistenz: kein starker Gewinneinbruch über die letzten 5–10 Jahre

Der Branchenvergleich wird **selbst aus den Screening-Ergebnissen berechnet** (Median je Branche/Sektor innerhalb des aktuellen Suchergebnisses) — kein zusätzlicher API-Call, kein zusätzliches Datenbudget-Risiko.

Alle Schwellenwerte werden als konfigurierbare `ScreeningCriteria` abgelegt, nicht hartkodiert.

**Qualitativ (KI-bewertet, nur für Kandidaten die den Zahlenfilter bestehen):**
- Verständliches, nachvollziehbares Geschäftsmodell
- Erkennbarer Burggraben (Marke, Netzwerkeffekt, Kostenvorteil, Wechselkosten)
- Nur "gutes Geschäftsmodell"-Kandidaten werden final vorgeschlagen — gute Zahlen allein reichen nicht

## 4. Architektur / Komponenten

1. **Data Provider Client** — kapselt Aufrufe an die Fundamentaldaten-API (Screener-Endpunkt für Marktscreening, Einzelwert-Abfragen für Portfolio-Ticker)
2. **Screening Engine** — wendet die branchenrelativen Buffett-Kriterien auf die Screener-Ergebnisse an, ermittelt Top-Kandidaten
3. **AI Assessor** — Claude-Integration für (a) qualitative Geschäftsmodell-Bewertung der Top-Kandidaten und (b) Klartext-Erklärung erkannter Fundamental-Änderungen im Portfolio
4. **Snapshot Store & Dedup Logic** — speichert tägliche Kennzahlen-Snapshots pro Ticker, vergleicht mit letztem Snapshot, entscheidet ob eine Änderung/ein Kandidat "neu genug" für eine Anzeige ist
5. **Scheduler** — täglicher In-App-Job (Spring `@Scheduled`), orchestriert die komplette Pipeline; kein separater AWS-Batch-Service nötig
6. **Dashboard (React)** — "Mein Portfolio" (Positionen + erkannte Änderungen) und "Vorschläge" (neue Kandidaten mit KI-Begründung)

## 5. Datenfluss (täglicher Ablauf)

```
Scheduler triggert Job
  ├─ Portfolio-Pfad: für jede gehaltene Position → aktuelle Kennzahlen holen
  │     → Vergleich mit letztem Snapshot → bei Schwellenwert-Überschreitung:
  │       Claude erklärt die Änderung → als Alert im Dashboard markiert
  │
  └─ Screening-Pfad: Screener-API mit Buffett-Kriterien aufrufen (marktweit, 1 Call)
        → branchenrelative Filterung/Ranking
        → Top-Kandidaten, die NICHT bereits unverändert vorgeschlagen wurden
        → Claude bewertet Geschäftsmodell/Burggraben je Kandidat
        → nur "gutes Geschäftsmodell"-Kandidaten werden final in "Vorschläge" gespeichert
```

Beide Pfade laufen unabhängig, teilen sich aber denselben Snapshot-Speicher.

## 6. Datenmodell (Kernentitäten)

- **PortfolioPosition**: Ticker, Stückzahl, Einstiegspreis, Kaufdatum — **Stückzahl, Einstiegspreis und Kaufdatum sind intern-only** (nur für Snapshot-Vergleich/Änderungserkennung genutzt), in der öffentlichen Ansicht wird nur Ticker + Branche angezeigt (siehe Abschnitt 9)
- **FundamentalSnapshot**: Ticker, Datum, erfasste Kennzahlen (KGV, KBV, ROE, Margen, Verschuldungsgrad, Current Ratio, Branche, ...) — ein Eintrag pro Ticker pro Tag, Basis für Änderungserkennung
- **ScreeningCriteria**: konfigurierbares Kriterien-Set (Schwellenwerte, branchenrelativ)
- **Suggestion**: Ticker, Datum, ausschlaggebende Kennzahlen, KI-Geschäftsmodell-Bewertung (Text + Einstufung), Status (neu/bereits gesehen/verworfen)
- **FundamentalAlert**: Verknüpfung zu PortfolioPosition + FundamentalSnapshot-Vergleich + KI-Erklärung, Zeitstempel

## 7. Tech-Stack & Deployment

Gleicher Stack wie das Alpine-Guide-Projekt (Konsistenz, Portfolio-Wiederverwendbarkeit):

- **Backend**: Java 21 / Spring Boot 3 / Spring AI (Anthropic-Anbindung) / Spring Scheduling
- **Datenbank**: PostgreSQL (passt zu den relationalen Entitäten oben; auf AWS als RDS)
- **Frontend**: React/TypeScript, zwei Hauptviews (Portfolio, Vorschläge)
- **Deployment**: AWS App Runner (Backend), S3/CloudFront (Frontend), Secrets Manager (API-Keys für Datenanbieter + Anthropic)
- **Externe APIs**: Fundamentaldaten-Anbieter (Free-Tier, Kandidat: Financial Modeling Prep, 250 Calls/Tag inkl. Screener-Endpunkt laut erster Recherche — muss beim Implementieren gegen die aktuelle Doku verifiziert werden), Anthropic Claude API

## 8. Offene Risiken — früh im Implementierungsplan zu prüfen

1. Ob der gewählte Datenanbieter den Screener-Endpunkt tatsächlich im Free-Tier freigibt. **Fallback:** feste Index-Liste (z. B. S&P 500) als Suchraum statt marktweitem Screening.
2. Ob 250 Calls/Tag für Portfolio-Einzelabfragen + Screening + späteres Wachstum ausreichen.
3. Kosten der Claude-API-Calls bei täglicher Nutzung im Auge behalten — nur Top-Kandidaten bewerten lassen, nicht die komplette Trefferliste.
4. **KI-Qualitätsrisiko bei der Geschäftsmodell-Bewertung**: Ohne aktuellen Kontext (Firmenbeschreibung, Geschäftsbericht-Ausschnitt) bewertet Claude den Burggraben teils aus veraltetem/lückenhaftem Trainingswissen — riskant, da das Ergebnis in echte Anlageentscheidungen einfließt. Der AI Assessor muss echten, aktuellen Kontext vom Datenanbieter als Input bekommen, nicht nur Kennzahlen + Tickername.
5. **Abhängigkeit von einem instabilen Free-Tier-Anbieter**: Kostenlose Finanzdaten-APIs ändern häufig Konditionen oder werden eingestellt. Der Data Provider Client muss von Anfang an mit austauschbarer Schnittstelle gebaut werden, nicht fest an einen Anbieter gekoppelt.
6. **Absicherung des Schreibzugriffs**: Die App ist öffentlich im Internet erreichbar. Der Single-User-Login zum Bearbeiten des Portfolios muss robust sein (Schutz vor Brute-Force etc.) — größere Angriffsfläche als ein rein privates Tool.
7. **Reales Vertrauens-/Fehlerrisiko**: Da Ergebnisse für echte Anlageentscheidungen genutzt werden, muss das Tool als Ausgangspunkt für eigene Recherche verstanden werden, nicht als fertiges Urteil — Datenfehler oder KI-Fehleinschätzung wirken sich sonst direkt auf reales Geld aus.

## 7.1 Entwicklungsansatz

- **DDD-inspiriert, aber pragmatisch**: fachliche Bausteine aus Abschnitt 4/6 (Screening, Portfolio-Monitoring, AI-Bewertung) als klar getrennte Domänen-Konzepte mit eigener Sprache (Ubiquitous Language: Position, Snapshot, Suggestion, Alert, Kriterium) — aber kein Overengineering: keine CQRS/Event-Sourcing-Infrastruktur, keine künstliche Trennung in Bounded Contexts für ein Projekt dieser Größe. Ein Modul pro fachlichem Baustein reicht.
- **TDD**: Tests vor Implementierung, insbesondere für die Kern-Fachlogik (Kriterien-Filter, branchenrelative Berechnung, Snapshot-Vergleich/Dedup) — das sind die Stellen mit der höchsten Fehleranfälligkeit und dem größten Schaden bei falschem Verhalten (Risiko 7).
- **Clean Code**: sprechende Namen aus der fachlichen Domäne statt technischer Abkürzungen, kleine fokussierte Klassen/Methoden, keine vorzeitige Abstraktion.

## 9. Öffentlichkeit, Formulierungsrichtlinie & Compliance

Die Anwendung ist öffentlich erreichbar (Freelancer-Vorzeigeprojekt), wird aber vorerst ausschließlich vom Nutzer selbst für eigene Anlageentscheidungen genutzt. Daraus ergeben sich folgende verbindliche Vorgaben:

**Datensichtbarkeit:**
- "Mein Portfolio" bleibt öffentlich sichtbar, zeigt aber nur **Ticker + Branche** — keine Stückzahl, kein Einstiegspreis, kein Kaufdatum. Diese Felder existieren im Datenmodell, werden aber nie über die öffentliche API/UI ausgegeben.
- **Schreibzugriff** (Portfolio-Position anlegen/bearbeiten) ist durch einen einfachen Single-User-Login geschützt — nur der Betreiber kann Positionen ändern. Die Leseansicht (Portfolio + Vorschläge) bleibt komplett ohne Login erreichbar.

**Formulierungsrichtlinie für alle KI-generierten Texte** (Screening-Begründungen, Änderungs-Erklärungen): deskriptiv/analytisch, nicht imperativ empfehlend.
- Beispiel richtig: *"Aktie X erfüllt aktuell Kriterium Y (KGV Z % unter Branchenmedian)."*
- Beispiel falsch: *"Wir empfehlen den Kauf von Aktie X."*
- Wird als feste Systemvorgabe im Claude-Prompt für den AI Assessor hinterlegt, nicht nur als UI-Textrichtlinie — die KI soll so formulieren, nicht nur die Anzeige nachträglich entschärfen.

**Interessenkonflikt-Hinweis:** Taucht ein Screening-Kandidat auf, dessen Ticker bereits in der öffentlichen Portfolio-Liste steht, wird automatisch ein Hinweis angezeigt ("Betreiber hält diese Position"). Kein manueller Pflegeaufwand — reiner Ticker-Abgleich zwischen `Suggestion` und `PortfolioPosition`.

**Globaler Disclaimer:** Fest im Footer auf jeder Seite: keine Anlageberatung, automatisiert erzeugte Analyse auf Basis der in Abschnitt 3 dokumentierten Kriterien, keine Berücksichtigung individueller Anlageziele oder finanzieller Verhältnisse der Besucher.

**Impressum:** Name + erreichbare Anschrift des Betreibers, gut sichtbar verlinkt — deckt sowohl die allgemeine Impressumspflicht (Freelancer-Kundengewinnung zählt als geschäftsmäßiger Zweck, auch ohne Unternehmen/Gewerbeanmeldung) als auch die Offenlegungspflicht für Anlageempfehlungen nach Art. 20 EU-Marktmissbrauchsverordnung ab. Der Nutzer ist aktuell Privatperson ohne registriertes Unternehmen — das Impressum braucht deshalb keine Firmendaten (kein Handelsregister, keine USt-ID), nur Name + Anschrift. Eine Gewerbeanmeldung/Freiberufler-Registrierung ist keine Voraussetzung für die Veröffentlichung, wird erst relevant, sobald tatsächlich Einkünfte daraus entstehen.

**Bewusst nicht behoben, weiterhin zu prüfen vor Launch:** Nutzungsbedingungen des gewählten Fundamentaldaten-Anbieters bzgl. öffentlicher Anzeige der bezogenen Daten (siehe Risiko 1 in Abschnitt 8 — betrifft jetzt zusätzlich die Compliance-Frage, nicht nur das Datenbudget).

## Entscheidungsverlauf (Kontext für Anschluss-Sessions)

- MVP deckt Screening und Portfolio-Monitoring gemeinsam ab (gleiche Datenbasis, kein doppelter Aufbau)
- Kriterien werden mit dem Nutzer gemeinsam definiert (nicht vorab vom Nutzer vorgegeben) und sind branchenrelativ statt absolut
- KI bewertet ausdrücklich die qualitative Geschäftsmodell-/Burggraben-Seite, nicht nur Text-Zusammenfassungen — ist Teil des Filters, nicht nur Erklärungsschicht
- Portfolio-Import startet manuell (Ticker + Stückzahl); Kontoauszug-/CSV-Import ist explizit als spätere Erweiterung vorgemerkt, nicht MVP
- Keine Watchlist-Pflege durch den Nutzer gewünscht — Vorschläge sollen aktiv aus dem Gesamtmarkt kommen
- Keine Wiederholung unveränderter Vorschläge — Snapshot-Vergleich mit Schwellenwert statt täglicher Neu-Anzeige
- Free-Tier-Datenbudget ist bewusste Wahl fürs MVP, nicht durch Mangel an Alternativen (bezahlter Plan wurde angeboten, abgelehnt)
- Gleicher Tech-Stack wie Alpine Guide (`/mnt/c/Users/PCUser/dev/berg`) bewusst gewählt für Konsistenz im Portfolio, nicht für zusätzlichen AWS-Lernumfang
- App ist bewusst öffentlich (Freelancer-Showcase), aber vorerst reiner Eigenbedarf — daraus folgt die Formulierungs-/Compliance-Richtlinie in Abschnitt 9, keine Trennung in separate private/öffentliche Instanzen
- Portfolio-Sichtbarkeit als Kompromiss gelöst: Ticker öffentlich, Stückzahl/Einstiegspreis/Kaufdatum nicht — reduziert Privatsphäre-Risiko ohne den Showcase-Wert zu verlieren, und die öffentliche Ticker-Liste dient gleichzeitig als Interessenkonflikt-Offenlegung nach Art. 20 MAR
