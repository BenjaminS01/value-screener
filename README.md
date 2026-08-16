# Value Screener

[![CI](https://github.com/BenjaminS01/value-screener/actions/workflows/ci.yml/badge.svg)](https://github.com/BenjaminS01/value-screener/actions/workflows/ci.yml)

Persönliches Tool für Value-Investing nach Buffett-Prinzipien. Siehe `PROJECT-STATUS.md` und
`docs/superpowers/specs/2026-07-21-value-screener-design.md` für den vollständigen Kontext.

## Company Research Agent (in Entwicklung)

Ein eigenständiger, serverless MCP-Server (`company-research-agent/`), der zu Portfolio-Positionen
und Screening-Kandidaten aktuelle, quellenbelegte Informationen recherchiert — vor allem aus
Quartalsberichten — und damit die KI-Bewertung mit echtem, aktuellem Kontext statt nur
Kennzahlen versorgt. Läuft als eigener Claude-Agent mit Web-Search-Tool, abgesichert durch mehrere
Guardrails (deskriptive Formulierung, Fakten-Abgleich, Quellenverweis- statt Zitat-Pflicht,
Prompt-Injection-Widerstand). Bewusst als unabhängiges Sub-Projekt konzipiert, parallel zur
Hauptanwendung entwickelbar.

Design: [`docs/superpowers/specs/2026-07-24-company-research-agent-design.md`](docs/superpowers/specs/2026-07-24-company-research-agent-design.md).
Umsetzung läuft aktuell in einem separaten Git-Worktree auf Branch `feature/company-research-agent`
(siehe `PROJECT-STATUS.md` für den genauen Stand).

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
