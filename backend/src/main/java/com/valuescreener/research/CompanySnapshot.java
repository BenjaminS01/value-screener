package com.valuescreener.research;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
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

    @OneToMany(mappedBy = "companySnapshot", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ResearchFinding> findings = new ArrayList<>();

    @Version
    @Column(nullable = false)
    private Long version;

    protected CompanySnapshot() {
        // JPA
    }

    public CompanySnapshot(String ticker, String isin, String companyName, String sector, String country,
                            String businessDescription) {
        this.ticker = requireValidTicker(ticker);
        this.isin = requireValidIsin(isin);
        this.companyName = requireNonBlank(companyName, "companyName");
        this.sector = requireNonBlank(sector, "sector");
        this.country = requireNonBlank(country, "country");
        this.businessDescription = requireNonBlank(businessDescription, "businessDescription");
    }

    public void applyUpdate(String companyName, String sector, String country, String businessDescription) {
        this.companyName = requireNonBlank(companyName, "companyName");
        this.sector = requireNonBlank(sector, "sector");
        this.country = requireNonBlank(country, "country");
        this.businessDescription = requireNonBlank(businessDescription, "businessDescription");
    }

    public void upsertFinding(ResearchCriterion criterionKey, BigDecimal numericValue, Boolean booleanValue,
                               String claim, String sourceUrl, LocalDate asOfDate) {
        for (ResearchFinding finding : findings) {
            if (finding.getCriterionKey() == criterionKey) {
                finding.applyUpdate(numericValue, booleanValue, claim, sourceUrl, asOfDate);
                return;
            }
        }
        ResearchFinding finding = new ResearchFinding(criterionKey, numericValue, booleanValue, claim,
                sourceUrl, asOfDate);
        finding.setCompanySnapshot(this);
        findings.add(finding);
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
    public List<ResearchFinding> getFindings() { return List.copyOf(findings); }
}
