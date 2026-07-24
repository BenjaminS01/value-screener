# Projektstatus: Value Screener

Letztes Update: 2026-07-24
Aktuelle Phase: **Phase 1 abgeschlossen** (siehe Abschnitt weiter unten). Parallel dazu läuft die
Umsetzung des Company-Research-Agent-Sub-Projekts in einem eigenen Worktree (siehe unten).

Dieses Dokument fasst den Stand zusammen, damit eine neue Session ohne erneute Erklärung anschließen kann.

## Hinweis für diese Session: paralleles Worktree für den Company Research Agent

Falls du (eine neue Claude-Code-Session) dieses Verzeichnis
(`/mnt/c/Users/PCUser/dev/value-screener`, Branch `main`) geöffnet hast: dieses Verzeichnis ist
bewusst unangetastet und frei für Arbeit an der Hauptanwendung (`backend/`, `frontend/`) — nutz es
dafür ohne Rücksicht auf das Folgende.

Parallel dazu läuft in einem **eigenen Git-Worktree** unter
`/mnt/c/Users/PCUser/dev/value-screener-research` (Branch `feature/company-research-agent`, von
`main` bei Commit `7e9e14a` abgezweigt) die Umsetzung des Company Research Agent — ein
eigenständiges, serverless MCP-Server-Sub-Projekt (`company-research-agent/`), das Quartalsberichte
recherchiert. Design: `docs/superpowers/specs/2026-07-24-company-research-agent-design.md`.
Implementierungsplan: `docs/superpowers/plans/2026-07-24-company-research-agent.md` (per
Subagent-Driven Development abgearbeitet, Fortschritt in
`.superpowers/sdd/progress.md` **im Worktree**, nicht in diesem Verzeichnis — Worktrees teilen sich
zwar die Git-Historie, aber nicht git-ignorierte Scratch-Dateien).

**Warum ein Worktree statt direkt auf `main`:** damit diese Session (Hauptanwendung) und die andere
Session (Company Research Agent) gleichzeitig arbeiten können, ohne sich gegenseitig unfertige
Dateien im selben Arbeitsverzeichnis zu überschreiben. Beide Worktrees teilen sich dasselbe
`.git`-Verzeichnis; Commits in einem sind über `git log`/`git fetch` auch vom anderen aus sichtbar.

**Zusammenführen:** noch nicht entschieden/erfolgt — wenn der Company-Research-Agent-Plan
abgeschlossen ist, wird `feature/company-research-agent` regulär nach `main` gemerged (Nutzer
entscheidet Zeitpunkt und Methode, wie bei allen Git-Operationen in diesem Projekt).

## Idee

Persönliches Tool für Value-Investing nach Buffett-Prinzipien:
1. **Portfolio-Monitoring** — erkennt, wenn sich bei gehaltenen Positionen fundamental etwas ändert (z. B. Marge fällt, Verschuldung steigt), erklärt die Änderung per KI in Klartext
2. **Screening** — schlägt täglich automatisch neue Aktien vor, die günstig bewertet sind (branchenrelativ) UND laut KI-Bewertung ein gutes, verständliches Geschäftsmodell mit erkennbarem Burggraben haben

Kein Trading-Tool — reine Analyse/Vorschläge, keine Kauf-/Verkaufsausführung.

**Öffentlich, aber vorerst nur Eigenbedarf:** Die App soll öffentlich erreichbar sein (Freelancer-Vorzeigeprojekt), wird aber vorerst nur vom Nutzer selbst genutzt. Daraus folgt eine Formulierungs- und Compliance-Richtlinie (siehe Design-Abschnitt 9): KI-Texte sind deskriptiv statt empfehlend formuliert, Portfolio zeigt öffentlich nur Ticker (keine Stückzahl/Einstiegspreis), Disclaimer + Impressum sind Pflichtbestandteile — Hintergrund: BaFin-Anlageberatung greift bei nicht-personalisierten, öffentlich verbreiteten Inhalten i.d.R. nicht, aber die Offenlegungspflichten aus Art. 20 EU-Marktmissbrauchsverordnung (Objektivität, Identität, Interessenkonflikte) gelten auch für Privatpersonen/Hobby-Tools.

Dient gleichzeitig als Portfolio-/Vorzeigeprojekt und AWS/KI-Lernvehikel, gleicher Tech-Stack wie das parallele Projekt **Alpine Guide** (`/mnt/c/Users/PCUser/dev/berg`): Java 21 / Spring Boot 3 / Spring AI / React / AWS App Runner.

