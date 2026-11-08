# Übergabe v2 Phase 1 Backend (Stand 2026-11-08)

Dieses Dokument ist der Einstiegspunkt für eine neue Chat-Session, auch auf einem anderen Rechner. Es
enthält alles, was bisher nur im Chat oder in lokalen, nicht eingecheckten Dateien stand.

## 1. Wo wir stehen

- **Branch:** `feature/v2-phase1-backend` (Start bei `982d4c8`).
- **Spec:** [`docs/superpowers/specs/2026-11-04-v2-phase1-backend-research-groups-design.md`](../specs/2026-11-04-v2-phase1-backend-research-groups-design.md)
  (übergeordnet: [`2026-11-03-app-scope-v2-design.md`](../specs/2026-11-03-app-scope-v2-design.md)).
- **Plan:** [`2026-11-04-v2-phase1-backend-login-research-groups.md`](2026-11-04-v2-phase1-backend-login-research-groups.md),
  Tasks 1–8. **Alle 8 Tasks sind umgesetzt, geprüft und committet, die Schlussprüfung ist erledigt.**
  Offen ist nur noch der Abschluss des Branches (siehe Abschnitt 3).

| Task | Inhalt | Commit |
|---|---|---|
| 1 | Account-Liste aus Konfiguration, Startprüfung | `c11632e` |
| 2 | Session-Login, CSRF, JSON-Fehler, Schreiben nur ADMIN | `ce71548` |
| 3 | Brute-Force-Schutz pro IP und Account, Login-Audit | `c32e9ff` |
| 4 | Portfolio rollenabhängig, Korrigieren/Löschen, 409 bei Konflikt | `6f8fd74` |
| 5 | Lokaler Recherche-Eingang mit Maschinen-Token, Sandbox read-only, Skill sicherer | `b164fb0` |
| 6 | Aktiengruppen: Katalog mit 35 Gruppen (V4), ADMIN-CRUD | `2345ffe` |
| 7 | Eine Gruppe pro Aktie, KI schlägt vor, MANUAL gewinnt, Filter, Gruppe im Portfolio | `8dcc82f` |
| 8 | Export, Live-Import mit Sync-Token, `scripts/push-research.sh` | `3f9918c` |

**Zwei Schönheitsfehler am Commit `3f9918c` (Task 8):**
- Er trägt versehentlich die Commit-Message von Task 7. Richtig wäre:
  `feat(research): export + token-protected live import; push script for the host`.
- `scripts/push-research.sh` ist in git als nicht ausführbar gespeichert (Modus `100644`, weil
  `core.fileMode=false`). Gilt auch für `dev-up.sh` und `docker/claude-sandbox/run.sh`; Korrektur siehe
  unten bei der Schlussprüfung.
- Beides entscheidet und erledigt der Nutzer (er macht alle git-Operationen).

**Schlussprüfung (2026-11-08) erledigt.** Ein Review des ganzen Branches fand nichts Kritisches. Die
Korrekturen sind in zwei Runden umgesetzt, jeweils geprüft und vom Nutzer committet:
- `6ca504f` (Runde A): Die 23 Maschinen-Token-Tests liefen vorher nie, weil sie in `static`-Unterklassen
  steckten, die Surefire überspringt. Jetzt stehen sie in drei eigenständigen Klassen. Der Test
  „ausgeschaltet → 404“ ist jetzt aussagekräftig. Der Hash-Generator liest das Passwort verdeckt. Die
  Anführungszeichen in `.env.example` und README sind richtig, und `ADMIN_USERNAME=admin` ist vorbelegt.
- `cc18fae` (Runde B):
  - einheitliche JSON-Fehler über `web/GlobalRequestErrorHandler`, 409 bei Wettlauf-Konflikten,
    Grenzen für `sortOrder`
  - ISIN-Normalisierung bei Kauf und Verkauf
  - XSRF-Cookie mit `SameSite=Strict` und `Secure` aus der Konfiguration (live geprüft)
  - `LoginAudit` filtert alle Steuerzeichen, `MachineTokenFilter` arbeitet mit einem leeren Kontext,
    der Sync-Log nennt die tatsächlich gespeicherte Gruppe
  - `.gitattributes`, `dev-up.sh` prüft CRLF und ein leeres `ADMIN_USERNAME`, das Push-Skript zeigt nur
    das Feld `error`
- Noch offen für den Nutzer: `dev-up.sh`, `docker/claude-sandbox/run.sh` und `scripts/push-research.sh`
  sind in git noch `100644`. Korrektur:
  `git update-index --chmod=+x dev-up.sh docker/claude-sandbox/run.sh scripts/push-research.sh` und committen.

## 2. Teststand

- Kompletter Lauf mit Docker (`cd backend && mvn test`, inkl. Testcontainers): **266 Tests grün**
  (2026-11-08, nach `cc18fae`).
- Hinweis: Vor einem Zählen `backend/target/surefire-reports` löschen. Dort liegen sonst noch alte
  Berichte gelöschter Testklassen.
