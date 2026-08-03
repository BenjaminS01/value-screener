# Session Research Collection — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give the backend a place to store company research facts gathered in ad-hoc Claude Code
sessions (Section 4 of the design spec) — a validated, authenticated write/read API backed by a new
`CompanySnapshot` domain, so research done outside the app stops living only in chat transcripts.

**Architecture:** One new DDD-inspired module, `com.valuescreener.research`, added to the existing
`backend/` Spring Boot app (same module as `portfolio` — not the separately deployed
`company-research-agent/` service). Follows exactly the layering already established by the
`portfolio` module: JPA entity with validating constructor and domain behavior → repository → service
→ REST controller → controller-scoped exception handler. A small `FinancialStats` value object
(JPA `@Embeddable`) groups the eight optional numeric/boolean facts (P/E, P/B, ROE, …) so entity
constructors and update methods don't balloon into 15+ positional parameters, and so "merge new facts
into old ones without losing what's already known" has one place to live and be unit-tested.

**Tech Stack:** Java 21, Spring Boot 3.3.5, Spring Data JPA, Spring Security (HTTP Basic, already
configured), PostgreSQL via Flyway migration, JUnit 5 + AssertJ + Mockito + Testcontainers (all already
on the `backend/` classpath — no new dependencies needed).

## Global Constraints

- **Git is entirely the user's responsibility.** Per this project's established workflow (see
  `PROJECT-STATUS.md`), no subagent or controller runs `git add`/`git commit`/any other write git
  command. Every task ends with "report completion, give the user the exact commit command" instead of
  an actual commit step — this deviates from this skill's usual template on purpose.
- **Explain the concept before each task.** Also per this project's established workflow, the
  controller narrates what the task does and why (junior/mid-level Java level) before dispatching it.
  Each task below has a "Concept" paragraph written for exactly that purpose — read it aloud/paraphrase
  it, don't skip straight to the file diff.
- **TDD throughout:** every code task is red → green → (refactor if needed), no exceptions.
- **DDD-inspired, pragmatic** (per the original design spec, Section 7.1): one module (`research`) per
  business concept, no CQRS/event sourcing, small focused classes.
- Package for all new code: `com.valuescreener.research` (mirrors `com.valuescreener.portfolio`),
  inside the existing `backend/` Maven module — **not** `company-research-agent/`.
- `sector` and `country` are plain, validated-non-blank `String` fields for this increment, **not** an
  enum — the older redesign's 11-value GICS-style taxonomy is a different, not-yet-reconciled document
  (design spec Section 7); adopting it here would be scope creep this plan explicitly avoids.
- All money/ratio fields use `BigDecimal` (never `double`/`float`), consistent with `PortfolioPosition`.
- ISIN validation reuses the exact same regex (`[A-Z]{2}[A-Z0-9]{9}[0-9]`) and normalization
  (trim + uppercase) as `PortfolioPosition` — duplicated deliberately, not extracted into a shared
  utility. Two call sites don't yet justify a cross-module abstraction; flagged as a candidate
  extraction if a third module ever needs it.
- No `SecurityConfig` changes needed: the existing `.anyRequest().authenticated()` rule already covers
  every new `/api/research/**` endpoint. No public (unauthenticated) read endpoint exists yet in this
  increment — the design's eventual public dashboard is explicitly deferred (design spec, Section 2:
  "UI details beyond the sketch").
- **Out of scope for this plan** (see design spec Sections 2 and 7): the Deep Research
  ("Tiefenrecherche") context-injection change to `company-research-agent/`'s `ResearchPromptBuilder`,
  any frontend/dashboard work, and reconciliation with `2026-07-30-screening-cost-redesign-design.md`.
  This plan only builds the storage/API side that a Claude Code research session writes to.

## File Structure

New files, all under `backend/src/main/java/com/valuescreener/research/` unless noted:

| File | Responsibility |
|---|---|
| `FinancialStats.java` | `@Embeddable` value object: the 8 optional numeric/boolean facts + merge logic |
| `CompanySnapshot.java` | JPA entity: identity/validation + `applyUpdate` domain method |
| `CompanySnapshotRepository.java` | Spring Data repository, `findByIsin` |
| `CompanySnapshotNotFoundException.java` | Thrown by the service when an ISIN isn't found |
| `UpsertCompanySnapshotRequest.java` | Inbound DTO (validated record) |
| `CompanySnapshotView.java` | Outbound DTO (record) + `from(CompanySnapshot)` mapper |
| `CompanySnapshotService.java` | Upsert-by-ISIN, list, get-by-ISIN |
| `CompanySnapshotController.java` | `/api/research/snapshots` REST endpoints |
| `CompanySnapshotExceptionHandler.java` | Maps domain exceptions to HTTP status codes |
| `backend/src/main/resources/db/migration/V3__create_company_snapshot.sql` | Schema |

Test files mirror this 1:1 under `backend/src/test/java/com/valuescreener/research/`, plus one
security test under `backend/src/test/java/com/valuescreener/security/ResearchSnapshotSecurityTest.java`
(same package as the existing `PortfolioSecurityTest`, for consistency).

---

### Task 1: `FinancialStats` value object

**Concept:** A `CompanySnapshot` collects up to 8 optional numeric/boolean facts (P/E, P/B, ROE,
debt/equity, current-year margin, current-year FCF sign, current-year net income vs. prior year,
insider ownership). Passing all 8 as loose constructor parameters everywhere would make every
constructor and update call unreadable. Grouping them into one small class — a JPA **embeddable value
object** — gives them one name, one place for the "merge new facts into old ones, keep what's not
re-provided" rule (the core design requirement from the spec: a session that doesn't re-find the P/E
this time must not erase the P/E from last time), and one place to answer "which of these fields did
this pass actually provide" (needed for the `updatedFields` freshness tracking in Task 2).

**Files:**
- Create: `backend/src/main/java/com/valuescreener/research/FinancialStats.java`
- Test: `backend/src/test/java/com/valuescreener/research/FinancialStatsTest.java`

**Interfaces:**
- Produces: `FinancialStats(BigDecimal peRatio, BigDecimal pbRatio, BigDecimal roePercent, BigDecimal debtToEquity, BigDecimal currentYearNetMarginPercent, Boolean currentYearFcfPositive, Boolean currentYearNetIncomeIncreasedYoy, BigDecimal insiderOwnershipPercent)` constructor; `static FinancialStats empty()`; `FinancialStats mergedWith(FinancialStats update)`; `Set<String> presentFieldNames()`; getters for all 8 fields. Consumed by `CompanySnapshot` (Task 2).