## Vollständiges Design

Das vollständige, mit dem Nutzer abgestimmte Design steht in:
[`docs/superpowers/specs/2026-07-21-value-screener-design.md`](docs/superpowers/specs/2026-07-21-value-screener-design.md)

Dort: Scope (in/out), Buffett-Kriterien-Set, Architektur/Komponenten, täglicher Datenfluss, Datenmodell, Tech-Stack/Deployment, offene Risiken, kompletter Entscheidungsverlauf.

## Implementierungsplan (Phase 1)

Vollständiger Task-für-Task-Plan (13 Tasks: Backend-Grundgerüst, Portfolio-Domäne mit TDD,
Security, Frontend-Grundgerüst, öffentliche Portfolio-Ansicht, Login+Formular, Disclaimer/Impressum,
Docker Compose/README, GitHub-Actions-CI) steht in:
[`docs/superpowers/plans/2026-07-21-phase1-portfolio-foundation.md`](docs/superpowers/plans/2026-07-21-phase1-portfolio-foundation.md)

Ausführung erfolgt per Superpowers **Subagent-Driven Development**, mit zwei vom Nutzer explizit
gewünschten Abweichungen vom Standard-Ablauf:
1. **Vor jedem Task** erklärt der Controller ausführlich (Junior/Mid-Level-Java-Niveau), was der Task
   macht und welche Konzepte dahinterstecken — der Nutzer lernt dabei mit.
2. **Git ist komplett Sache des Nutzers.** Der Controller/die Subagenten führen keine verändernden
   Git-Befehle aus (kein `init`, `add`, `commit`, `branch`, `remote`, `push`) — nur Lesebefehle
   (`status`, `diff`, `log`) zur Erzeugung von Review-Diffs. Nach jedem erfolgreich geprüften Task
   nennt der Controller den exakten Commit-Befehl, den der Nutzer selbst ausführt. GitHub-Repo
   anlegen/verbinden/pushen ebenfalls ausschließlich Nutzer-Sache (siehe Task 13 im Plan).

Fortschritt wird in `.superpowers/sdd/progress.md` (im Projektordner, git-ignored) mitgeführt.

### Stand der Umsetzung

- **Repo ist live auf GitHub:** `https://github.com/BenjaminS01/value-screener`, Branch `main`,
  sauberer Working Tree, lokal und remote synchron.
- **Task 1 (Backend-Projekt-Grundgerüst): fertig, geprüft (Approved), committet und gepusht.**
  Dabei gelöstes Umgebungsproblem: Testcontainers 1.x ist mit der lokalen Docker-Engine-Version in
  diesem WSL-Setup inkompatibel. Fix: `backend/src/test/resources/docker-java.properties` mit
  `api.version=1.44` — siehe Projekt-Memory "WSL Docker/Testcontainers fix", falls das bei Alpine
  Guide wieder auftaucht.
- **Umgebungsfix (2026-07-23, während Task 5 entdeckt): Java-Versionskonflikt.** Die Maschine hatte
  Java 25 als sdkman-Default, `pom.xml` ist aber auf Java 21 konfiguriert. Folge: Mockito konnte bei
  `@MockBean` auf konkrete Klassen (z. B. `PortfolioService` in `PortfolioControllerTest`) nicht mehr
  per Bytecode-Instrumentierung mocken (`MockitoException: Mockito cannot mock this class`) — betraf
  jeden `@WebMvcTest`/`@MockBean`-Test, nicht nur diesen. Fix (Nutzerentscheidung: global, nicht nur
  projektlokal): vorhandenes System-JDK 21 (`/usr/lib/jvm/java-21-openjdk-amd64`) als sdkman-Kandidat
  `21.0.11-local` registriert und per `sdk default java 21.0.11-local` global als Standard gesetzt.
  Betrifft alle Projekte/Shells auf dieser Maschine, nicht nur value-screener — falls Alpine Guide
  oder llm-broker bewusst Java 25 brauchen, dort gegenprüfen.
