# Projektstatus: Value Screener

Letztes Update: 2026-07-21
Aktuelle Phase: **Implementierung Phase 1 läuft** (Subagent-Driven Development nach Plan).

Dieses Dokument fasst den Stand zusammen, damit eine neue Session ohne erneute Erklärung anschließen kann.

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

- **Task 1 (Backend-Projekt-Grundgerüst): fertig implementiert, geprüft (Review: Approved,
  keine Critical/Important-Findings) und vom Nutzer selbst committet.**
- Dabei aufgetretenes und gelöstes Umgebungsproblem: Testcontainers 1.x ist mit der lokalen
  Docker-Engine-Version in diesem WSL-Setup inkompatibel (bekannter Upstream-Bug). Fix:
  `backend/src/test/resources/docker-java.properties` mit `api.version=1.44` — siehe Projekt-Memory
  "WSL Docker/Testcontainers fix" für Details, falls das bei Alpine Guide wieder auftaucht.
- **Kosmetisch offen (nicht blockierend):** Branch heißt noch `master`, nicht `main`
  (`git branch -m main`, jederzeit vom Nutzer nachholbar).
- **Repo ist noch privat/lokal** (kein `git remote`) — vor dem ersten Push auf GitHub muss die
  Historie frei von persönlichen Daten sein (siehe Hinweis unten zum öffentlichen Repo).
- **Nächster Schritt bei Wiederaufnahme:** Task 2 (PortfolioPosition-Domänen-Aggregat) erklären und
  per Subagent umsetzen lassen — siehe Plan-Datei.

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
