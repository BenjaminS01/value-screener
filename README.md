# Value Screener

[![CI](https://github.com/BenjaminS01/value-screener/actions/workflows/ci.yml/badge.svg)](https://github.com/BenjaminS01/value-screener/actions/workflows/ci.yml)

Persönliches Tool für Value-Investing nach Buffett-Prinzipien. Siehe `PROJECT-STATUS.md` und
`docs/superpowers/specs/2026-07-21-value-screener-design.md` für den vollständigen Kontext.

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
