package com.valuescreener.research;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

@Entity
@Table(name = "research_finding",
        uniqueConstraints = @UniqueConstraint(columnNames = {"company_snapshot_id", "criterion_key"}))
public class ResearchFinding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "company_snapshot_id", nullable = false)
    private CompanySnapshot companySnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "criterion_key", nullable = false, length = 40)
    private ResearchCriterion criterionKey;

    @Column(name = "numeric_value", precision = 12, scale = 4)
    private BigDecimal numericValue;

    @Column(name = "boolean_value")
    private Boolean booleanValue;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String claim;

    @Column(name = "source_url", length = 1000)
    private String sourceUrl;

    @Column(name = "as_of_date", nullable = false)
    private LocalDate asOfDate;

    @Version
    @Column(nullable = false)
    private Long version;

    protected ResearchFinding() {
        // JPA
    }

    public ResearchFinding(ResearchCriterion criterionKey, BigDecimal numericValue, Boolean booleanValue,
                            String claim, String sourceUrl, LocalDate asOfDate) {
        this.criterionKey = Objects.requireNonNull(criterionKey, "criterionKey must not be null");
        this.numericValue = numericValue;
        this.booleanValue = booleanValue;
        this.claim = requireNonBlank(claim, "claim");
        this.sourceUrl = sourceUrl;
        this.asOfDate = Objects.requireNonNull(asOfDate, "asOfDate must not be null");
    }

    void applyUpdate(BigDecimal numericValue, Boolean booleanValue, String claim, String sourceUrl,
                      LocalDate asOfDate) {
        this.numericValue = numericValue;
        this.booleanValue = booleanValue;
        this.claim = requireNonBlank(claim, "claim");
        this.sourceUrl = sourceUrl;
        this.asOfDate = Objects.requireNonNull(asOfDate, "asOfDate must not be null");
    }

    void setCompanySnapshot(CompanySnapshot companySnapshot) {
        this.companySnapshot = companySnapshot;
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    public Long getId() { return id; }
    public ResearchCriterion getCriterionKey() { return criterionKey; }
    public BigDecimal getNumericValue() { return numericValue; }
    public Boolean getBooleanValue() { return booleanValue; }
    public String getClaim() { return claim; }
    public String getSourceUrl() { return sourceUrl; }
    public LocalDate getAsOfDate() { return asOfDate; }
}