- [ ] **Step 1: Write the failing tests**

```java
package com.valuescreener.research;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class FinancialStatsTest {

    @Test
    void mergedWithPrefersNewValuesWhenPresent() {
        FinancialStats existing = new FinancialStats(
                new BigDecimal("15.0"), new BigDecimal("2.0"), new BigDecimal("18.0"), new BigDecimal("0.5"),
                new BigDecimal("12.0"), Boolean.TRUE, Boolean.TRUE, new BigDecimal("5.0"));
        FinancialStats update = new FinancialStats(
                new BigDecimal("16.0"), null, null, null, null, null, null, null);

        FinancialStats merged = existing.mergedWith(update);

        assertThat(merged.getPeRatio()).isEqualByComparingTo("16.0");
        assertThat(merged.getPbRatio()).isEqualByComparingTo("2.0");
        assertThat(merged.getRoePercent()).isEqualByComparingTo("18.0");
    }

    @Test
    void mergedWithKeepsExistingValuesWhenUpdateFieldIsNull() {
        FinancialStats existing = new FinancialStats(
                new BigDecimal("15.0"), null, null, null, null, null, null, null);
        FinancialStats update = FinancialStats.empty();

        FinancialStats merged = existing.mergedWith(update);

        assertThat(merged.getPeRatio()).isEqualByComparingTo("15.0");
    }

    @Test
    void presentFieldNamesReturnsOnlyNonNullFields() {
        FinancialStats stats = new FinancialStats(
                new BigDecimal("15.0"), null, new BigDecimal("18.0"), null, null, null, null, null);

        assertThat(stats.presentFieldNames()).containsExactlyInAnyOrder("peRatio", "roePercent");
    }

    @Test
    void presentFieldNamesReturnsEmptySetWhenAllFieldsAreNull() {
        assertThat(FinancialStats.empty().presentFieldNames()).isEmpty();
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd backend && mvn -q -Dtest=FinancialStatsTest test`
Expected: FAIL — `FinancialStats` does not exist (compile error).

- [ ] **Step 3: Write the implementation**

```java
package com.valuescreener.research;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Set;

@Embeddable
public class FinancialStats {

    @Column(name = "pe_ratio")
    private BigDecimal peRatio;

    @Column(name = "pb_ratio")
    private BigDecimal pbRatio;

    @Column(name = "roe_percent")
    private BigDecimal roePercent;

    @Column(name = "debt_to_equity")
    private BigDecimal debtToEquity;

    @Column(name = "current_year_net_margin_percent")
    private BigDecimal currentYearNetMarginPercent;

    @Column(name = "current_year_fcf_positive")
    private Boolean currentYearFcfPositive;

    @Column(name = "current_year_net_income_increased_yoy")
    private Boolean currentYearNetIncomeIncreasedYoy;

    @Column(name = "insider_ownership_percent")
    private BigDecimal insiderOwnershipPercent;

    protected FinancialStats() {
        // JPA
    }

    public FinancialStats(BigDecimal peRatio, BigDecimal pbRatio, BigDecimal roePercent, BigDecimal debtToEquity,
                           BigDecimal currentYearNetMarginPercent, Boolean currentYearFcfPositive,
                           Boolean currentYearNetIncomeIncreasedYoy, BigDecimal insiderOwnershipPercent) {
        this.peRatio = peRatio;
        this.pbRatio = pbRatio;
        this.roePercent = roePercent;
        this.debtToEquity = debtToEquity;
        this.currentYearNetMarginPercent = currentYearNetMarginPercent;
        this.currentYearFcfPositive = currentYearFcfPositive;
        this.currentYearNetIncomeIncreasedYoy = currentYearNetIncomeIncreasedYoy;
        this.insiderOwnershipPercent = insiderOwnershipPercent;
    }

    public static FinancialStats empty() {
        return new FinancialStats(null, null, null, null, null, null, null, null);
    }

    public FinancialStats mergedWith(FinancialStats update) {
        return new FinancialStats(
                update.peRatio != null ? update.peRatio : this.peRatio,
                update.pbRatio != null ? update.pbRatio : this.pbRatio,
                update.roePercent != null ? update.roePercent : this.roePercent,
                update.debtToEquity != null ? update.debtToEquity : this.debtToEquity,
                update.currentYearNetMarginPercent != null ? update.currentYearNetMarginPercent : this.currentYearNetMarginPercent,
                update.currentYearFcfPositive != null ? update.currentYearFcfPositive : this.currentYearFcfPositive,
                update.currentYearNetIncomeIncreasedYoy != null ? update.currentYearNetIncomeIncreasedYoy : this.currentYearNetIncomeIncreasedYoy,
                update.insiderOwnershipPercent != null ? update.insiderOwnershipPercent : this.insiderOwnershipPercent);
    }

    public Set<String> presentFieldNames() {
        Set<String> fields = new LinkedHashSet<>();
        if (peRatio != null) fields.add("peRatio");
        if (pbRatio != null) fields.add("pbRatio");
        if (roePercent != null) fields.add("roePercent");
        if (debtToEquity != null) fields.add("debtToEquity");
        if (currentYearNetMarginPercent != null) fields.add("currentYearNetMarginPercent");
        if (currentYearFcfPositive != null) fields.add("currentYearFcfPositive");
        if (currentYearNetIncomeIncreasedYoy != null) fields.add("currentYearNetIncomeIncreasedYoy");
        if (insiderOwnershipPercent != null) fields.add("insiderOwnershipPercent");
        return fields;
    }

    public BigDecimal getPeRatio() { return peRatio; }
    public BigDecimal getPbRatio() { return pbRatio; }
    public BigDecimal getRoePercent() { return roePercent; }
    public BigDecimal getDebtToEquity() { return debtToEquity; }
    public BigDecimal getCurrentYearNetMarginPercent() { return currentYearNetMarginPercent; }
    public Boolean getCurrentYearFcfPositive() { return currentYearFcfPositive; }
    public Boolean getCurrentYearNetIncomeIncreasedYoy() { return currentYearNetIncomeIncreasedYoy; }
    public BigDecimal getInsiderOwnershipPercent() { return insiderOwnershipPercent; }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd backend && mvn -q -Dtest=FinancialStatsTest test`
Expected: PASS (4/4).

- [ ] **Step 5: Report completion — do not commit**

Summarize the diff for the user and give them this exact command to run themselves:

```bash
git add backend/src/main/java/com/valuescreener/research/FinancialStats.java backend/src/test/java/com/valuescreener/research/FinancialStatsTest.java
git commit -m "feat(research): add FinancialStats value object with merge semantics"
```

