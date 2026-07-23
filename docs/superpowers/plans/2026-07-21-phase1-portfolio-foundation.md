# Value Screener — Phase 1: Projekt-Grundgerüst & Portfolio-Grundfunktion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ein lauffähiges, getestetes Grundgerüst (Spring Boot Backend + React Frontend) mit der Portfolio-Grundfunktion: Positionen anlegen (login-geschützt) und öffentlich als Ticker-Liste anzeigen (ohne Stückzahl/Einstiegspreis).

**Architecture:** Java/Spring-Boot-Backend mit einer klar abgegrenzten `portfolio`-Domäne (Aggregat, Repository, Application Service, REST-Controller) hinter Spring Security (Single-Admin-Login für Schreibzugriff, öffentlicher Lesezugriff). React/TypeScript-Frontend, das die öffentliche Liste anzeigt und ein login-geschütztes Formular zum Hinzufügen bereitstellt. PostgreSQL via Docker Compose lokal, Flyway für Schema-Migrationen.

**Tech Stack:** Java 21, Spring Boot 3.3.5, Maven, Spring Data JPA, Spring Security, Flyway, PostgreSQL, Testcontainers, JUnit 5, AssertJ, Mockito — React 18, TypeScript, Vite, Vitest, React Testing Library.

## Global Constraints

- Backend liegt in `backend/`, Frontend in `frontend/`, beide im Projekt-Root `/mnt/c/Users/PCUser/dev/value-screener`.
- Java 21, Spring Boot 3.3.5, Maven als Build-Tool (keine Gradle-Dateien).
- Öffentliche GET-Endpunkte geben niemals Stückzahl, Einstiegspreis oder Kaufdatum aus — nur Ticker (Design-Spec Abschnitt 9).
- Schreibende Endpunkte erfordern Authentifizierung (Single-Admin-User, kein Mehrbenutzer-System) — Design-Spec Abschnitt 9 / Risiko 6.
- Jede Frontend-Seite zeigt den globalen Disclaimer-Footer; ein Impressum ist Pflichtbestandteil — Design-Spec Abschnitt 9.
- TDD: Test vor Implementierung für jede fachliche Logik (Domänen-Invarianten, Application-Service-Verhalten, Controller-Verhalten, React-Komponenten mit Verhalten). Reine Verkabelung/Konfiguration ohne eigenes Verhalten (z. B. `main()`-Einstiegspunkte) braucht keinen eigenen Testzyklus.
- DDD-inspiriert, aber pragmatisch: ein Modul (`portfolio`) für den fachlichen Baustein dieser Phase, Invarianten im Aggregat, kein CQRS/Event-Sourcing — Design-Spec Abschnitt 7.1.
- Ticker werden immer normalisiert (getrimmt, Großbuchstaben) gespeichert.
- Frontend-Fetch-Aufrufe verwenden relative Pfade (`/api/...`), Vite-Dev-Server proxyt diese ans Backend.
- Das Projekt wird auf GitHub gehostet und über eine GitHub-Actions-Pipeline getestet (Task 13) — Portfolio-relevant.
- `git remote`/`git push`/GitHub-Repo-Anlage sind ausschließlich Aufgabe des Nutzers, nicht der Subagenten oder des Controllers.
- Commits werden nach jedem Task nicht automatisch gesetzt — der Controller fragt den Nutzer nach bestandener Review, ob er selbst committet oder der Controller committen soll.

---

## Task 1: Backend-Projekt-Grundgerüst

**Files:**
- Create: `backend/pom.xml`
- Create: `backend/src/main/java/com/valuescreener/ValueScreenerApplication.java`
- Create: `backend/src/main/resources/application.yml`
- Create: `backend/src/main/resources/db/migration/.gitkeep`
- Create: `backend/.gitignore`
- Create: `backend/src/test/java/com/valuescreener/ValueScreenerApplicationTests.java`
- Create: `backend/src/test/resources/docker-java.properties`
- Create: `.gitignore` (Projekt-Root)

**Interfaces:**
- Produces: lauffähige Spring-Boot-Anwendung, Maven-Modul unter `backend/`, Flyway-Migrationsverzeichnis `classpath:db/migration` für spätere Tasks.

- [ ] **Step 1: Git-Repository initialisieren**

```bash
cd /mnt/c/Users/PCUser/dev/value-screener
git init
```

- [ ] **Step 2: Projekt-Root `.gitignore` anlegen**

```
# Root .gitignore
.DS_Store
*.log
```

- [ ] **Step 3: `backend/pom.xml` anlegen**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.3.5</version>
        <relativePath/>
    </parent>

    <groupId>com.valuescreener</groupId>
    <artifactId>value-screener-backend</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <name>value-screener-backend</name>
    <description>Backend for the Value Screener project</description>

    <properties>
        <java.version>21</java.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-core</artifactId>
        </dependency>
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-database-postgresql</artifactId>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.security</groupId>
            <artifactId>spring-security-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-testcontainers</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>postgresql</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 4: `backend/.gitignore` anlegen**

```
target/
*.class
.idea/
*.iml
```

- [ ] **Step 5: Hauptklasse anlegen**

`backend/src/main/java/com/valuescreener/ValueScreenerApplication.java`:

```java
package com.valuescreener;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ValueScreenerApplication {
    public static void main(String[] args) {
        SpringApplication.run(ValueScreenerApplication.class, args);
    }
}
```

- [ ] **Step 6: `application.yml` anlegen**

`backend/src/main/resources/application.yml`:

```yaml
spring:
  application:
    name: value-screener-backend
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/value_screener}
    username: ${DB_USERNAME:value_screener}
    password: ${DB_PASSWORD:value_screener}
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
  flyway:
    locations: classpath:db/migration

app:
  admin:
    username: ${ADMIN_USERNAME:admin}
    password-hash: ${ADMIN_PASSWORD_HASH:}
```

- [ ] **Step 7: Leeres Flyway-Migrationsverzeichnis anlegen**

`backend/src/main/resources/db/migration/.gitkeep`: leere Datei (Git verfolgt keine leeren Verzeichnisse).

- [ ] **Step 8: Fehlschlagenden Kontext-Test schreiben**

`backend/src/test/java/com/valuescreener/ValueScreenerApplicationTests.java`:

```java
package com.valuescreener;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ValueScreenerApplicationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void contextLoads() {
    }
}
```

- [ ] **Step 9: Test ausführen und Ergebnis prüfen**

Run: `cd backend && mvn test -Dtest=ValueScreenerApplicationTests`
Erwartet: Der Test lädt den Spring-Kontext, startet einen Postgres-Testcontainer (Docker muss laufen) und schlägt **nicht** fehl, da noch keine problematische Konfiguration existiert — Ziel dieses Schritts ist zu bestätigen, dass das Grundgerüst kompiliert und der Kontext lädt. Falls Docker nicht verfügbar ist, schlägt der Test mit einem Testcontainers-Fehler fehl — Docker muss vor den nächsten Schritten laufen.

**Bekanntes Umgebungsproblem in diesem WSL-Setup:** Falls der Test mit `BadRequestException (Status
400: client version 1.32 is too old. Minimum supported API version is 1.44)` fehlschlägt, liegt das
an einem bekannten Testcontainers-1.x-vs-Docker-29-Bug (Docker lehnt alte API-Versionen komplett ab,
statt herunterzuhandeln). Fix: `backend/src/test/resources/docker-java.properties` mit dem Inhalt
`api.version=1.44` anlegen (Verzeichnis ggf. vorher erstellen). Kein `pom.xml`-Versions-Downgrade/-Upgrade
nötig — mit dieser einen Zeile lädt der Kontext danach fehlerfrei. Details siehe Projekt-Memory
"WSL Docker/Testcontainers fix".

- [ ] **Step 10: Commit**

```bash
cd /mnt/c/Users/PCUser/dev/value-screener
git add .gitignore backend/
git commit -m "chore: set up Spring Boot backend skeleton"
```

---

## Task 2: PortfolioPosition-Domänen-Aggregat (TDD-Invarianten)

**Files:**
- Create: `backend/src/test/java/com/valuescreener/portfolio/PortfolioPositionTest.java`
- Create: `backend/src/main/java/com/valuescreener/portfolio/PortfolioPosition.java`

**Interfaces:**
- Produces: `PortfolioPosition` — Konstruktor `PortfolioPosition(String ticker, String isin, String companyName, BigDecimal quantity, BigDecimal entryPrice, LocalDate purchaseDate)`, Getter `getId()`, `getTicker()`, `getIsin()`, `getCompanyName()`, `getQuantity()`, `getEntryPrice()`, `getPurchaseDate()`. Wirft `IllegalArgumentException` bei ungültigen Werten.

`companyName` wird manuell beim Anlegen der Position eingegeben (kein externer Datenanbieter-Call in Phase 1). Grund: Der Ticker allein ist für Menschen nicht lesbar ("AAPL" sagt nicht sofort "Apple") — das ist kein Privatsphäre-Thema (der Firmenname zu einem Ticker ist ohnehin öffentlich bekannt), sondern reine Usability. Betrifft nur Stückzahl/Einstiegspreis/Kaufdatum, die weiterhin nie öffentlich angezeigt werden (Design-Spec Abschnitt 9).