- **Task 2 (PortfolioPosition-Domänen-Aggregat): fertig, geprüft (Approved), committet und gepusht**
  (Commit `ca13802`, "add PortfolioPosition aggregate with ticker, ISIN and company name
  invariants"). Während der Umsetzung zwei vom Nutzer entschiedene Scope-Erweiterungen gegenüber
  dem ursprünglichen Plan:
  - **`companyName`** ergänzt — Ticker allein ist für Menschen nicht lesbar; manuell einzugeben
    (kein Datenanbieter-Call in Phase 1), keine Privatsphäre-Implikation.
  - **`isin`** ergänzt und zur **eindeutigen Kennung** gemacht (Unique-Constraint von `ticker` auf
    `isin` verschoben) — Ticker sind exchange-spezifisch nicht global eindeutig, relevant weil der
    Nutzer weltweit Aktien hält (Kauf i. d. R. über Gettex). Ticker bleibt zusätzlich bestehen, da
    die Screening-Pipeline (Phase 2) den Datenanbieter ticker-basiert abfragt. ISIN-Format wird
    validiert (Regex, kein Prüfziffern-Check — bewusst kein Overengineering für MVP), bleibt
    Backend-/Admin-only, taucht nicht in der öffentlichen Ansicht auf.
  - Finaler Konstruktor: `PortfolioPosition(String ticker, String isin, String companyName,
    BigDecimal quantity, BigDecimal entryPrice, LocalDate purchaseDate)`.
  - Der Plan (`docs/superpowers/plans/2026-07-21-phase1-portfolio-foundation.md`) wurde für Tasks
    2–6, 9, 10 konsistent mit-aktualisiert (Migration, DTOs, Service, Controller-/Security-Tests,
    Frontend-Formular) — Task 3 kann direkt aus dem Plan umgesetzt werden, ohne weitere Anpassung.
- **Nachträgliche Modellkorrektur (2026-07-23, vor Task 3 entdeckt):** Nutzerfrage aufgedeckt, dass
  `PortfolioPosition` mit genau einem `quantity`/`entryPrice`/`purchaseDate` pro ISIN Teilkäufe und
  Teilverkäufe nicht abbilden kann. Zwei Optionen abgewogen:
  - **Option 1 (gewählt):** Position bleibt eine Zeile pro ISIN (aggregierter Nettobestand).
    `entryPrice` wird zum **mengengewichteten Durchschnittspreis**, `purchaseDate` bleibt das Datum
    des **Erstkaufs** (ändert sich bei Nachkäufen nicht). Neue Aggregat-Methoden `recordPurchase`
    (erhöht Menge, rechnet Durchschnittspreis neu) und `recordSale` (verringert Menge, Preis bleibt
    unverändert, Verkauf über Bestand hinaus wird abgelehnt; Menge 0 → Position gilt als
    `isClosed()` und wird vom Service gelöscht). Zieht auch Task-3-Korrektur nach sich:
    `findByTicker` → **`findByIsin`**, da ISIN (nicht Ticker) die eindeutige Kennung ist und für
    das Zusammenführen von Nachkäufen gebraucht wird. Task 4 wird von "addPosition" auf
    "buy/sell" umgestellt.
  - **Option 2 (vermerkt, nicht umgesetzt):** vollständiges Transaktions-/Lot-Modell (jeder Kauf/
    Verkauf als eigener Datensatz, Position als Read-Model aus Transaktionen berechnet, FIFO-
    Kostenbasis möglich). Genauer, aber mehr Infrastruktur — würde Richtung Event-Sourcing gehen,
    das die Design-Spec (Abschnitt 7.1) bewusst ausschließt. Für später vorgemerkt, falls z. B. eine
    exakte FIFO-Kostenbasis fürs Steuerjahr gebraucht wird.
  - Plan-Datei entsprechend angepasst: Task 2 um `recordPurchase`/`recordSale` erweitert, Task 3
    `findByIsin` statt `findByTicker`, Task 4 auf `buy`/`sell` (`BuyPositionRequest`/
    `SellPositionRequest`, `PositionNotFoundException`) umgestellt, Task 5 um `POST /api/portfolio/sell`
    ergänzt (`POST /api/portfolio` bleibt Pfad-/JSON-kompatibel, Task 6 braucht daher keine Änderung).
    Task 9/10 (Frontend, noch nicht umgesetzt) sind mit einem Hinweis im Plan markiert: Funktionsname
    `addPosition` bei Umsetzung in `buyPosition` umbenennen; ein Sell-Formular ist bewusst nicht Teil
    von Phase 1.
- **Aggregat-Erweiterung `recordPurchase`/`recordSale`/`isClosed`: fertig, geprüft (Approved),
  noch nicht committet** — Commit-Befehl siehe unten, vom Nutzer manuell auszuführen.
- **Nächster Schritt bei Wiederaufnahme:** Nach dem Commit der Aggregat-Erweiterung direkt mit
  Task 3 (PortfolioPositionRepository + Flyway-Migration, mit `findByIsin`) weitermachen — siehe
  Plan-Datei. Das MCP-Nebenthema (siehe unten) ist bewusst pausiert und nicht Teil dieses nächsten
  Schritts.

## Nebenthema: MCP (Model Context Protocol) — Lernthema, pausiert

Der Nutzer möchte sich neben der eigentlichen Implementierung auch praktisch mit MCP weiterbilden,
mit diesem Projekt als Lernvehikel (passt zum in der Idee genannten Zweck als AWS/KI-Lernvehikel).
Besprochener Fahrplan, noch nicht fortgesetzt:

1. **Einstieg (begonnen):** MCP zunächst rein als Dev-Tooling in der Claude-Code-Session ausprobieren
   — ein Postgres-MCP-Server gegen die lokale DB, um das Client/Server/Tool-Call-Konzept ohne
   App-Code-Änderung zu verstehen. Dafür wurde ein **Wegwerf-Docker-Container** `value-screener-postgres`
   gestartet (Postgres 16, DB/User/Passwort `value_screener`, Port 5432 — passend zu
   `backend/src/main/resources/application.yml`). **Wichtig:** Das ist nicht das offizielle
   Docker-Compose-Setup aus Task 9 des Implementierungsplans — beides bewusst getrennt halten. Der
   Container war zum Stand dieses Updates noch nicht mit dem MCP-Postgres-Server verbunden; die DB
   ist leer, da Flyway-Migrationen erst mit Task 3 entstehen.
2. **Später, für Phase 2 vorgemerkt (Screening Engine / AI Assessor):**
   - Eigener MCP-Server, der den Data-Provider-Client (Fundamentaldaten-Anbieter) kapselt, mit Tools
     wie `get_fundamentals(ticker)`, `screen_market(criteria)`, `get_company_profile(ticker)`. Das
     adressiert direkt zwei offene Risiken aus der Design-Spec (Abschnitt 8): Risiko 4 (AI Assessor
     braucht echten aktuellen Kontext statt nur Kennzahlen) und Risiko 5 (Data-Provider-Anbindung
     muss austauschbar bleiben — MCP liefert dafür eine saubere Protokollgrenze).
   - MCP-Client vom AI Assessor zu einem externen Server (z. B. Web-Suche) für zusätzlichen
     qualitativen Kontext (News, Firmenbeschreibung) bei der Burggraben-Bewertung.

Bewusste Entscheidung: MCP nicht in den Phase-1-Plan hineinziehen (reine Portfolio-CRUD/Auth-Basis),
sondern bei Wiederaufnahme von Phase 2 aktiv wieder ansprechen.

## Nebenthema: llm-broker als zentraler LLM-Service — Kandidat für Phase 2, nicht entschieden

Eigenes, separates Projekt unter `~/projects/llm-broker` (Spring Boot, gleicher Stack) geprüft:
ein eigenständiger Service, der Anwendungen von LLM-Details entkoppelt — Aufrufer schicken
`topic` + Rohdaten an `POST /api/v1/process`, der Broker übernimmt Prompt-Bau (Mustache-Template
pro Topic-YAML), providerunabhängigen LLM-Aufruf (Spring AI: OpenAI/Anthropic/Google/Ollama),
Input-/Output-Schema-Validierung, Retries bei ungültiger LLM-Antwort und liefert strukturiertes
JSON zurück. Dazu JWT-Auth (Keycloak) und Token-/Latenz-/Error-Metriken pro Topic. Laut
Git-Historie schon weit fortgeschritten (9 Epics fertig: Setup, CI/CD, Topic-Management,
LLM-Boundary, Processing-Pipeline, REST-API, Security, Observability).

**Warum relevant für Value Screener:** Beide AI-Anwendungsfälle im Design (Abschnitt 4: AI
Assessor) passen als Topics — Burggraben-/Geschäftsmodell-Bewertung eines Screening-Kandidaten und
Klartext-Erklärung einer erkannten Fundamental-Änderung. Der Broker würde zwei offene Punkte aus
der Design-Spec direkt mit-lösen, ohne sie in value-screener nochmal zu bauen:
- **Risiko 3** (Claude-Kosten im Blick behalten, Abschnitt 8) — Token-/Latenz-Metriken pro Topic
  gibt es im Broker bereits.
- Die **Formulierungsrichtlinie** (Abschnitt 9, deskriptiv statt empfehlend) lässt sich zentral im
  `promptTemplate` der jeweiligen Topic-YAML verankern statt verstreut im value-screener-Code.

**Warum noch nicht entschieden:** zusätzlicher Netzwerk-Hop und zusätzlicher Deploy (Broker braucht
eigenes JWT/OAuth2 — aktuell lokal über Keycloak gelöst, müsste für ein öffentlich erreichbares
Setup produktionstauglich laufen) — mehr Betriebsaufwand als eine direkte Spring-AI-Anthropic-
Anbindung im value-screener-Backend selbst. Der eigentlich starke Case ist die Wiederverwendung
über mehrere zukünftige Projekte hinweg (u. a. Alpine Guide, `/mnt/c/Users/PCUser/dev/berg`, nutzt
den Broker aktuell noch nicht), nicht der Nutzen für value-screener isoliert betrachtet.

Bewusste Entscheidung: wie beim MCP-Thema erst bei Wiederaufnahme von Phase 2 (AI Assessor) aktiv
wieder ansprechen und dann entscheiden, nicht in Phase 1 hineinziehen.

## Hinweis: öffentliches Repo

Dieses Repo wird öffentlich auf GitHub gehostet (Vorzeigeprojekt). Daher: keine private
E-Mail-Adresse, Postanschrift oder sonstige personenbezogene/geschäftsvertrauliche Angaben in
Doku, Code oder Commit-Historie — mit Ausnahme des Impressums (siehe Design-Spec Abschnitt 9),
das bewusst erst kurz vor Deployment mit echten Angaben befüllt wird. Zugangsdaten (Admin-Passwort,
API-Keys) laufen ausschließlich über Umgebungsvariablen, nie über Dateien im Repo.

## Offenes Risiko aus dem Design (weiterhin ungeprüft)

Risiko 1 aus der Design-Spec: ob der gewählte Fundamentaldaten-Anbieter (Kandidat: Financial
Modeling Prep) den Screener-Endpunkt tatsächlich im Free-Tier freigibt — relevant erst ab Phase 2
(Screening-Pipeline), nicht blockierend für Phase 1.

## Phase 1 abgeschlossen (2026-07-24)

Alle 13 Tasks umgesetzt, committed, per Task-Review geprüft. Finaler Whole-Branch-Review (gesamte
Historie, 19 Commits, Modell: Opus) fand keinen Critical-Fund. Ein Important-Fund (fehlender
globaler Exception-Handler — Domain-Exceptions wie `PositionNotFoundException` oder
`IllegalArgumentException` bei unbekannter ISIN/Overselling/fehlerhafter ISIN landeten als HTTP 500
statt 404/400) wurde noch vor Abschluss gefixt: `PortfolioExceptionHandler`
(`@RestControllerAdvice`, scoped auf `PortfolioController`) + 3 neue Tests, unabhängig vom Reviewer
erneut verifiziert (33/33 Backend-Tests grün).

**Backlog für Phase 2 (Minor-Funde aus dem finalen Review, keine Blocker für Phase 1):**
- `@Transactional` auf `PortfolioService.buy`/`sell` ergänzen (aktuell TOCTOU-Fenster bei
  gleichzeitigem Anlegen derselben ISIN — bei Single-User heute irrelevant, aber Phase 2 könnte
  durch einen Scheduler echte Nebenläufigkeit einführen).
- Regressionstest, der sicherstellt, dass der öffentliche Endpunkt (`GET /api/portfolio/public`)
  nie `quantity`/`entryPrice`/`isin` im JSON exponiert (aktuell nur strukturell durch die DTO
  garantiert, nicht durch einen expliziten Test abgesichert).
- Frontend-Fehlermeldungen in `AddPositionForm`/`portfolioApi.ts` sind statuscode-blind (z. B.
  falsches Passwort zeigt nur generische "401"-Meldung) — Politur, kein Blocker.
- Vor Phase 4 (AWS-Deployment): CORS-Policy fehlt noch (Dev funktioniert nur über den
  Vite-`/api`-Proxy), HTTP Basic Auth erfordert zwingend HTTPS in Produktion — beides für den
  Phase-4-Plan vormerken.