---

### Task 2: `CompanySnapshot` entity

**Concept:** This is the actual "one row per researched company" domain object, following the exact
same shape as `PortfolioPosition`: a private no-arg constructor for JPA, a validating public
constructor, and a domain method (`applyUpdate`, mirroring `recordPurchase`/`recordSale`) that encodes
the business rule from the design spec — a later research pass **overwrites** identity/description
fields with fresher values, but only overwrites the optional `FinancialStats` fields and `moatNote`
**when the new pass actually found them**, otherwise keeps what's already known. It also computes
`updatedFields` on every write — the set of optional fields *this particular pass* provided — which is
exactly the "which data was updated" freshness signal from the design spec's UI requirement.

**Files:**
- Create: `backend/src/main/java/com/valuescreener/research/CompanySnapshot.java`
- Test: `backend/src/test/java/com/valuescreener/research/CompanySnapshotTest.java`

**Interfaces:**
- Consumes: `FinancialStats` (Task 1) — `FinancialStats.empty()`, `mergedWith`, `presentFieldNames()`.
- Produces: `CompanySnapshot(String ticker, String isin, String companyName, String sector, String country, String businessDescription, String moatNote, FinancialStats financialStats, LocalDate asOfDate, Set<String> sources)` constructor; `void applyUpdate(String companyName, String sector, String country, String businessDescription, String moatNote, FinancialStats financialStats, LocalDate asOfDate, Set<String> sources)`; getters `getId/getTicker/getIsin/getCompanyName/getSector/getCountry/getBusinessDescription/getMoatNote/getFinancialStats/getAsOfDate/getSources/getUpdatedFields`. Consumed by `CompanySnapshotRepository` (Task 3) and `CompanySnapshotService` (Task 4).

- [ ] **Step 1: Write the failing tests**

```java
package com.valuescreener.research;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompanySnapshotTest {

    private static CompanySnapshot minimalSnapshot() {
        return new CompanySnapshot(
                "aapl", "us0378331005", "Apple Inc.", "Information Technology", "USA",
                "Designs and sells consumer electronics.", null, null,
                LocalDate.of(2026, 8, 1), Set.of("https://example.com/aapl-key-stats"));
    }

    @Test
    void createsSnapshotWithNormalizedUppercaseTickerAndIsin() {
        CompanySnapshot snapshot = minimalSnapshot();

        assertThat(snapshot.getTicker()).isEqualTo("AAPL");
        assertThat(snapshot.getIsin()).isEqualTo("US0378331005");
        assertThat(snapshot.getSources()).containsExactly("https://example.com/aapl-key-stats");
    }

    @Test
    void rejectsBlankTicker() {
        assertThatThrownBy(() -> new CompanySnapshot(
                "  ", "US0378331005", "Apple Inc.", "Information Technology", "USA",
                "Designs and sells consumer electronics.", null, null, LocalDate.of(2026, 8, 1), Set.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ticker");
    }

    @Test
    void rejectsMalformedIsin() {
        assertThatThrownBy(() -> new CompanySnapshot(
                "AAPL", "NOT-AN-ISIN", "Apple Inc.", "Information Technology", "USA",
                "Designs and sells consumer electronics.", null, null, LocalDate.of(2026, 8, 1), Set.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("isin");
    }

    @Test
    void rejectsBlankBusinessDescription() {
        assertThatThrownBy(() -> new CompanySnapshot(
                "AAPL", "US0378331005", "Apple Inc.", "Information Technology", "USA",
                "  ", null, null, LocalDate.of(2026, 8, 1), Set.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("businessDescription");
    }

    @Test
    void constructorRecordsWhichOptionalFieldsWerePresent() {
        FinancialStats stats = new FinancialStats(new BigDecimal("28.0"), null, null, null, null, null, null, null);
        CompanySnapshot snapshot = new CompanySnapshot(
                "AAPL", "US0378331005", "Apple Inc.", "Information Technology", "USA",
                "Designs and sells consumer electronics.", "Strong brand moat.", stats,
                LocalDate.of(2026, 8, 1), Set.of());

        assertThat(snapshot.getUpdatedFields()).containsExactlyInAnyOrder("peRatio", "moatNote");
    }

    @Test
    void applyUpdateOverwritesProvidedFieldsAndKeepsExistingOnesWhenNotProvided() {
        FinancialStats initialStats = new FinancialStats(new BigDecimal("28.0"), new BigDecimal("40.0"), null, null, null, null, null, null);
        CompanySnapshot snapshot = new CompanySnapshot(
                "AAPL", "US0378331005", "Apple Inc.", "Information Technology", "USA",
                "Designs and sells consumer electronics.", "Strong brand moat.", initialStats,
                LocalDate.of(2026, 8, 1), Set.of("https://example.com/first-source"));

        FinancialStats freshPeOnly = new FinancialStats(new BigDecimal("29.5"), null, null, null, null, null, null, null);
        snapshot.applyUpdate("Apple Inc.", "Information Technology", "USA",
                "Designs and sells consumer electronics and services.", null, freshPeOnly,
                LocalDate.of(2026, 8, 5), Set.of("https://example.com/second-source"));

        assertThat(snapshot.getFinancialStats().getPeRatio()).isEqualByComparingTo("29.5");
        assertThat(snapshot.getFinancialStats().getPbRatio()).isEqualByComparingTo("40.0");
        assertThat(snapshot.getMoatNote()).isEqualTo("Strong brand moat.");
        assertThat(snapshot.getAsOfDate()).isEqualTo(LocalDate.of(2026, 8, 5));
        assertThat(snapshot.getSources()).containsExactlyInAnyOrder(
                "https://example.com/first-source", "https://example.com/second-source");
    }

    @Test
    void applyUpdateTracksOnlyFieldsProvidedInThisCall() {
        CompanySnapshot snapshot = minimalSnapshot();

        FinancialStats freshRoeOnly = new FinancialStats(null, null, new BigDecimal("35.0"), null, null, null, null, null);
        snapshot.applyUpdate("Apple Inc.", "Information Technology", "USA",
                "Designs and sells consumer electronics.", null, freshRoeOnly, LocalDate.of(2026, 8, 5), Set.of());

        assertThat(snapshot.getUpdatedFields()).containsExactly("roePercent");
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd backend && mvn -q -Dtest=CompanySnapshotTest test`
Expected: FAIL — `CompanySnapshot` does not exist (compile error).

- [ ] **Step 3: Write the implementation**