`isin` wird ebenfalls manuell eingegeben und ist die **eigentliche eindeutige Kennung** einer Position — Ticker sind exchange-spezifisch und nicht global eindeutig (dasselbe Symbol kann je nach Börse unterschiedliche Wertpapiere meinen). Deshalb wandert die Eindeutigkeitsbeschränkung in der DB von `ticker` auf `isin` (siehe Task 3). Der Ticker bleibt zusätzlich als eigenes Feld bestehen, weil die Screening-Pipeline (Phase 2) den Datenanbieter ticker-basiert abfragt. Validiert wird nur das Format (`[A-Z]{2}[A-Z0-9]{9}[0-9]`, 12 Zeichen) — die volle ISIN-Prüfziffernberechnung ist für die MVP-Phase bewusst nicht implementiert (Overengineering für den aktuellen Nutzen). ISIN bleibt eine reine Backend-/Admin-Angabe und wird in der öffentlichen Ansicht nicht angezeigt (die zeigt weiterhin nur Ticker + Firmenname, Task 9).

- [ ] **Step 1: Fehlschlagenden Test schreiben**

`backend/src/test/java/com/valuescreener/portfolio/PortfolioPositionTest.java`:

```java
package com.valuescreener.portfolio;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PortfolioPositionTest {

    @Test
    void createsPositionWithNormalizedUppercaseTickerAndIsin() {
        PortfolioPosition position = new PortfolioPosition(
                "aapl", "us0378331005", "Apple Inc.", new BigDecimal("10"), new BigDecimal("150.00"), LocalDate.of(2026, 1, 15));

        assertThat(position.getTicker()).isEqualTo("AAPL");
        assertThat(position.getIsin()).isEqualTo("US0378331005");
        assertThat(position.getCompanyName()).isEqualTo("Apple Inc.");
    }

    @Test
    void rejectsBlankTicker() {
        assertThatThrownBy(() -> new PortfolioPosition(
                "  ", "US0378331005", "Apple Inc.", new BigDecimal("10"), new BigDecimal("150.00"), LocalDate.of(2026, 1, 15)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ticker");
    }

    @Test
    void rejectsTickerLongerThanTenCharacters() {
        assertThatThrownBy(() -> new PortfolioPosition(
                "TOOLONGTICKER", "US0378331005", "Apple Inc.", new BigDecimal("10"), new BigDecimal("150.00"), LocalDate.of(2026, 1, 15)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ticker");
    }

    @Test
    void rejectsBlankIsin() {
        assertThatThrownBy(() -> new PortfolioPosition(
                "AAPL", "  ", "Apple Inc.", new BigDecimal("10"), new BigDecimal("150.00"), LocalDate.of(2026, 1, 15)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("isin");
    }

    @Test
    void rejectsMalformedIsin() {
        assertThatThrownBy(() -> new PortfolioPosition(
                "AAPL", "NOT-AN-ISIN", "Apple Inc.", new BigDecimal("10"), new BigDecimal("150.00"), LocalDate.of(2026, 1, 15)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("isin");
    }

    @Test
    void rejectsBlankCompanyName() {
        assertThatThrownBy(() -> new PortfolioPosition(
                "AAPL", "US0378331005", "  ", new BigDecimal("10"), new BigDecimal("150.00"), LocalDate.of(2026, 1, 15)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("companyName");
    }

    @Test
    void rejectsZeroOrNegativeQuantity() {
        assertThatThrownBy(() -> new PortfolioPosition(
                "AAPL", "US0378331005", "Apple Inc.", BigDecimal.ZERO, new BigDecimal("150.00"), LocalDate.of(2026, 1, 15)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("quantity");
    }

    @Test
    void rejectsZeroOrNegativeEntryPrice() {
        assertThatThrownBy(() -> new PortfolioPosition(
                "AAPL", "US0378331005", "Apple Inc.", new BigDecimal("10"), BigDecimal.ZERO, LocalDate.of(2026, 1, 15)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("entryPrice");
    }

    @Test
    void rejectsNullPurchaseDate() {
        assertThatThrownBy(() -> new PortfolioPosition(
                "AAPL", "US0378331005", "Apple Inc.", new BigDecimal("10"), new BigDecimal("150.00"), null))
                .isInstanceOf(NullPointerException.class);
    }
}
```

- [ ] **Step 2: Test ausführen, Fehlschlag bestätigen**

Run: `cd backend && mvn test -Dtest=PortfolioPositionTest`
Erwartet: FAIL (Kompilierfehler, `PortfolioPosition` existiert nicht).

- [ ] **Step 3: Minimale Implementierung schreiben**

`backend/src/main/java/com/valuescreener/portfolio/PortfolioPosition.java`:

```java
package com.valuescreener.portfolio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.regex.Pattern;

@Entity
@Table(name = "portfolio_position")
public class PortfolioPosition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10)
    private String ticker;

    @Column(nullable = false, unique = true, length = 12)
    private String isin;

    @Column(name = "company_name", nullable = false, length = 200)
    private String companyName;

    @Column(nullable = false)
    private BigDecimal quantity;

    @Column(name = "entry_price", nullable = false)
    private BigDecimal entryPrice;

    @Column(name = "purchase_date", nullable = false)
    private LocalDate purchaseDate;

    protected PortfolioPosition() {
        // JPA
    }

    private static final Pattern ISIN_PATTERN = Pattern.compile("[A-Z]{2}[A-Z0-9]{9}[0-9]");

    public PortfolioPosition(String ticker, String isin, String companyName, BigDecimal quantity, BigDecimal entryPrice, LocalDate purchaseDate) {
        this.ticker = requireValidTicker(ticker);
        this.isin = requireValidIsin(isin);
        this.companyName = requireNonBlank(companyName, "companyName");
        this.quantity = requirePositive(quantity, "quantity");
        this.entryPrice = requirePositive(entryPrice, "entryPrice");
        this.purchaseDate = Objects.requireNonNull(purchaseDate, "purchaseDate must not be null");
    }

    private static String requireValidTicker(String ticker) {
        if (ticker == null || ticker.isBlank()) {
            throw new IllegalArgumentException("ticker must not be blank");
        }
        String normalized = ticker.trim().toUpperCase();
        if (normalized.length() > 10) {
            throw new IllegalArgumentException("ticker must not exceed 10 characters");
        }
        return normalized;
    }

    private static String requireValidIsin(String isin) {
        if (isin == null || isin.isBlank()) {
            throw new IllegalArgumentException("isin must not be blank");
        }
        String normalized = isin.trim().toUpperCase();
        if (!ISIN_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("isin must be a valid 12-character ISIN");
        }
        return normalized;
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    private static BigDecimal requirePositive(BigDecimal value, String fieldName) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
        return value;
    }

    public Long getId() {
        return id;
    }

    public String getTicker() {
        return ticker;
    }

    public String getIsin() {
        return isin;
    }

    public String getCompanyName() {
        return companyName;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getEntryPrice() {
        return entryPrice;
    }

    public LocalDate getPurchaseDate() {
        return purchaseDate;
    }
}
```

- [ ] **Step 4: Test ausführen, Erfolg bestätigen**