- Manueller Test am 2026-11-08 durch den Nutzer: `./dev-up.sh` gestartet, Sandbox
  (`docker/claude-sandbox/run.sh`) gestartet, Schreibschutz (`! touch /workspace/test.txt`) und
  Recherche („Recherchiere Apple“) durchgeführt — laut Nutzer erledigt.
- **Übertragung ins Live-System (`push-research.sh`) wurde NICHT getestet.** Siehe Abschnitt 3a:
  Das Thema wird ganz am Ende angeschaut.

## 3. Nächste Schritte

**Ganz normal mit den nächsten Tasks weitermachen.** An den Tokens wird jetzt nichts geändert, und
der Übertragungstest wird jetzt nicht gemacht (siehe 3a).

1. ~~Schlussprüfung des ganzen Branches~~ erledigt (2026-11-08, siehe oben).
2. ~~Kompletter `mvn test` mit Docker, `PROJECT-STATUS.md` aktualisieren~~ erledigt. Offen: Branch
   abschließen (superpowers:finishing-a-development-branch; git macht der Nutzer).
3. Nächste Phase laut Scope-v2-Spec planen (Frontend für Login, Portfolio, Research mit Gruppen),
   mit Spec und Plan wie bisher.

## 3a. Ganz am Ende nochmal anschauen: Tokens und Übertragung ins Live-System

Der Nutzer hat am 2026-11-08 entschieden: **Dieses Thema wird ganz am Ende des Projekts nochmal
angeschaut, weil es momentan viel zu kompliziert ist.** Bis dahin nichts daran ändern und den Test
nicht wieder vorschlagen.

Was damals zu kompliziert war:
- Der lokale Test der Übertragung braucht ein zweites Backend auf Port 8081, eine `live.env` und zwei
  Token-Paare. Das war nicht praktikabel testbar.
- Die Variablennamen (`RESEARCH_INGEST_TOKEN`, `APP_RESEARCH_SYNC_TOKEN_HASH` …) und die
  Generator-Ausgabe (`token=` / `sha256=`) waren verwirrend; Token und Hash wurden verwechselt.

Ideen für später (nicht beschlossen):
- Einen deutlich einfacheren Testweg ausdenken, oder direkt ins echte Live-System übertragen.
- Verständlichere Namen nach der Regel „`…_KEY` ist geheim, `…_KEY_HASH` bekommt das Backend“:

   | Heute | Vorschlag | Wofür |
   |---|---|---|
   | `RESEARCH_INGEST_TOKEN` | `RESEARCH_KEY` | Sandbox schreibt ins lokale Backend |
   | `APP_RESEARCH_INGEST_TOKEN_HASH` | `RESEARCH_KEY_HASH` | Hash dazu, in `.env` |
   | `RESEARCH_SYNC_TOKEN` | `LIVE_UPLOAD_KEY` | Rechner lädt ins Live-System hoch |
   | `APP_RESEARCH_SYNC_TOKEN_HASH` | `LIVE_UPLOAD_KEY_HASH` | Hash dazu, im Live-System |
   | `APP_RESEARCH_SYNC_ENABLED` | `LIVE_UPLOAD_ENABLED` | Upload live einschalten |
   | `LIVE_BACKEND_URL` | bleibt | |

   Dazu: `MachineTokenGenerator` bekommt ein Argument (`research` oder `live`) und gibt fertige
   Zeilen zum Kopieren aus, z. B. `RESEARCH_KEY_HASH=…` (für `.env`) und `RESEARCH_KEY=…` (geheim).
   Grund: Der Nutzer hat beim Test `token` und `sha256` verwechselt. Umsetzung über Platzhalter in
   `application.yml` (`token-hash: ${RESEARCH_KEY_HASH:}`), betroffen sind außerdem `run.sh`,
   `dev-up.sh`, `.env.example`, `SKILL.md`, `push-research.sh`, README und Tests.
   Erst dem Nutzer die Namen bestätigen lassen, und erst ganz am Ende.

## 4. Arbeitsweise mit dem Nutzer (verbindlich)

- Antworten auf **Deutsch**, kurz und übersichtlich. Wenn der Nutzer „nur die Befehle“ will, nur
  Befehle geben und nichts nebenbei prüfen.
- **Der Nutzer macht alle git-Operationen.** Claude und Subagents führen kein zustandsänderndes git
  aus; Claude gibt nur die Befehle (Commit-Message kurz, mit `Co-Authored-By`-Zeile).
- **Keine Tools installieren.**
- Tokens und Passwörter nie auf die Kommandozeile, nie ins Repo. `.env` enthält nur Hashes. Der
  Live-Sync-Token liegt außerhalb des Repos in `~/.config/value-screener/live.env` (`chmod 600`),
  weil die Sandbox das Repo (read-only) lesen kann. Die Sandbox bekommt nie das ADMIN-Passwort.
- Vorgehen bisher: subagent-driven development (Implementer auf Sonnet, danach Reviewer, Fixrunden,
  dann Commit durch den Nutzer). In jeden Implementer-Auftrag die „Global Constraints“ des Plans
  einfügen. Neue `@WebMvcTest`-Klassen ohne `SecuritySliceTestConfig` müssen `LoginThrottleWebConfig`
  per `excludeFilters` ausschließen.