```java
package com.valuescreener.research;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

@Entity
@Table(name = "company_snapshot")
public class CompanySnapshot {

    private static final Pattern ISIN_PATTERN = Pattern.compile("[A-Z]{2}[A-Z0-9]{9}[0-9]");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10)
    private String ticker;

    @Column(nullable = false, unique = true, length = 12)
    private String isin;

    @Column(name = "company_name", nullable = false, length = 200)
    private String companyName;

    @Column(nullable = false, length = 100)
    private String sector;

    @Column(nullable = false, length = 100)
    private String country;

    @Column(name = "business_description", nullable = false, columnDefinition = "TEXT")
    private String businessDescription;

    @Column(name = "moat_note", columnDefinition = "TEXT")
    private String moatNote;

    @Embedded
    private FinancialStats financialStats;

    @Column(name = "as_of_date", nullable = false)
    private LocalDate asOfDate;

    @ElementCollection
    @CollectionTable(name = "company_snapshot_source", joinColumns = @JoinColumn(name = "company_snapshot_id"))
    @Column(name = "source", nullable = false, length = 500)
    private Set<String> sources = new LinkedHashSet<>();

    @ElementCollection
    @CollectionTable(name = "company_snapshot_updated_field", joinColumns = @JoinColumn(name = "company_snapshot_id"))
    @Column(name = "field_name", nullable = false, length = 100)
    private Set<String> updatedFields = new LinkedHashSet<>();

    @Version
    @Column(nullable = false)
    private Long version;

    protected CompanySnapshot() {
        // JPA
    }

    public CompanySnapshot(String ticker, String isin, String companyName, String sector, String country,
                            String businessDescription, String moatNote, FinancialStats financialStats,
                            LocalDate asOfDate, Set<String> sources) {
        this.ticker = requireValidTicker(ticker);
        this.isin = requireValidIsin(isin);
        this.companyName = requireNonBlank(companyName, "companyName");
        this.sector = requireNonBlank(sector, "sector");
        this.country = requireNonBlank(country, "country");
        this.businessDescription = requireNonBlank(businessDescription, "businessDescription");
        this.moatNote = moatNote;
        this.financialStats = financialStats != null ? financialStats : FinancialStats.empty();
        this.asOfDate = Objects.requireNonNull(asOfDate, "asOfDate must not be null");
        this.sources = new LinkedHashSet<>(sources != null ? sources : Set.of());
        this.updatedFields = computeUpdatedFields(this.moatNote, this.financialStats);
    }

    public void applyUpdate(String companyName, String sector, String country, String businessDescription,
                             String moatNote, FinancialStats financialStats, LocalDate asOfDate, Set<String> sources) {
        this.companyName = requireNonBlank(companyName, "companyName");
        this.sector = requireNonBlank(sector, "sector");
        this.country = requireNonBlank(country, "country");
        this.businessDescription = requireNonBlank(businessDescription, "businessDescription");
        FinancialStats providedStats = financialStats != null ? financialStats : FinancialStats.empty();
        this.updatedFields = computeUpdatedFields(moatNote, providedStats);
        this.moatNote = moatNote != null ? moatNote : this.moatNote;
        this.financialStats = this.financialStats.mergedWith(providedStats);
        this.asOfDate = Objects.requireNonNull(asOfDate, "asOfDate must not be null");
        this.sources.addAll(sources != null ? sources : Set.of());
    }

    private static Set<String> computeUpdatedFields(String moatNote, FinancialStats financialStats) {
        Set<String> fields = new LinkedHashSet<>(financialStats.presentFieldNames());
        if (moatNote != null) {
            fields.add("moatNote");
        }
        return fields;
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

    public Long getId() { return id; }
    public String getTicker() { return ticker; }
    public String getIsin() { return isin; }
    public String getCompanyName() { return companyName; }
    public String getSector() { return sector; }
    public String getCountry() { return country; }
    public String getBusinessDescription() { return businessDescription; }
    public String getMoatNote() { return moatNote; }
    public FinancialStats getFinancialStats() { return financialStats; }
    public LocalDate getAsOfDate() { return asOfDate; }
    public Set<String> getSources() { return Set.copyOf(sources); }
    public Set<String> getUpdatedFields() { return Set.copyOf(updatedFields); }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd backend && mvn -q -Dtest=CompanySnapshotTest test`
Expected: PASS (6/6).

- [ ] **Step 5: Report completion — do not commit**

```bash
git add backend/src/main/java/com/valuescreener/research/CompanySnapshot.java backend/src/test/java/com/valuescreener/research/CompanySnapshotTest.java
git commit -m "feat(research): add CompanySnapshot entity with merge-on-update semantics"
```

---

### Task 3: Flyway migration + `CompanySnapshotRepository`

**Concept:** `backend/` uses `spring.jpa.hibernate.ddl-auto: validate` (see `application.yml`) — Hibernate
never generates schema itself, it only checks the mapped entity matches what Flyway already created.
So the entity from Task 2 needs a hand-written migration before it can be persisted at all. This task
also adds the repository (one-liner, same shape as `PortfolioPositionRepository`) and a
Testcontainers-backed test proving both the migration and the `@Version` optimistic-locking column
actually work against a real Postgres — not just compile.

**Files:**
- Create: `backend/src/main/resources/db/migration/V3__create_company_snapshot.sql`
- Create: `backend/src/main/java/com/valuescreener/research/CompanySnapshotRepository.java`
- Test: `backend/src/test/java/com/valuescreener/research/CompanySnapshotRepositoryTest.java`

**Interfaces:**
- Consumes: `CompanySnapshot` (Task 2) — full constructor and `applyUpdate`.
- Produces: `CompanySnapshotRepository extends JpaRepository<CompanySnapshot, Long>` with
  `Optional<CompanySnapshot> findByIsin(String isin)`. Consumed by `CompanySnapshotService` (Task 4).

- [ ] **Step 1: Write the failing test**