Run: `cd backend && mvn test -Dtest=PortfolioPositionTest`
Erwartet: PASS (alle 9 Tests grün).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/valuescreener/portfolio/PortfolioPosition.java backend/src/test/java/com/valuescreener/portfolio/PortfolioPositionTest.java
git commit -m "feat: add PortfolioPosition aggregate with validated invariants"
```

### Nachträgliche Erweiterung: Teilkäufe/Teilverkäufe (Option 1)

**Grund:** Ein einzelnes `quantity`/`entryPrice`/`purchaseDate` pro ISIN kann keine Nachkäufe oder
Teilverkäufe abbilden. Entscheidung (siehe `PROJECT-STATUS.md`): Position bleibt eine Zeile pro
ISIN, `entryPrice` wird zum mengengewichteten Durchschnittspreis, `purchaseDate` bleibt das Datum
des Erstkaufs. Ein vollständiges Transaktions-/Lot-Modell (Option 2) wäre genauer, ist aber für das
MVP bewusst zurückgestellt (Richtung Event-Sourcing, siehe Design-Spec Abschnitt 7.1).

**Interfaces (Ergänzung):** `void recordPurchase(BigDecimal additionalQuantity, BigDecimal purchasePrice)`,
`void recordSale(BigDecimal soldQuantity)`, `boolean isClosed()`.

- [ ] **Step 6: Fehlschlagende Tests ergänzen**

Ergänzung in `backend/src/test/java/com/valuescreener/portfolio/PortfolioPositionTest.java`:

```java
    @Test
    void recordPurchaseIncreasesQuantityAndRecalculatesWeightedAverageEntryPrice() {
        PortfolioPosition position = new PortfolioPosition(
                "AAPL", "US0378331005", "Apple Inc.", new BigDecimal("10"), new BigDecimal("150.00"), LocalDate.of(2026, 1, 15));

        position.recordPurchase(new BigDecimal("10"), new BigDecimal("170.00"));

        assertThat(position.getQuantity()).isEqualByComparingTo("20");
        assertThat(position.getEntryPrice()).isEqualByComparingTo("160.00");
        assertThat(position.getPurchaseDate()).isEqualTo(LocalDate.of(2026, 1, 15));
    }

    @Test
    void recordPurchaseRejectsZeroOrNegativeQuantity() {
        PortfolioPosition position = new PortfolioPosition(
                "AAPL", "US0378331005", "Apple Inc.", new BigDecimal("10"), new BigDecimal("150.00"), LocalDate.of(2026, 1, 15));

        assertThatThrownBy(() -> position.recordPurchase(BigDecimal.ZERO, new BigDecimal("170.00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("quantity");
    }

    @Test
    void recordSaleDecreasesQuantityWithoutChangingEntryPrice() {
        PortfolioPosition position = new PortfolioPosition(
                "AAPL", "US0378331005", "Apple Inc.", new BigDecimal("20"), new BigDecimal("160.00"), LocalDate.of(2026, 1, 15));

        position.recordSale(new BigDecimal("5"));

        assertThat(position.getQuantity()).isEqualByComparingTo("15");
        assertThat(position.getEntryPrice()).isEqualByComparingTo("160.00");
    }

    @Test
    void recordSaleRejectsQuantityExceedingCurrentHolding() {
        PortfolioPosition position = new PortfolioPosition(
                "AAPL", "US0378331005", "Apple Inc.", new BigDecimal("20"), new BigDecimal("160.00"), LocalDate.of(2026, 1, 15));

        assertThatThrownBy(() -> position.recordSale(new BigDecimal("25")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceed");
    }

    @Test
    void recordSaleToZeroClosesPosition() {
        PortfolioPosition position = new PortfolioPosition(
                "AAPL", "US0378331005", "Apple Inc.", new BigDecimal("20"), new BigDecimal("160.00"), LocalDate.of(2026, 1, 15));

        position.recordSale(new BigDecimal("20"));

        assertThat(position.getQuantity()).isEqualByComparingTo("0");
        assertThat(position.isClosed()).isTrue();
    }
```

- [ ] **Step 7: Tests ausführen, Fehlschlag bestätigen**

Run: `cd backend && mvn test -Dtest=PortfolioPositionTest`
Erwartet: FAIL (Kompilierfehler, `recordPurchase`/`recordSale`/`isClosed` existieren nicht).

- [ ] **Step 8: Methoden ergänzen**

Ergänzung in `backend/src/main/java/com/valuescreener/portfolio/PortfolioPosition.java` (Import
`java.math.RoundingMode` ergänzen; Methoden nach den bestehenden Gettern einfügen):

```java
    public void recordPurchase(BigDecimal additionalQuantity, BigDecimal purchasePrice) {
        BigDecimal validQuantity = requirePositive(additionalQuantity, "quantity");
        BigDecimal validPrice = requirePositive(purchasePrice, "purchasePrice");

        BigDecimal existingCost = this.quantity.multiply(this.entryPrice);
        BigDecimal addedCost = validQuantity.multiply(validPrice);
        BigDecimal newQuantity = this.quantity.add(validQuantity);

        this.entryPrice = existingCost.add(addedCost).divide(newQuantity, 6, RoundingMode.HALF_UP);
        this.quantity = newQuantity;
    }

    public void recordSale(BigDecimal soldQuantity) {
        BigDecimal validQuantity = requirePositive(soldQuantity, "quantity");
        if (validQuantity.compareTo(this.quantity) > 0) {
            throw new IllegalArgumentException("sale quantity must not exceed current holding");
        }
        this.quantity = this.quantity.subtract(validQuantity);
    }

    public boolean isClosed() {
        return quantity.signum() == 0;
    }
```

- [ ] **Step 9: Tests ausführen, Erfolg bestätigen; Commit**

Run: `cd backend && mvn test -Dtest=PortfolioPositionTest`
Erwartet: PASS (alle 14 Tests grün).

```bash
git add backend/src/main/java/com/valuescreener/portfolio/PortfolioPosition.java backend/src/test/java/com/valuescreener/portfolio/PortfolioPositionTest.java
git commit -m "feat: support partial buys/sells via weighted-average PortfolioPosition"
```

---

## Task 3: PortfolioPositionRepository + Flyway-Migration

**Files:**
- Create: `backend/src/main/resources/db/migration/V1__create_portfolio_position.sql`
- Create: `backend/src/main/java/com/valuescreener/portfolio/PortfolioPositionRepository.java`
- Create: `backend/src/test/java/com/valuescreener/portfolio/PortfolioPositionRepositoryTest.java`

**Interfaces:**
- Consumes: `PortfolioPosition` (Task 2).
- Produces: `PortfolioPositionRepository extends JpaRepository<PortfolioPosition, Long>` mit `Optional<PortfolioPosition> findByIsin(String isin)`.

`findByIsin` statt `findByTicker`, weil ISIN (nicht Ticker) die eindeutige Kennung ist (siehe
Task-2-Begründung) und Task 4 (Buy/Sell) genau diese Lookup braucht, um beim Nachkauf die
bestehende Position zu finden und zusammenzuführen.

- [ ] **Step 1: Flyway-Migration anlegen**

`backend/src/main/resources/db/migration/V1__create_portfolio_position.sql`:

```sql
CREATE TABLE portfolio_position (
    id BIGSERIAL PRIMARY KEY,
    ticker VARCHAR(10) NOT NULL,
    isin VARCHAR(12) NOT NULL UNIQUE,
    company_name VARCHAR(200) NOT NULL,
    quantity NUMERIC(20, 6) NOT NULL,
    entry_price NUMERIC(20, 6) NOT NULL,
    purchase_date DATE NOT NULL
);
```

Die Datei `db/migration/.gitkeep` aus Task 1 kann jetzt entfernt werden, da das Verzeichnis nicht mehr leer ist:

```bash
rm backend/src/main/resources/db/migration/.gitkeep
```

- [ ] **Step 2: Fehlschlagenden Repository-Test schreiben**

`backend/src/test/java/com/valuescreener/portfolio/PortfolioPositionRepositoryTest.java`:

```java
package com.valuescreener.portfolio;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PortfolioPositionRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private PortfolioPositionRepository repository;

    @Test
    void savesAndFindsPositionByIsin() {
        repository.save(new PortfolioPosition(
                "MSFT", "US5949181045", "Microsoft Corp.", new BigDecimal("5"), new BigDecimal("300.00"), LocalDate.of(2026, 2, 1)));

        assertThat(repository.findByIsin("US5949181045")).isPresent();
    }

    @Test
    void returnsEmptyWhenIsinNotFound() {
        assertThat(repository.findByIsin("US0000000000")).isEmpty();
    }
}
```

- [ ] **Step 3: Test ausführen, Fehlschlag bestätigen**

Run: `cd backend && mvn test -Dtest=PortfolioPositionRepositoryTest`
Erwartet: FAIL (Kompilierfehler, `PortfolioPositionRepository` existiert nicht).

- [ ] **Step 4: Repository-Interface anlegen**

`backend/src/main/java/com/valuescreener/portfolio/PortfolioPositionRepository.java`:

```java
package com.valuescreener.portfolio;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PortfolioPositionRepository extends JpaRepository<PortfolioPosition, Long> {
    Optional<PortfolioPosition> findByIsin(String isin);
}
```

- [ ] **Step 5: Test ausführen, Erfolg bestätigen**

Run: `cd backend && mvn test -Dtest=PortfolioPositionRepositoryTest`
Erwartet: PASS (beide Tests grün, Flyway-Migration läuft automatisch gegen den Testcontainer).

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/resources/db/migration/V1__create_portfolio_position.sql backend/src/main/java/com/valuescreener/portfolio/PortfolioPositionRepository.java backend/src/test/java/com/valuescreener/portfolio/PortfolioPositionRepositoryTest.java
git rm backend/src/main/resources/db/migration/.gitkeep
git commit -m "feat: add portfolio_position table and repository"
```

---

## Task 4: PortfolioService (Application Service)

**Files:**
- Create: `backend/src/main/java/com/valuescreener/portfolio/BuyPositionRequest.java`
- Create: `backend/src/main/java/com/valuescreener/portfolio/SellPositionRequest.java`
- Create: `backend/src/main/java/com/valuescreener/portfolio/PublicPortfolioPositionView.java`
- Create: `backend/src/main/java/com/valuescreener/portfolio/PositionNotFoundException.java`
- Create: `backend/src/main/java/com/valuescreener/portfolio/PortfolioService.java`
- Create: `backend/src/test/java/com/valuescreener/portfolio/PortfolioServiceTest.java`

**Interfaces:**
- Consumes: `PortfolioPosition`, `PortfolioPositionRepository` (Task 2, 3).
- Produces: `BuyPositionRequest(String ticker, String isin, String companyName, BigDecimal quantity, BigDecimal entryPrice, LocalDate purchaseDate)`,
  `SellPositionRequest(String isin, BigDecimal quantity)`, `PublicPortfolioPositionView(String ticker, String companyName)`,
  `PortfolioService` mit `void buy(BuyPositionRequest request)`, `void sell(SellPositionRequest request)` und
  `List<PublicPortfolioPositionView> listPublicPositions()` — konsumiert von `PortfolioController` (Task 5).

`BuyPositionRequest` ersetzt das frühere `AddPortfolioPositionRequest` 1:1 in Feldern und JSON-Form (siehe Nachtrag zu
Task 2/3: Teilkäufe erfordern jetzt ein Upsert-Verhalten statt eines reinen "Anlegen"). `isin` bleibt Pflichtfeld
(`@NotBlank`), taucht aber bewusst NICHT in `PublicPortfolioPositionView` auf — die öffentliche Ansicht bleibt bei
Ticker + Firmenname (siehe Task-2-Begründung oben).

`buy`: Sucht per `findByIsin`. Existiert die Position noch nicht, wird sie neu angelegt (wie früher `addPosition`).
Existiert sie bereits, wird `recordPurchase` aufgerufen (Menge/Durchschnittspreis werden auf der bestehenden
Position aktualisiert) — das bildet Nachkäufe ab.

`sell`: Sucht per `findByIsin`, wirft `PositionNotFoundException` wenn keine Position existiert, ruft sonst
`recordSale` auf. Ist die Position danach `isClosed()` (Menge 0), wird sie gelöscht statt gespeichert — das bildet
einen vollständigen Verkauf ab, ohne eine "leere" Position liegen zu lassen.

- [ ] **Step 1: Request- und View-DTOs anlegen**

`backend/src/main/java/com/valuescreener/portfolio/BuyPositionRequest.java`:

```java
package com.valuescreener.portfolio;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BuyPositionRequest(
        @NotBlank String ticker,
        @NotBlank String isin,
        @NotBlank String companyName,
        @NotNull @Positive BigDecimal quantity,
        @NotNull @Positive BigDecimal entryPrice,
        @NotNull LocalDate purchaseDate
) {
}
```

`backend/src/main/java/com/valuescreener/portfolio/SellPositionRequest.java`:

```java
package com.valuescreener.portfolio;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record SellPositionRequest(
        @NotBlank String isin,
        @NotNull @Positive BigDecimal quantity
) {
}
```

`backend/src/main/java/com/valuescreener/portfolio/PublicPortfolioPositionView.java`:

```java
package com.valuescreener.portfolio;

public record PublicPortfolioPositionView(String ticker, String companyName) {
}
```

`backend/src/main/java/com/valuescreener/portfolio/PositionNotFoundException.java`:

```java
package com.valuescreener.portfolio;

public class PositionNotFoundException extends RuntimeException {

    public PositionNotFoundException(String isin) {
        super("no portfolio position found for isin " + isin);
    }
}
```

- [ ] **Step 2: Fehlschlagenden Service-Test schreiben**

`backend/src/test/java/com/valuescreener/portfolio/PortfolioServiceTest.java`:

```java
package com.valuescreener.portfolio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioServiceTest {

    @Mock
    private PortfolioPositionRepository repository;

    @Test
    void buyCreatesNewPositionWhenIsinUnknown() {
        PortfolioService service = new PortfolioService(repository);
        when(repository.findByIsin("US0378331005")).thenReturn(Optional.empty());
        BuyPositionRequest request = new BuyPositionRequest(
                "aapl", "US0378331005", "Apple Inc.", new BigDecimal("10"), new BigDecimal("150.00"), LocalDate.of(2026, 1, 15));

        service.buy(request);

        ArgumentCaptor<PortfolioPosition> captor = ArgumentCaptor.forClass(PortfolioPosition.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getTicker()).isEqualTo("AAPL");
        assertThat(captor.getValue().getIsin()).isEqualTo("US0378331005");
        assertThat(captor.getValue().getQuantity()).isEqualByComparingTo("10");
    }

    @Test
    void buyMergesIntoExistingPositionWhenIsinKnown() {
        PortfolioService service = new PortfolioService(repository);
        PortfolioPosition existing = new PortfolioPosition(
                "AAPL", "US0378331005", "Apple Inc.", new BigDecimal("10"), new BigDecimal("150.00"), LocalDate.of(2026, 1, 15));
        when(repository.findByIsin("US0378331005")).thenReturn(Optional.of(existing));
        BuyPositionRequest request = new BuyPositionRequest(
                "aapl", "US0378331005", "Apple Inc.", new BigDecimal("10"), new BigDecimal("170.00"), LocalDate.of(2026, 3, 1));

        service.buy(request);

        ArgumentCaptor<PortfolioPosition> captor = ArgumentCaptor.forClass(PortfolioPosition.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getQuantity()).isEqualByComparingTo("20");
        assertThat(captor.getValue().getEntryPrice()).isEqualByComparingTo("160.00");
    }

    @Test
    void sellReducesExistingPosition() {
        PortfolioService service = new PortfolioService(repository);
        PortfolioPosition existing = new PortfolioPosition(
                "AAPL", "US0378331005", "Apple Inc.", new BigDecimal("10"), new BigDecimal("150.00"), LocalDate.of(2026, 1, 15));
        when(repository.findByIsin("US0378331005")).thenReturn(Optional.of(existing));

        service.sell(new SellPositionRequest("US0378331005", new BigDecimal("4")));

        ArgumentCaptor<PortfolioPosition> captor = ArgumentCaptor.forClass(PortfolioPosition.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getQuantity()).isEqualByComparingTo("6");
    }

    @Test
    void sellDeletesPositionWhenFullyClosed() {
        PortfolioService service = new PortfolioService(repository);
        PortfolioPosition existing = new PortfolioPosition(
                "AAPL", "US0378331005", "Apple Inc.", new BigDecimal("10"), new BigDecimal("150.00"), LocalDate.of(2026, 1, 15));
        when(repository.findByIsin("US0378331005")).thenReturn(Optional.of(existing));

        service.sell(new SellPositionRequest("US0378331005", new BigDecimal("10")));

        verify(repository).delete(existing);
        verify(repository, never()).save(existing);
    }

    @Test
    void sellRejectsUnknownIsin() {
        PortfolioService service = new PortfolioService(repository);
        when(repository.findByIsin("US0378331005")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.sell(new SellPositionRequest("US0378331005", new BigDecimal("1"))))
                .isInstanceOf(PositionNotFoundException.class);
    }

    @Test
    void listPublicPositionsExposesTickerAndCompanyName() {
        PortfolioService service = new PortfolioService(repository);
        PortfolioPosition position = new PortfolioPosition(
                "MSFT", "US5949181045", "Microsoft Corp.", new BigDecimal("5"), new BigDecimal("300.00"), LocalDate.of(2026, 2, 1));
        when(repository.findAll()).thenReturn(List.of(position));

        List<PublicPortfolioPositionView> result = service.listPublicPositions();

        assertThat(result).containsExactly(new PublicPortfolioPositionView("MSFT", "Microsoft Corp."));
    }
}
```

- [ ] **Step 3: Test ausführen, Fehlschlag bestätigen**

Run: `cd backend && mvn test -Dtest=PortfolioServiceTest`
Erwartet: FAIL (Kompilierfehler, `PortfolioService` existiert nicht).

- [ ] **Step 4: PortfolioService implementieren**

`backend/src/main/java/com/valuescreener/portfolio/PortfolioService.java`:

```java
package com.valuescreener.portfolio;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PortfolioService {

    private final PortfolioPositionRepository repository;

    public PortfolioService(PortfolioPositionRepository repository) {
        this.repository = repository;
    }

    public void buy(BuyPositionRequest request) {
        PortfolioPosition position = repository.findByIsin(request.isin()).orElse(null);

        if (position == null) {
            repository.save(new PortfolioPosition(
                    request.ticker(), request.isin(), request.companyName(),
                    request.quantity(), request.entryPrice(), request.purchaseDate()));
            return;
        }

        position.recordPurchase(request.quantity(), request.entryPrice());
        repository.save(position);
    }

    public void sell(SellPositionRequest request) {
        PortfolioPosition position = repository.findByIsin(request.isin())
                .orElseThrow(() -> new PositionNotFoundException(request.isin()));

        position.recordSale(request.quantity());

        if (position.isClosed()) {
            repository.delete(position);
        } else {
            repository.save(position);
        }
    }

    public List<PublicPortfolioPositionView> listPublicPositions() {
        return repository.findAll().stream()
                .map(position -> new PublicPortfolioPositionView(position.getTicker(), position.getCompanyName()))
                .toList();
    }
}
```

- [ ] **Step 5: Test ausführen, Erfolg bestätigen**

Run: `cd backend && mvn test -Dtest=PortfolioServiceTest`
Erwartet: PASS (alle sechs Tests grün).

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/valuescreener/portfolio/BuyPositionRequest.java backend/src/main/java/com/valuescreener/portfolio/SellPositionRequest.java backend/src/main/java/com/valuescreener/portfolio/PublicPortfolioPositionView.java backend/src/main/java/com/valuescreener/portfolio/PositionNotFoundException.java backend/src/main/java/com/valuescreener/portfolio/PortfolioService.java backend/src/test/java/com/valuescreener/portfolio/PortfolioServiceTest.java
git commit -m "feat: add PortfolioService with buy/sell semantics for partial trades"
```

---

## Task 5: PortfolioController (REST-Schicht)

**Files:**
- Create: `backend/src/main/java/com/valuescreener/portfolio/PortfolioController.java`
- Create: `backend/src/test/java/com/valuescreener/portfolio/PortfolioControllerTest.java`

**Interfaces:**
- Consumes: `PortfolioService` (Task 4).
- Produces: `GET /api/portfolio/public` → `List<PublicPortfolioPositionView>`; `POST /api/portfolio` (Body: `BuyPositionRequest` als JSON, identisches JSON-Schema wie das frühere `AddPortfolioPositionRequest`) → `201 Created`; `POST /api/portfolio/sell` (Body: `SellPositionRequest` als JSON) → `204 No Content`. Diese Pfade werden von Task 6 (Security) und dem Frontend (Task 9/10) konsumiert. `POST /api/portfolio` bleibt für Task 6 unverändert (gleicher Pfad, gleiches JSON) — die dortigen Tests brauchen keine Anpassung.

- [ ] **Step 1: Fehlschlagenden Controller-Test schreiben**

`backend/src/test/java/com/valuescreener/portfolio/PortfolioControllerTest.java`:

```java
package com.valuescreener.portfolio;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PortfolioController.class)
@AutoConfigureMockMvc(addFilters = false)
class PortfolioControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PortfolioService portfolioService;

    @Test
    void returnsPublicPositionsAsJson() throws Exception {
        when(portfolioService.listPublicPositions())
                .thenReturn(List.of(new PublicPortfolioPositionView("AAPL", "Apple Inc.")));

        mockMvc.perform(get("/api/portfolio/public"))
                .andExpect(status().isOk())
                .andExpect(content().json("[{\"ticker\":\"AAPL\",\"companyName\":\"Apple Inc.\"}]"));
    }

    @Test
    void createsPositionOnValidRequest() throws Exception {
        mockMvc.perform(post("/api/portfolio")
                        .contentType("application/json")
                        .content("""
                                {"ticker":"AAPL","isin":"US0378331005","companyName":"Apple Inc.","quantity":10,"entryPrice":150.00,"purchaseDate":"2026-01-15"}
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    void rejectsAddPositionWithBlankTicker() throws Exception {
        mockMvc.perform(post("/api/portfolio")
                        .contentType("application/json")
                        .content("""
                                {"ticker":"","isin":"US0378331005","companyName":"Apple Inc.","quantity":10,"entryPrice":150.00,"purchaseDate":"2026-01-15"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sellsPositionOnValidRequest() throws Exception {
        mockMvc.perform(post("/api/portfolio/sell")
                        .contentType("application/json")
                        .content("""
                                {"isin":"US0378331005","quantity":4}
                                """))
                .andExpect(status().isNoContent());
    }
}
```

Hinweis: `@AutoConfigureMockMvc(addFilters = false)` deaktiviert die Spring-Security-Filterkette für diesen fokussierten Web-Slice-Test — Authentifizierung wird erst in Task 6 mit einem vollen `@SpringBootTest` geprüft.

- [ ] **Step 2: Test ausführen, Fehlschlag bestätigen**

Run: `cd backend && mvn test -Dtest=PortfolioControllerTest`
Erwartet: FAIL (Kompilierfehler, `PortfolioController` existiert nicht).

- [ ] **Step 3: Controller implementieren**

`backend/src/main/java/com/valuescreener/portfolio/PortfolioController.java`:

```java
package com.valuescreener.portfolio;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/portfolio")
public class PortfolioController {

    private final PortfolioService portfolioService;

    public PortfolioController(PortfolioService portfolioService) {
        this.portfolioService = portfolioService;
    }

    @GetMapping("/public")
    public List<PublicPortfolioPositionView> listPublicPositions() {
        return portfolioService.listPublicPositions();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void buy(@Valid @RequestBody BuyPositionRequest request) {
        portfolioService.buy(request);
    }

    @PostMapping("/sell")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void sell(@Valid @RequestBody SellPositionRequest request) {
        portfolioService.sell(request);
    }
}
```

- [ ] **Step 4: Test ausführen, Erfolg bestätigen**

Run: `cd backend && mvn test -Dtest=PortfolioControllerTest`
Erwartet: PASS (alle vier Tests grün).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/valuescreener/portfolio/PortfolioController.java backend/src/test/java/com/valuescreener/portfolio/PortfolioControllerTest.java
git commit -m "feat: add PortfolioController REST endpoints"
```

---

## Task 6: Spring-Security-Konfiguration (Single-Admin-Login)

**Files:**
- Create: `backend/src/main/java/com/valuescreener/security/SecurityConfig.java`
- Create: `backend/src/main/java/com/valuescreener/security/AdminPasswordHashGenerator.java`
- Create: `backend/src/test/java/com/valuescreener/security/PortfolioSecurityTest.java`

**Interfaces:**
- Consumes: `app.admin.username`, `app.admin.password-hash` (Konfiguration aus `application.yml`, Task 1).
- Produces: `SecurityFilterChain`-Bean, die `GET /api/portfolio/public` öffentlich lässt und alle anderen Pfade (inkl. `POST /api/portfolio`) per HTTP-Basic-Auth schützt.

- [ ] **Step 1: Fehlschlagenden Security-Test schreiben**

`backend/src/test/java/com/valuescreener/security/PortfolioSecurityTest.java`:

```java
package com.valuescreener.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class PortfolioSecurityTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void adminCredentials(DynamicPropertyRegistry registry) {
        registry.add("app.admin.username", () -> "admin");
        registry.add("app.admin.password-hash", () -> new BCryptPasswordEncoder().encode("test-password"));
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void allowsUnauthenticatedReadOfPublicPortfolio() throws Exception {
        mockMvc.perform(get("/api/portfolio/public"))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsUnauthenticatedWrite() throws Exception {
        mockMvc.perform(post("/api/portfolio")
                        .contentType("application/json")
                        .content("""
                                {"ticker":"AAPL","isin":"US0378331005","companyName":"Apple Inc.","quantity":10,"entryPrice":150.00,"purchaseDate":"2026-01-15"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void acceptsAuthenticatedWrite() throws Exception {
        mockMvc.perform(post("/api/portfolio")
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic("admin", "test-password"))
                        .contentType("application/json")
                        .content("""
                                {"ticker":"AAPL","isin":"US0378331005","companyName":"Apple Inc.","quantity":10,"entryPrice":150.00,"purchaseDate":"2026-01-15"}
                                """))
                .andExpect(status().isCreated());
    }
}
```

- [ ] **Step 2: Test ausführen, Fehlschlag bestätigen**

Run: `cd backend && mvn test -Dtest=PortfolioSecurityTest`
Erwartet: FAIL — ohne `SecurityConfig` sichert die Standard-Spring-Security-Autokonfiguration alle Pfade mit einem zufällig generierten Passwort ab, `allowsUnauthenticatedReadOfPublicPortfolio` und `acceptsAuthenticatedWrite` schlagen fehl.

- [ ] **Step 3: SecurityConfig implementieren**

`backend/src/main/java/com/valuescreener/security/SecurityConfig.java`:

```java
package com.valuescreener.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${app.admin.username}")
    private String adminUsername;

    @Value("${app.admin.password-hash}")
    private String adminPasswordHash;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        UserDetails admin = User.withUsername(adminUsername)
                .password(adminPasswordHash)
                .roles("ADMIN")
                .build();
        return new InMemoryUserDetailsManager(admin);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/portfolio/public").permitAll()
                        .anyRequest().authenticated())
                .httpBasic(basic -> {
                });
        return http.build();
    }
}
```

- [ ] **Step 4: Test ausführen, Erfolg bestätigen**

Run: `cd backend && mvn test -Dtest=PortfolioSecurityTest`
Erwartet: PASS (alle drei Tests grün).

- [ ] **Step 5: Hilfsprogramm zum Erzeugen des Admin-Passwort-Hashes anlegen**

`backend/src/main/java/com/valuescreener/security/AdminPasswordHashGenerator.java`:

```java
package com.valuescreener.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class AdminPasswordHashGenerator {
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: AdminPasswordHashGenerator <plain-text-password>");
            System.exit(1);
        }
        System.out.println(new BCryptPasswordEncoder().encode(args[0]));
    }
}
```

Dieses kleine Hilfsprogramm braucht keinen eigenen Test (keine Verzweigungslogik außer Argument-Prüfung, reiner Aufruf einer bereits getesteten Bibliotheksfunktion). Es wird über `mvn spring-boot:run -Dspring-boot.run.main-class=com.valuescreener.security.AdminPasswordHashGenerator -Dspring-boot.run.arguments=<passwort>` ausgeführt (siehe Task 11, README).

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/valuescreener/security/ backend/src/test/java/com/valuescreener/security/
git commit -m "feat: secure write access with single-admin HTTP Basic auth"
```

---

## Task 7: Frontend-Grundgerüst (Vite + React + TypeScript + Vitest)

**Files:**
- Create: `frontend/package.json`
- Create: `frontend/tsconfig.json`
- Create: `frontend/vite.config.ts`
- Create: `frontend/index.html`
- Create: `frontend/.gitignore`
- Create: `frontend/src/main.tsx`
- Create: `frontend/src/App.tsx`
- Create: `frontend/src/setupTests.ts`
- Create: `frontend/src/App.test.tsx`

**Interfaces:**
- Produces: lauffähige React-App (`App`-Komponente als Default-Export aus `src/App.tsx`), Vitest-Setup mit `jsdom`-Umgebung und `@testing-library/jest-dom`-Matchern, Vite-Dev-Proxy für `/api` → `http://localhost:8080`.

- [ ] **Step 1: `package.json` anlegen**

`frontend/package.json`:

```json
{
  "name": "value-screener-frontend",
  "private": true,
  "version": "0.1.0",
  "type": "module",
  "scripts": {
    "dev": "vite",
    "build": "tsc -b && vite build",
    "test": "vitest run",
    "preview": "vite preview"
  },
  "dependencies": {
    "react": "^18.3.1",
    "react-dom": "^18.3.1"
  },
  "devDependencies": {
    "@testing-library/jest-dom": "^6.5.0",
    "@testing-library/react": "^16.0.1",
    "@types/react": "^18.3.12",
    "@types/react-dom": "^18.3.1",
    "@vitejs/plugin-react": "^4.3.3",
    "jsdom": "^25.0.1",
    "typescript": "^5.6.3",
    "vite": "^5.4.10",
    "vitest": "^2.1.4"
  }
}
```

- [ ] **Step 2: `tsconfig.json` anlegen**

`frontend/tsconfig.json`:

```json
{
  "compilerOptions": {
    "target": "ES2020",
    "useDefineForClassFields": true,
    "lib": ["ES2020", "DOM", "DOM.Iterable"],
    "module": "ESNext",
    "skipLibCheck": true,
    "moduleResolution": "bundler",
    "allowImportingTsExtensions": true,
    "resolveJsonModule": true,
    "isolatedModules": true,
    "noEmit": true,
    "jsx": "react-jsx",
    "strict": true,
    "noUnusedLocals": true,
    "noUnusedParameters": true,
    "noFallthroughCasesInSwitch": true,
    "types": ["vitest/globals", "@testing-library/jest-dom"]
  },
  "include": ["src"]
}
```

- [ ] **Step 3: `vite.config.ts` anlegen**

`frontend/vite.config.ts`:

```ts
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: './src/setupTests.ts',
    globals: true,
  },
})
```

- [ ] **Step 4: `index.html` anlegen**

`frontend/index.html`:

```html
<!doctype html>
<html lang="de">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>Value Screener</title>
  </head>
  <body>
    <div id="root"></div>
    <script type="module" src="/src/main.tsx"></script>
  </body>
</html>
```

- [ ] **Step 5: `.gitignore` anlegen**

`frontend/.gitignore`:

```
node_modules/
dist/
```

- [ ] **Step 6: Abhängigkeiten installieren**

Run: `cd frontend && npm install`
Erwartet: `node_modules/` wird angelegt, `package-lock.json` wird erzeugt.

- [ ] **Step 7: Test-Setup-Datei anlegen**

`frontend/src/setupTests.ts`:

```ts
import '@testing-library/jest-dom/vitest'
```

- [ ] **Step 8: Fehlschlagenden Smoke-Test schreiben**

`frontend/src/App.test.tsx`:

```tsx
import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import App from './App'

describe('App', () => {
  it('renders the app title', () => {
    render(<App />)
    expect(screen.getByRole('heading', { name: 'Value Screener' })).toBeInTheDocument()
  })
})
```

- [ ] **Step 9: Test ausführen, Fehlschlag bestätigen**

Run: `cd frontend && npm test`
Erwartet: FAIL (`./App` existiert nicht).

- [ ] **Step 10: `App.tsx` und `main.tsx` anlegen**

`frontend/src/App.tsx`:

```tsx
function App() {
  return (
    <div>
      <h1>Value Screener</h1>
    </div>
  )
}

export default App
```

`frontend/src/main.tsx`:

```tsx
import React from 'react'
import ReactDOM from 'react-dom/client'
import App from './App'

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
)
```

- [ ] **Step 11: Test ausführen, Erfolg bestätigen**

Run: `cd frontend && npm test`
Erwartet: PASS.

- [ ] **Step 12: Commit**

```bash
git add frontend/
git commit -m "chore: set up Vite/React/TypeScript frontend skeleton"
```

---

## Task 8: Disclaimer-Footer

**Files:**
- Create: `frontend/src/components/Footer.tsx`
- Create: `frontend/src/components/Footer.test.tsx`

**Interfaces:**
- Produces: `Footer`-Komponente (Default-Export aus `src/components/Footer.tsx`), wird in Task 12 von `App.tsx` eingebunden.

- [ ] **Step 1: Fehlschlagenden Test schreiben**

`frontend/src/components/Footer.test.tsx`:

```tsx
import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { Footer } from './Footer'

describe('Footer', () => {
  it('shows the mandatory disclaimer text', () => {
    render(<Footer />)

    const footer = screen.getByRole('contentinfo')
    expect(footer.textContent).toContain('keine Anlageberatung oder Anlageempfehlung dar')
  })
})
```

- [ ] **Step 2: Test ausführen, Fehlschlag bestätigen**

Run: `cd frontend && npm test`
Erwartet: FAIL (`./Footer` existiert nicht).

- [ ] **Step 3: Footer implementieren**

`frontend/src/components/Footer.tsx`:

```tsx
export function Footer() {
  return (
    <footer>
      <p>
        Diese Anwendung ist ein technisches Demonstrationsprojekt. Alle dargestellten Inhalte sind
        automatisiert erzeugte Analysen auf Basis öffentlich zugänglicher Daten und stellen keine
        Anlageberatung oder Anlageempfehlung dar. Sie berücksichtigen keine individuellen
        Anlageziele oder finanziellen Verhältnisse. Jegliche Entscheidung liegt in der
        Verantwortung des Nutzers.
      </p>
    </footer>
  )
}
```

- [ ] **Step 4: Test ausführen, Erfolg bestätigen**

Run: `cd frontend && npm test`
Erwartet: PASS.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/Footer.tsx frontend/src/components/Footer.test.tsx
git commit -m "feat: add mandatory disclaimer footer"
```

---

## Task 9: Öffentliche Portfolio-Ansicht

**Files:**
- Create: `frontend/src/api/portfolioApi.ts`
- Create: `frontend/src/pages/PortfolioPage.tsx`
- Create: `frontend/src/pages/PortfolioPage.test.tsx`

**Interfaces:**
- Produces: `fetchPublicPositions(): Promise<PublicPosition[]>`, `PublicPosition { ticker: string; companyName: string }` (aus `src/api/portfolioApi.ts`, wird in Task 10 erweitert). `PortfolioPage`-Komponente (Named Export), wird in Task 12 von `App.tsx` eingebunden.

- [ ] **Step 1: API-Client anlegen**

`frontend/src/api/portfolioApi.ts`:

```ts
export interface PublicPosition {
  ticker: string
  companyName: string
}

export async function fetchPublicPositions(): Promise<PublicPosition[]> {
  const response = await fetch('/api/portfolio/public')
  if (!response.ok) {
    throw new Error(`Failed to load portfolio: ${response.status}`)
  }
  return response.json()
}
```

- [ ] **Step 2: Fehlschlagenden Test für `PortfolioPage` schreiben**

`frontend/src/pages/PortfolioPage.test.tsx`:

```tsx
import { render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { PortfolioPage } from './PortfolioPage'

describe('PortfolioPage', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn())
  })

  it('renders public tickers and company names returned by the backend', async () => {
    ;(fetch as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({
      ok: true,
      json: async () => [
        { ticker: 'AAPL', companyName: 'Apple Inc.' },
        { ticker: 'MSFT', companyName: 'Microsoft Corp.' },
      ],
    })

    render(<PortfolioPage />)

    expect(await screen.findByText('Apple Inc. (AAPL)')).toBeInTheDocument()
    expect(await screen.findByText('Microsoft Corp. (MSFT)')).toBeInTheDocument()
  })

  it('shows an error message when the request fails', async () => {
    ;(fetch as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({
      ok: false,
      status: 500,
      json: async () => [],
    })

    render(<PortfolioPage />)

    expect(await screen.findByRole('alert')).toHaveTextContent('Failed to load portfolio: 500')
  })
})
```

- [ ] **Step 3: Test ausführen, Fehlschlag bestätigen**

Run: `cd frontend && npm test`
Erwartet: FAIL (`./PortfolioPage` existiert nicht).

- [ ] **Step 4: `PortfolioPage` implementieren**

`frontend/src/pages/PortfolioPage.tsx`:

```tsx
import { useEffect, useState } from 'react'
import { fetchPublicPositions, PublicPosition } from '../api/portfolioApi'

export function PortfolioPage() {
  const [positions, setPositions] = useState<PublicPosition[]>([])
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    fetchPublicPositions()
      .then(setPositions)
      .catch((err: Error) => setError(err.message))
  }, [])

  if (error) {
    return <p role="alert">{error}</p>
  }

  return (
    <section>
      <h1>Mein Portfolio</h1>
      <ul>
        {positions.map((position) => (
          <li key={position.ticker}>{position.companyName} ({position.ticker})</li>
        ))}
      </ul>
    </section>
  )
}
```

- [ ] **Step 5: Test ausführen, Erfolg bestätigen**

Run: `cd frontend && npm test`
Erwartet: PASS.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/api/portfolioApi.ts frontend/src/pages/PortfolioPage.tsx frontend/src/pages/PortfolioPage.test.tsx
git commit -m "feat: display public portfolio ticker list"
```

---

## Task 10: Login + Position hinzufügen

**Files:**
- Modify: `frontend/src/api/portfolioApi.ts`
- Create: `frontend/src/components/LoginForm.tsx`
- Create: `frontend/src/components/AddPositionForm.tsx`
- Create: `frontend/src/components/AddPositionForm.test.tsx`
- Modify: `frontend/src/pages/PortfolioPage.tsx`

**⚠️ Noch nicht mit der Buy/Sell-Änderung abgeglichen:** Task 4/5 heißen jetzt `buy`/`sell` statt
`addPosition` (siehe Nachtrag zu Task 2, `PROJECT-STATUS.md`). Der `POST /api/portfolio`-Pfad und das
JSON-Schema sind identisch geblieben, daher ruft die Funktion unten weiterhin denselben Endpunkt
auf — nur der Funktionsname (`addPosition` → z. B. `buyPosition`) sollte bei Umsetzung dieses Tasks
zur Konsistenz mit dem Backend umbenannt werden. Ein Verkaufs-Formular (`sell`) ist bewusst noch
nicht Teil dieses Tasks — Frontend-Scope für Phase 1 bleibt "Position hinzufügen"; der Sell-Endpunkt
existiert im Backend bereits für spätere Erweiterung.

**Interfaces:**
- Consumes: `PortfolioPage` (Task 9).
- Produces: `Credentials { username: string; password: string }`, `addPosition(credentials, input): Promise<void>` (ergänzt `portfolioApi.ts`; bei Umsetzung ggf. in `buyPosition` umbenennen), `LoginForm`- und `AddPositionForm`-Komponenten.

- [ ] **Step 1: `portfolioApi.ts` um `addPosition` erweitern**

`frontend/src/api/portfolioApi.ts` (vollständiger neuer Inhalt):

```ts
export interface PublicPosition {
  ticker: string
  companyName: string
}

export async function fetchPublicPositions(): Promise<PublicPosition[]> {
  const response = await fetch('/api/portfolio/public')
  if (!response.ok) {
    throw new Error(`Failed to load portfolio: ${response.status}`)
  }
  return response.json()
}

export interface AddPositionInput {
  ticker: string
  isin: string
  companyName: string
  quantity: number
  entryPrice: number
  purchaseDate: string
}

export interface Credentials {
  username: string
  password: string
}

export async function addPosition(credentials: Credentials, input: AddPositionInput): Promise<void> {
  const authHeader = 'Basic ' + btoa(`${credentials.username}:${credentials.password}`)
  const response = await fetch('/api/portfolio', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: authHeader,
    },
    body: JSON.stringify(input),
  })
  if (!response.ok) {
    throw new Error(`Failed to add position: ${response.status}`)
  }
}
```

- [ ] **Step 2: Fehlschlagenden Test für `AddPositionForm` schreiben**

`frontend/src/components/AddPositionForm.test.tsx`:

```tsx
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AddPositionForm } from './AddPositionForm'

describe('AddPositionForm', () => {
  const credentials = { username: 'admin', password: 'secret' }

  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn())
  })

  it('submits the entered position with a basic auth header', async () => {
    ;(fetch as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({ ok: true })
    const onAdded = vi.fn()

    render(<AddPositionForm credentials={credentials} onAdded={onAdded} />)

    fireEvent.change(screen.getByLabelText('Ticker'), { target: { value: 'aapl' } })
    fireEvent.change(screen.getByLabelText('ISIN'), { target: { value: 'US0378331005' } })
    fireEvent.change(screen.getByLabelText('Unternehmensname'), { target: { value: 'Apple Inc.' } })
    fireEvent.change(screen.getByLabelText('Stückzahl'), { target: { value: '10' } })
    fireEvent.change(screen.getByLabelText('Einstiegspreis'), { target: { value: '150' } })
    fireEvent.change(screen.getByLabelText('Kaufdatum'), { target: { value: '2026-01-15' } })
    fireEvent.click(screen.getByRole('button', { name: 'Hinzufügen' }))

    await waitFor(() => expect(onAdded).toHaveBeenCalled())

    const [, options] = (fetch as unknown as ReturnType<typeof vi.fn>).mock.calls[0]
    expect(options.headers.Authorization).toBe('Basic ' + btoa('admin:secret'))
  })
})
```

- [ ] **Step 3: Test ausführen, Fehlschlag bestätigen**

Run: `cd frontend && npm test`
Erwartet: FAIL (`./AddPositionForm` existiert nicht).

- [ ] **Step 4: `LoginForm` implementieren**

`frontend/src/components/LoginForm.tsx`:

```tsx
import { FormEvent, useState } from 'react'
import { Credentials } from '../api/portfolioApi'

interface LoginFormProps {
  onLogin: (credentials: Credentials) => void
}

export function LoginForm({ onLogin }: LoginFormProps) {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    onLogin({ username, password })
  }

  return (
    <form onSubmit={handleSubmit} aria-label="Anmelden">
      <label>
        Benutzername
        <input value={username} onChange={(e) => setUsername(e.target.value)} />
      </label>
      <label>
        Passwort
        <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} />
      </label>
      <button type="submit">Anmelden</button>
    </form>
  )
}
```

- [ ] **Step 5: `AddPositionForm` implementieren**

`frontend/src/components/AddPositionForm.tsx`:

```tsx
import { FormEvent, useState } from 'react'
import { addPosition, Credentials } from '../api/portfolioApi'

interface AddPositionFormProps {
  credentials: Credentials
  onAdded: () => void
}

export function AddPositionForm({ credentials, onAdded }: AddPositionFormProps) {
  const [ticker, setTicker] = useState('')
  const [isin, setIsin] = useState('')
  const [companyName, setCompanyName] = useState('')
  const [quantity, setQuantity] = useState('')
  const [entryPrice, setEntryPrice] = useState('')
  const [purchaseDate, setPurchaseDate] = useState('')
  const [error, setError] = useState<string | null>(null)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    try {
      await addPosition(credentials, {
        ticker,
        isin,
        companyName,
        quantity: Number(quantity),
        entryPrice: Number(entryPrice),
        purchaseDate,
      })
      setTicker('')
      setIsin('')
      setCompanyName('')
      setQuantity('')
      setEntryPrice('')
      setPurchaseDate('')
      onAdded()
    } catch (err) {
      setError((err as Error).message)
    }
  }

  return (
    <form onSubmit={handleSubmit} aria-label="Position hinzufügen">
      <label>
        Ticker
        <input value={ticker} onChange={(e) => setTicker(e.target.value)} />
      </label>
      <label>
        ISIN
        <input value={isin} onChange={(e) => setIsin(e.target.value)} />
      </label>
      <label>
        Unternehmensname
        <input value={companyName} onChange={(e) => setCompanyName(e.target.value)} />
      </label>
      <label>
        Stückzahl
        <input value={quantity} onChange={(e) => setQuantity(e.target.value)} />
      </label>
      <label>
        Einstiegspreis
        <input value={entryPrice} onChange={(e) => setEntryPrice(e.target.value)} />
      </label>
      <label>
        Kaufdatum
        <input type="date" value={purchaseDate} onChange={(e) => setPurchaseDate(e.target.value)} />
      </label>
      <button type="submit">Hinzufügen</button>
      {error && <p role="alert">{error}</p>}
    </form>
  )
}
```

- [ ] **Step 6: Test ausführen, Erfolg bestätigen**

Run: `cd frontend && npm test`
Erwartet: PASS.

- [ ] **Step 7: `PortfolioPage` um Login/Formular erweitern**

`frontend/src/pages/PortfolioPage.tsx` (vollständiger neuer Inhalt):

```tsx
import { useEffect, useState } from 'react'
import { Credentials, fetchPublicPositions, PublicPosition } from '../api/portfolioApi'
import { AddPositionForm } from '../components/AddPositionForm'
import { LoginForm } from '../components/LoginForm'

export function PortfolioPage() {
  const [positions, setPositions] = useState<PublicPosition[]>([])
  const [error, setError] = useState<string | null>(null)
  const [credentials, setCredentials] = useState<Credentials | null>(null)

  function loadPositions() {
    fetchPublicPositions()
      .then(setPositions)
      .catch((err: Error) => setError(err.message))
  }

  useEffect(() => {
    loadPositions()
  }, [])

  if (error) {
    return <p role="alert">{error}</p>
  }

  return (
    <section>
      <h1>Mein Portfolio</h1>
      <ul>
        {positions.map((position) => (
          <li key={position.ticker}>{position.companyName} ({position.ticker})</li>
        ))}
      </ul>
      {credentials ? (
        <AddPositionForm credentials={credentials} onAdded={loadPositions} />
      ) : (
        <LoginForm onLogin={setCredentials} />
      )}
    </section>
  )
}
```

- [ ] **Step 8: Bestehende `PortfolioPage`-Tests erneut ausführen**

Run: `cd frontend && npm test`
Erwartet: PASS — die Tests aus Task 9 prüfen nur die Ticker-Liste bzw. die Fehlermeldung und bleiben gültig, obwohl jetzt zusätzlich ein `LoginForm` gerendert wird.

- [ ] **Step 9: Commit**

```bash
git add frontend/src/api/portfolioApi.ts frontend/src/components/LoginForm.tsx frontend/src/components/AddPositionForm.tsx frontend/src/components/AddPositionForm.test.tsx frontend/src/pages/PortfolioPage.tsx
git commit -m "feat: add login-gated form to add portfolio positions"
```

---

## Task 11: Docker Compose, README, lokale End-to-End-Prüfung

**Files:**
- Create: `docker-compose.yml`
- Create: `README.md`

**Interfaces:**
- Keine neuen Code-Schnittstellen — dieser Task verkabelt die vorherigen Tasks zu einem lokal lauffähigen System.

- [ ] **Step 1: `docker-compose.yml` anlegen**

`docker-compose.yml` (Projekt-Root):

```yaml
services:
  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: value_screener
      POSTGRES_USER: value_screener
      POSTGRES_PASSWORD: value_screener
    ports:
      - "5432:5432"
    volumes:
      - value-screener-postgres-data:/var/lib/postgresql/data

volumes:
  value-screener-postgres-data:
```

- [ ] **Step 2: `README.md` anlegen**

`README.md` (Projekt-Root):

```markdown
# Value Screener

Persönliches Tool für Value-Investing nach Buffett-Prinzipien. Siehe `PROJECT-STATUS.md` und
`docs/superpowers/specs/2026-07-21-value-screener-design.md` für den vollständigen Kontext.

## Phase 1: Projekt-Grundgerüst + Portfolio-Grundfunktion

### Voraussetzungen

- Java 21
- Maven 3.9+
- Node.js 20+
- Docker (für die lokale Postgres-Instanz)

### Lokale Postgres-Datenbank starten

\`\`\`bash
docker compose up -d
\`\`\`

### Admin-Passwort-Hash erzeugen

\`\`\`bash
cd backend
mvn -q spring-boot:run \
  -Dspring-boot.run.main-class=com.valuescreener.security.AdminPasswordHashGenerator \
  -Dspring-boot.run.arguments=<dein-passwort>
\`\`\`

Den ausgegebenen Hash als `ADMIN_PASSWORD_HASH` setzen (siehe unten).

### Backend starten

\`\`\`bash
cd backend
export ADMIN_USERNAME=admin
export ADMIN_PASSWORD_HASH='<erzeugter-hash>'
mvn spring-boot:run
\`\`\`

Backend läuft auf http://localhost:8080.

### Backend-Tests ausführen

\`\`\`bash
cd backend
mvn test
\`\`\`

### Frontend starten

\`\`\`bash
cd frontend
npm install
npm run dev
\`\`\`

Frontend läuft auf http://localhost:5173 und proxyt `/api`-Aufrufe an das Backend.

### Frontend-Tests ausführen

\`\`\`bash
cd frontend
npm test
\`\`\`

### Manuelle End-to-End-Prüfung

1. Backend und Frontend wie oben starten.
2. Browser auf http://localhost:5173 öffnen — "Mein Portfolio" ist leer.
3. Über das Anmeldeformular mit Benutzername/Passwort (wie oben gesetzt) anmelden und eine
   Position hinzufügen (z. B. Ticker `AAPL`, Stückzahl `10`, Einstiegspreis `150`, Kaufdatum
   `2026-01-15`).
4. Die Liste zeigt danach `AAPL` — Stückzahl und Einstiegspreis sind nirgends sichtbar.
5. "Impressum" im Menü öffnen (siehe nächste Aufgabe) und prüfen, dass die Platzhalter-Angaben
   vor einem echten Live-Gang durch echte Daten ersetzt werden.
\`\`\`
```

- [ ] **Step 3: Manuelle End-to-End-Prüfung durchführen**

Die in Step 2 dokumentierten Schritte 1-4 jetzt tatsächlich ausführen und bestätigen, dass sie wie beschrieben funktionieren.

- [ ] **Step 4: Commit**

```bash
git add docker-compose.yml README.md
git commit -m "chore: add docker-compose setup and README with run instructions"
```

---

## Task 12: Impressum-Seite + Navigation

**Files:**
- Create: `frontend/src/pages/ImpressumPage.tsx`
- Create: `frontend/src/pages/ImpressumPage.test.tsx`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.test.tsx`

**Interfaces:**
- Consumes: `Footer` (Task 8), `PortfolioPage` (Task 9/10).
- Produces: vollständige `App`-Komponente mit Navigation zwischen "Portfolio" und "Impressum", jede Ansicht zeigt den `Footer`.

- [ ] **Step 1: Fehlschlagenden Test für `ImpressumPage` schreiben**

`frontend/src/pages/ImpressumPage.test.tsx`:

```tsx
import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { ImpressumPage } from './ImpressumPage'

describe('ImpressumPage', () => {
  it('renders the Impressum heading', () => {
    render(<ImpressumPage />)

    expect(screen.getByRole('heading', { name: 'Impressum' })).toBeInTheDocument()
  })
})
```

- [ ] **Step 2: Test ausführen, Fehlschlag bestätigen**

Run: `cd frontend && npm test`
Erwartet: FAIL (`./ImpressumPage` existiert nicht).

- [ ] **Step 3: `ImpressumPage` implementieren**

`frontend/src/pages/ImpressumPage.tsx`:

```tsx
export function ImpressumPage() {
  return (
    <section>
      <h1>Impressum</h1>
      <p>Angaben gemäß § 5 DDG</p>
      <p>[Name des Betreibers]</p>
      <p>[Erreichbare Anschrift]</p>
      <p>Kontakt: [E-Mail-Adresse]</p>
    </section>
  )
}
```

**Wichtig:** Die eckigen Klammern sind keine offenen Programmieraufgaben, sondern markieren echte
persönliche Daten (Name, Anschrift, E-Mail), die der Nutzer selbst einträgt, bevor die
Anwendung live geht — siehe Design-Spec Abschnitt 9. Vor einem echten öffentlichen Deployment
müssen diese drei Zeilen durch die tatsächlichen Angaben ersetzt werden.

- [ ] **Step 4: Test ausführen, Erfolg bestätigen**

Run: `cd frontend && npm test`
Erwartet: PASS.

- [ ] **Step 5: `App.tsx` um Navigation erweitern**

`frontend/src/App.tsx` (vollständiger neuer Inhalt):

```tsx
import { useState } from 'react'
import { Footer } from './components/Footer'
import { ImpressumPage } from './pages/ImpressumPage'
import { PortfolioPage } from './pages/PortfolioPage'

type View = 'portfolio' | 'impressum'

function App() {
  const [view, setView] = useState<View>('portfolio')

  return (
    <div>
      <header>
        <h1>Value Screener</h1>
        <nav>
          <button onClick={() => setView('portfolio')}>Portfolio</button>
          <button onClick={() => setView('impressum')}>Impressum</button>
        </nav>
      </header>
      {view === 'portfolio' ? <PortfolioPage /> : <ImpressumPage />}
      <Footer />
    </div>
  )
}

export default App
```

- [ ] **Step 6: `App.test.tsx` aktualisieren**

`frontend/src/App.test.tsx` (vollständiger neuer Inhalt):

```tsx
import { render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import App from './App'

describe('App', () => {
  beforeEach(() => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({ ok: true, json: async () => [] }),
    )
  })

  it('renders the app title and portfolio view by default', () => {
    render(<App />)
    expect(screen.getByRole('heading', { name: 'Value Screener' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Mein Portfolio' })).toBeInTheDocument()
  })

  it('switches to the Impressum view', () => {
    render(<App />)
    screen.getByRole('button', { name: 'Impressum' }).click()
    expect(screen.getByRole('heading', { name: 'Impressum' })).toBeInTheDocument()
  })
})
```

Dieser Test stubbt `fetch` global, damit `PortfolioPage` (jetzt Teil von `App`) keinen echten
Netzwerkaufruf auslöst.

- [ ] **Step 7: Test ausführen, Erfolg bestätigen**

Run: `cd frontend && npm test`
Erwartet: PASS (alle Tests im Projekt grün — vollständiger Lauf).

- [ ] **Step 8: Commit**

```bash
git add frontend/src/pages/ImpressumPage.tsx frontend/src/pages/ImpressumPage.test.tsx frontend/src/App.tsx frontend/src/App.test.tsx
git commit -m "feat: add Impressum page and navigation"
```

---

## Task 13: GitHub-Actions-CI-Pipeline

**Files:**
- Create: `.github/workflows/ci.yml`
- Modify: `README.md`

**Interfaces:**
- Konsumiert die Test-Kommandos aus Task 1 (`mvn test`) und Task 7 (`npm test`) — keine neuen Code-Schnittstellen.

**Hinweis zu diesem Task:** Der Implementierer legt nur die Workflow-Datei lokal an und committet sie
lokal. Das Anlegen des GitHub-Repositories, `git remote add` und `git push` sind laut Nutzer-Vorgabe
bewusst **nicht** Teil dieses Tasks — das übernimmt der Nutzer selbst. Der Controller weist im Chat
darauf hin, sobald ein guter Zeitpunkt dafür ist.

- [ ] **Step 1: Workflow-Verzeichnis und Datei anlegen**

`.github/workflows/ci.yml`:

```yaml
name: CI

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

jobs:
  backend:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
          cache: maven
      - name: Run backend tests
        working-directory: backend
        run: mvn -B test

  frontend:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: '20'
          cache: npm
          cache-dependency-path: frontend/package-lock.json
      - name: Install dependencies
        working-directory: frontend
        run: npm ci
      - name: Run frontend tests
        working-directory: frontend
        run: npm test
```

Hinweis für den Implementierer: `mvn test` in der Backend-Job nutzt Testcontainers (siehe Task 1/3/6) —
GitHub-hosted `ubuntu-latest`-Runner haben Docker bereits vorinstalliert und laufend, es ist keine
zusätzliche Konfiguration nötig.

- [ ] **Step 2: Lokal validieren, dass beide Testbefehle isoliert funktionieren**

Run: `cd backend && mvn test`
Erwartet: alle Backend-Tests grün (wie in den vorherigen Tasks bereits bestätigt).

Run: `cd frontend && npm test`
Erwartet: alle Frontend-Tests grün (wie in den vorherigen Tasks bereits bestätigt).

Diese Wiederholung dient nur der Bestätigung, dass die Befehle, die die Pipeline exakt so ausführt,
lokal fehlerfrei laufen — kein neuer Test-Code in diesem Task.

- [ ] **Step 3: README um CI-Hinweis ergänzen**

In `README.md` nach der Überschrift `# Value Screener` einfügen:

```markdown
[![CI](https://github.com/<github-user>/<repo-name>/actions/workflows/ci.yml/badge.svg)](https://github.com/<github-user>/<repo-name>/actions/workflows/ci.yml)
```

**Wichtig:** `<github-user>` und `<repo-name>` sind keine offene Programmieraufgabe, sondern echte
Angaben, die erst feststehen, sobald das GitHub-Repository angelegt ist (Nutzer-Aufgabe, siehe Hinweis
oben). Der Platzhalter bleibt bis dahin bewusst so stehen und wird danach durch die echten Werte ersetzt.

- [ ] **Step 4: Commit**

```bash
git add .github/workflows/ci.yml README.md
git commit -m "ci: add GitHub Actions pipeline for backend and frontend tests"
```

---

## Nach Abschluss von Phase 1

Ergebnis: lauffähiges, getestetes Grundgerüst mit Portfolio-CRUD (anlegen + öffentlich anzeigen),
Single-Admin-Login, Disclaimer-Footer, Impressum-Platzhalter und einer CI-Pipeline, die Backend- und
Frontend-Tests bei jedem Push/PR ausführt. Nächste Schritte laut Design-Spec:

- Phase 2: Screening-Pipeline (Data Provider Client, Screening Engine, AI Assessor) — eigener Plan.
- Phase 3: Snapshot-basierte Änderungserkennung fürs Portfolio + Dedup-Logik — eigener Plan.
- Phase 4: AWS-Deployment (App Runner, RDS, Secrets Manager) — eigener Plan.
- Vor jedem Live-Gang: Platzhalter in `ImpressumPage.tsx` durch echte Daten ersetzen, Risiko 1
  (Datenanbieter-AGB) aus der Design-Spec prüfen.
- Nutzer legt das GitHub-Repository an, fügt es als Remote hinzu und pusht — nicht Teil der
  automatisierten Tasks (siehe Task 13).
