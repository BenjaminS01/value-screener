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

    @Column(name = "opportunities_and_risks_note", columnDefinition = "TEXT")
    private String opportunitiesAndRisksNote;

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
                            String businessDescription, String moatNote, String opportunitiesAndRisksNote,
                            FinancialStats financialStats, LocalDate asOfDate, Set<String> sources) {
        this.ticker = requireValidTicker(ticker);
        this.isin = requireValidIsin(isin);
        this.companyName = requireNonBlank(companyName, "companyName");
        this.sector = requireNonBlank(sector, "sector");
        this.country = requireNonBlank(country, "country");
        this.businessDescription = requireNonBlank(businessDescription, "businessDescription");
        this.moatNote = moatNote;
        this.opportunitiesAndRisksNote = opportunitiesAndRisksNote;
        this.financialStats = financialStats != null ? financialStats : FinancialStats.empty();
        this.asOfDate = Objects.requireNonNull(asOfDate, "asOfDate must not be null");
        this.sources = new LinkedHashSet<>(sources != null ? sources : Set.of());
        this.updatedFields = computeUpdatedFields(this.moatNote, this.opportunitiesAndRisksNote, this.financialStats);
    }

    public void applyUpdate(String companyName, String sector, String country, String businessDescription,
                             String moatNote, String opportunitiesAndRisksNote, FinancialStats financialStats,
                             LocalDate asOfDate, Set<String> sources) {
        this.companyName = requireNonBlank(companyName, "companyName");
        this.sector = requireNonBlank(sector, "sector");
        this.country = requireNonBlank(country, "country");
        this.businessDescription = requireNonBlank(businessDescription, "businessDescription");
        FinancialStats providedStats = financialStats != null ? financialStats : FinancialStats.empty();
        this.updatedFields = computeUpdatedFields(moatNote, opportunitiesAndRisksNote, providedStats);
        this.moatNote = moatNote != null ? moatNote : this.moatNote;
        this.opportunitiesAndRisksNote = opportunitiesAndRisksNote != null ? opportunitiesAndRisksNote : this.opportunitiesAndRisksNote;
        this.financialStats = this.financialStats.mergedWith(providedStats);
        this.asOfDate = Objects.requireNonNull(asOfDate, "asOfDate must not be null");
        this.sources.addAll(sources != null ? sources : Set.of());
    }

    private static Set<String> computeUpdatedFields(String moatNote, String opportunitiesAndRisksNote, FinancialStats financialStats) {
        Set<String> fields = new LinkedHashSet<>(financialStats.presentFieldNames());
        if (moatNote != null) {
            fields.add("moatNote");
        }
        if (opportunitiesAndRisksNote != null) {
            fields.add("opportunitiesAndRisksNote");
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
    public String getOpportunitiesAndRisksNote() { return opportunitiesAndRisksNote; }
    public FinancialStats getFinancialStats() { return financialStats; }
    public LocalDate getAsOfDate() { return asOfDate; }
    public Set<String> getSources() { return Set.copyOf(sources); }
    public Set<String> getUpdatedFields() { return Set.copyOf(updatedFields); }
}