```java
package com.valuescreener.research;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CompanySnapshotRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private CompanySnapshotRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    private static CompanySnapshot newSnapshot() {
        return new CompanySnapshot(
                "AAPL", "US0378331005", "Apple Inc.", "Information Technology", "USA",
                "Designs and sells consumer electronics.", null, null,
                LocalDate.of(2026, 8, 1), Set.of("https://example.com/aapl-key-stats"));
    }

    @Test
    void savesAndFindsSnapshotByIsin() {
        repository.save(newSnapshot());

        assertThat(repository.findByIsin("US0378331005")).isPresent();
    }

    @Test
    void returnsEmptyWhenIsinNotFound() {
        assertThat(repository.findByIsin("US0000000000")).isEmpty();
    }

    @Test
    void rejectsSaveOfAStaleCopyAfterAConcurrentUpdate() {
        CompanySnapshot saved = repository.saveAndFlush(newSnapshot());
        Long id = saved.getId();

        CompanySnapshot staleCopy = repository.findById(id).orElseThrow();
        entityManager.detach(staleCopy);

        CompanySnapshot freshCopy = repository.findById(id).orElseThrow();
        freshCopy.applyUpdate("Apple Inc.", "Information Technology", "USA",
                "Designs and sells consumer electronics.", null, null, LocalDate.of(2026, 8, 5), Set.of());
        repository.saveAndFlush(freshCopy);

        staleCopy.applyUpdate("Apple Inc.", "Information Technology", "USA",
                "Designs and sells consumer electronics.", null, null, LocalDate.of(2026, 8, 6), Set.of());
        assertThatThrownBy(() -> repository.saveAndFlush(staleCopy))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd backend && mvn -q -Dtest=CompanySnapshotRepositoryTest test`
Expected: FAIL — `CompanySnapshotRepository` does not exist, and even once it does, Flyway has no
migration for `company_snapshot` yet.

- [ ] **Step 3: Write the migration**

```sql
CREATE TABLE company_snapshot (
    id BIGSERIAL PRIMARY KEY,
    ticker VARCHAR(10) NOT NULL,
    isin VARCHAR(12) NOT NULL UNIQUE,
    company_name VARCHAR(200) NOT NULL,
    sector VARCHAR(100) NOT NULL,
    country VARCHAR(100) NOT NULL,
    business_description TEXT NOT NULL,
    moat_note TEXT,
    pe_ratio NUMERIC(12, 4),
    pb_ratio NUMERIC(12, 4),
    roe_percent NUMERIC(12, 4),
    debt_to_equity NUMERIC(12, 4),
    current_year_net_margin_percent NUMERIC(12, 4),
    current_year_fcf_positive BOOLEAN,
    current_year_net_income_increased_yoy BOOLEAN,
    insider_ownership_percent NUMERIC(12, 4),
    as_of_date DATE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE company_snapshot_source (
    company_snapshot_id BIGINT NOT NULL REFERENCES company_snapshot(id) ON DELETE CASCADE,
    source VARCHAR(500) NOT NULL,
    PRIMARY KEY (company_snapshot_id, source)
);

CREATE TABLE company_snapshot_updated_field (
    company_snapshot_id BIGINT NOT NULL REFERENCES company_snapshot(id) ON DELETE CASCADE,
    field_name VARCHAR(100) NOT NULL,
    PRIMARY KEY (company_snapshot_id, field_name)
);
```

Save as `backend/src/main/resources/db/migration/V3__create_company_snapshot.sql` (`V3` because `V1`
and `V2` already exist for `portfolio_position`).

- [ ] **Step 4: Write the repository**