- Der Nutzer arbeitet in WSL. Java 21 muss gesetzt werden:
  `export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64; export PATH=$JAVA_HOME/bin:$PATH`.
- Die IDE des Nutzers erzeugt CRLF. Diffs mit `git diff --ignore-cr-at-eol` ansehen und nur Dateien mit
  echten Änderungen committen. `.env` mit CRLF bricht `dev-up.sh` (`sed -i 's/\r$//' .env`).
- In einer Claude-Umgebung ohne Docker laufen nur die Slice- und Unit-Tests; Testcontainers-Tests
  muss der Nutzer lokal laufen lassen.

## 5. Zurückgestellte Punkte für die Schlussprüfung

**Stand 2026-11-08: Alle Punkte unten sind in der Schlussprüfung erledigt oder bewusst gelassen**,
außer der Beschriftung der Generator-Ausgabe (Abschnitt 3a). Bewusst gelassen: festes IP-Fenster und
O(n)-Aufräumen (Task 3), `List<?>` (Task 4), ungültiger `groupSource` beim Ingest (Task 7), Frontend
nutzt noch `sector` (Frontend-Phase). Kleine Reste aus den Korrektur-Reviews (optional):
`\p{Cntrl}` erfasst keine C1-Steuerzeichen; der Sync-Log ist ungetestet; ein doppelter
`MethodArgumentNotValidException`-Handler in `StockGroupIllegalArgumentHandler`.

Ursprüngliche Liste. Muss vor Abschluss erledigt werden:
- `.env.example` behauptet, der bcrypt-Hash brauche keine Anführungszeichen. Falsch: `dev-up.sh`
  liest `.env` mit `source`, deshalb `ADMIN_PASSWORD_HASH='$2a$…'` in einfachen Anführungszeichen.
- README „Admin-Passwort-Hash erzeugen“ und `.env.example` übergeben das Passwort als Argument
  (`-Dspring-boot.run.arguments=<passwort>`). Widerspricht der Regel „nie auf der Kommandozeile“;
  Generator sollte das Passwort verdeckt einlesen.
- Beschriftung der Generator-Ausgabe: gehört zu Abschnitt 3a (ganz am Ende).
- `.gitattributes` mit LF für Skripte und `.env*` anbieten.
- Gruppen-Endpunkte: gleichzeitiges Anlegen/Löschen kann `DataIntegrityViolationException` → 500
  liefern (auf 409 abbilden); keine Bereichsprüfung für `sortOrder`; ungültiges JSON ohne
  `{"error"}`-Body.

Kleinigkeiten (optional):
- Task 1: `AccountRegistryTest` prüft bei `*`/leer/fehlendem Hash nur den Exception-Typ.
- Task 2: XSRF-Cookie `Secure`/`SameSite` nicht an Session-Cookie-Einstellung gekoppelt (hinter TLS-Proxy
  `forward-headers-strategy` nötig); `AuthExceptionHandler` nur für `AuthController`; Rotationstest
  hängt an der Set-Cookie-Reihenfolge.
- Task 3: festes statt gleitendes IP-Fenster; O(n)-Aufräumen bei > 10 000 Einträgen; `LoginAudit`
  entfernt nur CR/LF.
- Task 4: `GET /api/portfolio` liefert `List<?>`; schwache ADMIN-„nicht 403“-Prüfung.
- Task 5: `MachineTokenFilter` setzt Auth im bestehenden Context statt `createEmptyContext`; kein Test
  für „enabled ohne Hash startet nicht“.
- Task 7: USER-403-Test ohne Body-Prüfung; ungültiger `groupSource`-Wert → 400 beim Ingest;
  **Frontend nutzt noch `sector`** (in der Frontend-Phase umstellen).
- Task 8: Push-Skript zeigt keinen Antwort-Body (bei 422 unklar, welche Gruppe fehlt); Sync-Log nennt
  die angefragte statt der behaltenen Gruppe.

## 6. Wichtige Entscheidungen (Kurzfassung, Details in den Specs)

- Kein Live-Betrieb bisher; Frontend folgt nach dem Backend.
- Recherche-Skill schreibt nur ins **lokale** Backend (Ingest-Token, nur im Profil `local`). Nie in AWS.
- Der Nutzer überträgt geprüfte Daten per Host-Skript mit eigenem Sync-Token ins Live-System.
- Aktiengruppen: gepflegte Liste (35 Startgruppen), eine Gruppe pro Aktie, KI schlägt vor, ADMIN
  überschreibt; eine manuelle Gruppe wird von der KI nie überschrieben.
- Quartalsbericht-Auswertung wird pro Aktie gespeichert und wiederverwendet; nur ADMIN erzwingt neu;
  Chat immer möglich (spätere Phase, siehe RAG-Spec).
- Daten-Alter-Hinweis (6 Monate), Investment-These und Status Beobachten/Abgelehnt nur für ADMIN,
  Recherche-Historie (spätere Phasen, siehe Scope-v2-Spec §4a).