```java
package com.valuescreener.research;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CompanySnapshotRepository extends JpaRepository<CompanySnapshot, Long> {
    Optional<CompanySnapshot> findByIsin(String isin);
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `cd backend && mvn -q -Dtest=CompanySnapshotRepositoryTest test`
Expected: PASS (3/3).

- [ ] **Step 6: Report completion — do not commit**

```bash
git add backend/src/main/resources/db/migration/V3__create_company_snapshot.sql backend/src/main/java/com/valuescreener/research/CompanySnapshotRepository.java backend/src/test/java/com/valuescreener/research/CompanySnapshotRepositoryTest.java
git commit -m "feat(research): add company_snapshot schema and repository"
```

---

### Task 4: DTOs + `CompanySnapshotService`

**Concept:** The service is the upsert-by-ISIN logic (same pattern as `PortfolioService.buy`): look
the ISIN up, create a new `CompanySnapshot` if it's genuinely new, otherwise call `applyUpdate` on the
existing one. The two DTOs are the HTTP-facing shapes — `UpsertCompanySnapshotRequest` is what a
Claude Code session sends in (with Bean Validation annotations so a malformed request never reaches
domain code), and `CompanySnapshotView` is what comes back out, including the `updatedFields` and
`sources` the caller needs to know "did my data actually get recorded."

**Files:**
- Create: `backend/src/main/java/com/valuescreener/research/UpsertCompanySnapshotRequest.java`
- Create: `backend/src/main/java/com/valuescreener/research/CompanySnapshotView.java`
- Create: `backend/src/main/java/com/valuescreener/research/CompanySnapshotNotFoundException.java`
- Create: `backend/src/main/java/com/valuescreener/research/CompanySnapshotService.java`
- Test: `backend/src/test/java/com/valuescreener/research/CompanySnapshotServiceTest.java`

**Interfaces:**
- Consumes: `CompanySnapshot`/`FinancialStats` (Tasks 1–2), `CompanySnapshotRepository` (Task 3).
- Produces: `UpsertCompanySnapshotRequest` (record, fields listed in Step 3); `CompanySnapshotView`
  (record, with `static CompanySnapshotView from(CompanySnapshot)`); `CompanySnapshotNotFoundException`;
  `CompanySnapshotService(CompanySnapshotRepository repository)` with
  `CompanySnapshotView upsert(UpsertCompanySnapshotRequest request)`,
  `List<CompanySnapshotView> listAll()`, `CompanySnapshotView getByIsin(String isin)`. Consumed by
  `CompanySnapshotController` (Task 5).

- [ ] **Step 1: Write the failing tests**

```java
package com.valuescreener.research;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanySnapshotServiceTest {

    @Mock
    private CompanySnapshotRepository repository;

    @Test
    void upsertCreatesNewSnapshotWhenIsinUnknown() {
        CompanySnapshotService service = new CompanySnapshotService(repository);
        when(repository.findByIsin("US0378331005")).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        UpsertCompanySnapshotRequest request = new UpsertCompanySnapshotRequest(
                "aapl", "US0378331005", "Apple Inc.", "Information Technology", "USA",
                "Designs and sells consumer electronics.", "Strong brand moat.",
                new BigDecimal("28.0"), null, null, null, null, null, null, null,
                LocalDate.of(2026, 8, 1), Set.of("https://example.com/aapl-key-stats"));

        CompanySnapshotView result = service.upsert(request);

        assertThat(result.ticker()).isEqualTo("AAPL");
        assertThat(result.peRatio()).isEqualByComparingTo("28.0");
        assertThat(result.updatedFields()).containsExactlyInAnyOrder("peRatio", "moatNote");
    }

    @Test
    void upsertMergesIntoExistingSnapshotWhenIsinKnown() {
        CompanySnapshotService service = new CompanySnapshotService(repository);
        FinancialStats existingStats = new FinancialStats(new BigDecimal("28.0"), new BigDecimal("40.0"), null, null, null, null, null, null);
        CompanySnapshot existing = new CompanySnapshot(
                "AAPL", "US0378331005", "Apple Inc.", "Information Technology", "USA",
                "Designs and sells consumer electronics.", "Strong brand moat.", existingStats,
                LocalDate.of(2026, 8, 1), Set.of("https://example.com/first-source"));
        when(repository.findByIsin("US0378331005")).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        UpsertCompanySnapshotRequest request = new UpsertCompanySnapshotRequest(
                "aapl", "US0378331005", "Apple Inc.", "Information Technology", "USA",
                "Designs and sells consumer electronics and services.", null,
                new BigDecimal("29.5"), null, null, null, null, null, null, null,
                LocalDate.of(2026, 8, 5), Set.of("https://example.com/second-source"));

        CompanySnapshotView result = service.upsert(request);

        assertThat(result.peRatio()).isEqualByComparingTo("29.5");
        assertThat(result.pbRatio()).isEqualByComparingTo("40.0");
        assertThat(result.sources()).containsExactlyInAnyOrder(
                "https://example.com/first-source", "https://example.com/second-source");

        ArgumentCaptor<CompanySnapshot> captor = ArgumentCaptor.forClass(CompanySnapshot.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue()).isSameAs(existing);
    }

    @Test
    void listAllReturnsAllSnapshots() {
        CompanySnapshotService service = new CompanySnapshotService(repository);
        CompanySnapshot snapshot = new CompanySnapshot(
                "AAPL", "US0378331005", "Apple Inc.", "Information Technology", "USA",
                "Designs and sells consumer electronics.", null, null,
                LocalDate.of(2026, 8, 1), Set.of());
        when(repository.findAll()).thenReturn(List.of(snapshot));

        List<CompanySnapshotView> result = service.listAll();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).ticker()).isEqualTo("AAPL");
    }

    @Test
    void getByIsinReturnsSnapshotWhenFound() {
        CompanySnapshotService service = new CompanySnapshotService(repository);
        CompanySnapshot snapshot = new CompanySnapshot(
                "AAPL", "US0378331005", "Apple Inc.", "Information Technology", "USA",
                "Designs and sells consumer electronics.", null, null,
                LocalDate.of(2026, 8, 1), Set.of());
        when(repository.findByIsin("US0378331005")).thenReturn(Optional.of(snapshot));

        assertThat(service.getByIsin("US0378331005").isin()).isEqualTo("US0378331005");
    }

    @Test
    void getByIsinThrowsWhenNotFound() {
        CompanySnapshotService service = new CompanySnapshotService(repository);
        when(repository.findByIsin("US0378331005")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getByIsin("US0378331005"))
                .isInstanceOf(CompanySnapshotNotFoundException.class);
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd backend && mvn -q -Dtest=CompanySnapshotServiceTest test`
Expected: FAIL — none of `UpsertCompanySnapshotRequest`, `CompanySnapshotView`,
`CompanySnapshotNotFoundException`, `CompanySnapshotService` exist yet.

- [ ] **Step 3: Write the DTOs, exception, and service**

```java
package com.valuescreener.research;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

public record UpsertCompanySnapshotRequest(
        @NotBlank String ticker,
        @NotBlank String isin,
        @NotBlank String companyName,
        @NotBlank String sector,
        @NotBlank String country,
        @NotBlank String businessDescription,
        String moatNote,
        BigDecimal peRatio,
        BigDecimal pbRatio,
        BigDecimal roePercent,
        BigDecimal debtToEquity,
        BigDecimal currentYearNetMarginPercent,
        Boolean currentYearFcfPositive,
        Boolean currentYearNetIncomeIncreasedYoy,
        BigDecimal insiderOwnershipPercent,
        @NotNull LocalDate asOfDate,
        Set<String> sources
) {
}
```

```java
package com.valuescreener.research;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

public record CompanySnapshotView(
        Long id,
        String ticker,
        String isin,
        String companyName,
        String sector,
        String country,
        String businessDescription,
        String moatNote,
        BigDecimal peRatio,
        BigDecimal pbRatio,
        BigDecimal roePercent,
        BigDecimal debtToEquity,
        BigDecimal currentYearNetMarginPercent,
        Boolean currentYearFcfPositive,
        Boolean currentYearNetIncomeIncreasedYoy,
        BigDecimal insiderOwnershipPercent,
        LocalDate asOfDate,
        Set<String> sources,
        Set<String> updatedFields
) {
    public static CompanySnapshotView from(CompanySnapshot snapshot) {
        FinancialStats stats = snapshot.getFinancialStats();
        return new CompanySnapshotView(
                snapshot.getId(), snapshot.getTicker(), snapshot.getIsin(), snapshot.getCompanyName(),
                snapshot.getSector(), snapshot.getCountry(), snapshot.getBusinessDescription(), snapshot.getMoatNote(),
                stats.getPeRatio(), stats.getPbRatio(), stats.getRoePercent(), stats.getDebtToEquity(),
                stats.getCurrentYearNetMarginPercent(), stats.getCurrentYearFcfPositive(),
                stats.getCurrentYearNetIncomeIncreasedYoy(), stats.getInsiderOwnershipPercent(),
                snapshot.getAsOfDate(), snapshot.getSources(), snapshot.getUpdatedFields());
    }
}
```

```java
package com.valuescreener.research;

public class CompanySnapshotNotFoundException extends RuntimeException {

    public CompanySnapshotNotFoundException(String isin) {
        super("no company snapshot found for isin " + isin);
    }
}
```

```java
package com.valuescreener.research;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
public class CompanySnapshotService {

    private final CompanySnapshotRepository repository;

    public CompanySnapshotService(CompanySnapshotRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public CompanySnapshotView upsert(UpsertCompanySnapshotRequest request) {
        FinancialStats stats = new FinancialStats(
                request.peRatio(), request.pbRatio(), request.roePercent(), request.debtToEquity(),
                request.currentYearNetMarginPercent(), request.currentYearFcfPositive(),
                request.currentYearNetIncomeIncreasedYoy(), request.insiderOwnershipPercent());
        Set<String> sources = request.sources() != null ? request.sources() : Set.of();

        CompanySnapshot snapshot = repository.findByIsin(request.isin()).orElse(null);
        if (snapshot == null) {
            snapshot = new CompanySnapshot(request.ticker(), request.isin(), request.companyName(),
                    request.sector(), request.country(), request.businessDescription(), request.moatNote(),
                    stats, request.asOfDate(), sources);
        } else {
            snapshot.applyUpdate(request.companyName(), request.sector(), request.country(),
                    request.businessDescription(), request.moatNote(), stats, request.asOfDate(), sources);
        }
        return CompanySnapshotView.from(repository.save(snapshot));
    }

    public List<CompanySnapshotView> listAll() {
        return repository.findAll().stream().map(CompanySnapshotView::from).toList();
    }

    public CompanySnapshotView getByIsin(String isin) {
        return repository.findByIsin(isin)
                .map(CompanySnapshotView::from)
                .orElseThrow(() -> new CompanySnapshotNotFoundException(isin));
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd backend && mvn -q -Dtest=CompanySnapshotServiceTest test`
Expected: PASS (5/5).

- [ ] **Step 5: Report completion — do not commit**

```bash
git add backend/src/main/java/com/valuescreener/research/UpsertCompanySnapshotRequest.java backend/src/main/java/com/valuescreener/research/CompanySnapshotView.java backend/src/main/java/com/valuescreener/research/CompanySnapshotNotFoundException.java backend/src/main/java/com/valuescreener/research/CompanySnapshotService.java backend/src/test/java/com/valuescreener/research/CompanySnapshotServiceTest.java
git commit -m "feat(research): add CompanySnapshotService upsert/list/get-by-isin"
```

---

### Task 5: `CompanySnapshotController` + exception handler

**Concept:** The thin HTTP layer — three endpoints (`POST` to upsert, `GET` to list, `GET /{isin}` for
one), plus a controller-scoped `@RestControllerAdvice` that maps the service's exceptions to HTTP
status codes, exactly like `PortfolioExceptionHandler` does for `PortfolioController`. This is the
endpoint a Claude Code research session will actually call.

**Files:**
- Create: `backend/src/main/java/com/valuescreener/research/CompanySnapshotController.java`
- Create: `backend/src/main/java/com/valuescreener/research/CompanySnapshotExceptionHandler.java`
- Test: `backend/src/test/java/com/valuescreener/research/CompanySnapshotControllerTest.java`

**Interfaces:**
- Consumes: `CompanySnapshotService`, `UpsertCompanySnapshotRequest`, `CompanySnapshotView`,
  `CompanySnapshotNotFoundException` (Task 4).
- Produces: `POST /api/research/snapshots`, `GET /api/research/snapshots`,
  `GET /api/research/snapshots/{isin}`. Consumed by Task 6's security test and, later, by whatever
  calls this endpoint from a Claude Code session (out of scope here — see Global Constraints).

- [ ] **Step 1: Write the failing tests**

```java
package com.valuescreener.research;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CompanySnapshotController.class)
@AutoConfigureMockMvc(addFilters = false)
class CompanySnapshotControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CompanySnapshotService service;

    private static CompanySnapshotView sampleView() {
        return new CompanySnapshotView(
                1L, "AAPL", "US0378331005", "Apple Inc.", "Information Technology", "USA",
                "Designs and sells consumer electronics.", "Strong brand moat.",
                new BigDecimal("28.0"), null, null, null, null, null, null, null,
                LocalDate.of(2026, 8, 1), Set.of("https://example.com/aapl-key-stats"), Set.of("peRatio", "moatNote"));
    }

    @Test
    void upsertsSnapshotOnValidRequest() throws Exception {
        when(service.upsert(any())).thenReturn(sampleView());

        mockMvc.perform(post("/api/research/snapshots")
                        .contentType("application/json")
                        .content("""
                                {"ticker":"AAPL","isin":"US0378331005","companyName":"Apple Inc.",
                                 "sector":"Information Technology","country":"USA",
                                 "businessDescription":"Designs and sells consumer electronics.",
                                 "asOfDate":"2026-08-01"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticker").value("AAPL"));
    }

    @Test
    void rejectsUpsertWithBlankBusinessDescription() throws Exception {
        mockMvc.perform(post("/api/research/snapshots")
                        .contentType("application/json")
                        .content("""
                                {"ticker":"AAPL","isin":"US0378331005","companyName":"Apple Inc.",
                                 "sector":"Information Technology","country":"USA",
                                 "businessDescription":"","asOfDate":"2026-08-01"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listsAllSnapshots() throws Exception {
        when(service.listAll()).thenReturn(List.of(sampleView()));

        mockMvc.perform(get("/api/research/snapshots"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ticker").value("AAPL"));
    }

    @Test
    void getsSnapshotByIsin() throws Exception {
        when(service.getByIsin("US0378331005")).thenReturn(sampleView());

        mockMvc.perform(get("/api/research/snapshots/US0378331005"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isin").value("US0378331005"));
    }

    @Test
    void gettingUnknownIsinReturnsNotFound() throws Exception {
        doThrow(new CompanySnapshotNotFoundException("US0378331005"))
                .when(service).getByIsin("US0378331005");

        mockMvc.perform(get("/api/research/snapshots/US0378331005"))
                .andExpect(status().isNotFound());
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd backend && mvn -q -Dtest=CompanySnapshotControllerTest test`
Expected: FAIL — `CompanySnapshotController` does not exist.

- [ ] **Step 3: Write the controller and exception handler**

```java
package com.valuescreener.research;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/research/snapshots")
public class CompanySnapshotController {

    private final CompanySnapshotService service;

    public CompanySnapshotController(CompanySnapshotService service) {
        this.service = service;
    }

    @PostMapping
    public CompanySnapshotView upsert(@Valid @RequestBody UpsertCompanySnapshotRequest request) {
        return service.upsert(request);
    }

    @GetMapping
    public List<CompanySnapshotView> listAll() {
        return service.listAll();
    }

    @GetMapping("/{isin}")
    public CompanySnapshotView getByIsin(@PathVariable String isin) {
        return service.getByIsin(isin);
    }
}
```

```java
package com.valuescreener.research;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = CompanySnapshotController.class)
public class CompanySnapshotExceptionHandler {

    @ExceptionHandler(CompanySnapshotNotFoundException.class)
    public ResponseEntity<Void> handleNotFound(CompanySnapshotNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Void> handleIllegalArgument(IllegalArgumentException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd backend && mvn -q -Dtest=CompanySnapshotControllerTest test`
Expected: PASS (5/5).

- [ ] **Step 5: Report completion — do not commit**

```bash
git add backend/src/main/java/com/valuescreener/research/CompanySnapshotController.java backend/src/main/java/com/valuescreener/research/CompanySnapshotExceptionHandler.java backend/src/test/java/com/valuescreener/research/CompanySnapshotControllerTest.java
git commit -m "feat(research): add CompanySnapshotController REST endpoints"
```

---

### Task 6: Security verification test

**Concept:** `SecurityConfig` already declares `.anyRequest().authenticated()` for anything that isn't
explicitly listed as `permitAll()` — so `/api/research/**` should already require the admin login
without touching `SecurityConfig` at all. This task doesn't change production code; it writes the test
that proves that's actually true (the same discipline `PortfolioSecurityTest` already applies to the
portfolio endpoints), which matters specifically because the design spec's whole reason for choosing
this write path over direct database access was to keep it behind exactly this kind of authentication
boundary (design spec Section 8).

**Files:**
- Test: `backend/src/test/java/com/valuescreener/security/ResearchSnapshotSecurityTest.java`

**Interfaces:**
- Consumes: `CompanySnapshotController` (Task 5), existing `SecurityConfig`. Produces nothing new —
  verification only.

- [ ] **Step 1: Write the failing test**

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
class ResearchSnapshotSecurityTest {

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
    void rejectsUnauthenticatedRead() throws Exception {
        mockMvc.perform(get("/api/research/snapshots"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsUnauthenticatedWrite() throws Exception {
        mockMvc.perform(post("/api/research/snapshots")
                        .contentType("application/json")
                        .content("""
                                {"ticker":"AAPL","isin":"US0378331005","companyName":"Apple Inc.",
                                 "sector":"Information Technology","country":"USA",
                                 "businessDescription":"Designs and sells consumer electronics.",
                                 "asOfDate":"2026-08-01"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void acceptsAuthenticatedWrite() throws Exception {
        mockMvc.perform(post("/api/research/snapshots")
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic("admin", "test-password"))
                        .contentType("application/json")
                        .content("""
                                {"ticker":"AAPL","isin":"US0378331005","companyName":"Apple Inc.",
                                 "sector":"Information Technology","country":"USA",
                                 "businessDescription":"Designs and sells consumer electronics.",
                                 "asOfDate":"2026-08-01"}
                                """))
                .andExpect(status().isOk());
    }
}
```

- [ ] **Step 2: Run the test**

Run: `cd backend && mvn -q -Dtest=ResearchSnapshotSecurityTest test`
Expected: PASS (3/3) on the first try — this confirms the existing `.anyRequest().authenticated()`
default already covers the new endpoints, with no `SecurityConfig` change required. If any test fails
here, that's a real gap to fix in `SecurityConfig`, not a test bug — do not weaken the test to make it
pass.

- [ ] **Step 3: Report completion — do not commit**

```bash
git add backend/src/test/java/com/valuescreener/security/ResearchSnapshotSecurityTest.java
git commit -m "test(security): verify research snapshot endpoints require authentication"
```

---

### Task 7: Update `PROJECT-STATUS.md`

**Concept:** This project keeps a running status document so a new session can pick up without
re-deriving context. This task adds one entry documenting what got built, closing the loop on the
"stop Schritt 4, do this instead" decision from this session.

**Files:**
- Modify: `PROJECT-STATUS.md`

**Interfaces:** None (documentation only).

- [ ] **Step 1: Add a status entry**

Insert a new subsection near the top (after the existing "Company Research Agent: Worktree-Phase
beendet" section), written in German to match the rest of the document:

```markdown
## Session Research Collection: Backend-Grundlage umgesetzt (2026-08-0X)

Neues Modul `com.valuescreener.research` in `backend/` umgesetzt: `CompanySnapshot`-Aggregat
(Identität + `FinancialStats`-Value-Object für die 8 optionalen Kennzahlen, Merge-Semantik — ein
späterer Recherche-Pass überschreibt nur Felder, die er tatsächlich liefert, verliert also nie zuvor
gefundene Daten), Repository, Service (Upsert nach ISIN), REST-Controller unter
`/api/research/snapshots` (POST/GET/GET-by-ISIN), abgesichert durch die bestehende
Single-User-Auth (kein `SecurityConfig`-Änderung nötig, `.anyRequest().authenticated()` deckt es
bereits ab, per eigenem Test verifiziert). Design:
[`docs/superpowers/specs/2026-08-03-session-research-collection-design.md`](docs/superpowers/specs/2026-08-03-session-research-collection-design.md).
Plan: [`docs/superpowers/plans/2026-08-03-session-research-collection.md`](docs/superpowers/plans/2026-08-03-session-research-collection.md).

**Bewusste Entscheidung (siehe Design-Doku):** Schritt 4 (Live-Call-Test des Company Research Agent)
wird zugunsten dieses Themas pausiert — dieser Baustein braucht keine bezahlte KI-Nutzung zum Testen
(reines JUnit/Testcontainers), im Gegensatz zum bisherigen Redesign-Pfad.

**Noch offen:** Deep-Research-Kontext-Injection (`ResearchPromptBuilder` in `company-research-agent/`
mit `CompanySnapshot`-Daten füttern), Dashboard/Frontend, Abgleich mit
`2026-07-30-screening-cost-redesign-design.md` — alles bewusst außerhalb dieses Plans (siehe
Design-Doku Abschnitt 7).
```

Replace `2026-08-0X` with the actual date this task is executed.

- [ ] **Step 2: Report completion — do not commit**

```bash
git add PROJECT-STATUS.md
git commit -m "docs: record session research collection backend milestone"
```

---

## Self-Review Notes

- **Spec coverage:** Design spec Section 4 (Session Collection fields, freshness tracking, Admin-API
  write path) → Tasks 1–5. Section 8 (Admin API over MCP/SQL for prompt-injection safety) → Task 6
  verifies the auth boundary that decision depends on. Sections 5/7 (Deep Research context injection,
  UI) are explicitly out of scope per Global Constraints, matching the design spec's own Section 2
  scope cut.
- **Placeholder scan:** none — every step has real, complete code.
- **Type consistency:** `FinancialStats` field names (`peRatio`, `pbRatio`, `roePercent`,
  `debtToEquity`, `currentYearNetMarginPercent`, `currentYearFcfPositive`,
  `currentYearNetIncomeIncreasedYoy`, `insiderOwnershipPercent`) are identical across Tasks 1, 2, 4, 5.
  `CompanySnapshot.applyUpdate`'s parameter order matches its constructor's tail (minus ticker/isin,
  which never change) across Tasks 2–4.
