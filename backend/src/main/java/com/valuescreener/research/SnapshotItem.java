package com.valuescreener.research;

import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Storage shape of a {@link CompanySnapshot}: pk "SNAPSHOT", sk "ISIN#" + isin. The findings are embedded as a
 * map keyed by criterion name. The version is maintained by the repository (it writes the transaction itself,
 * so the enhanced client's version extension is not involved).
 */
@DynamoDbBean
public class SnapshotItem {

    public static final TableSchema<SnapshotItem> SCHEMA = TableSchema.fromBean(SnapshotItem.class);
    static final String PK = "SNAPSHOT";
    static final String SK_PREFIX = "ISIN#";

    private String pk;
    private String sk;
    private String isin;
    private String ticker;
    private String companyName;
    private String country;
    private String businessDescription;
    private String groupKey;
    private GroupSource groupSource;
    private Long version;
    private Map<String, FindingData> findings = new LinkedHashMap<>();

    public static String sortKey(String isin) {
        return SK_PREFIX + isin;
    }

    /** @param version the version to store (the repository passes the next one) */
    static SnapshotItem from(CompanySnapshot s, long version) {
        SnapshotItem item = new SnapshotItem();
        item.pk = PK;
        item.sk = sortKey(s.getIsin());
        item.isin = s.getIsin();
        item.ticker = s.getTicker();
        item.companyName = s.getCompanyName();
        item.country = s.getCountry();
        item.businessDescription = s.getBusinessDescription();
        item.groupKey = s.getGroupKey();
        item.groupSource = s.getGroupSource();
        item.version = version;
        for (ResearchFinding f : s.getFindings()) {
            item.findings.put(f.getCriterionKey().name(), FindingData.from(f));
        }
        return item;
    }

    CompanySnapshot toDomain() {
        List<ResearchFinding> restored = findings.entrySet().stream()
                .map(e -> e.getValue().toDomain(ResearchCriterion.valueOf(e.getKey())))
                .sorted(Comparator.comparing(ResearchFinding::getCriterionKey))
                .toList();
        return CompanySnapshot.restore(ticker, isin, companyName, country, businessDescription, groupKey,
                groupSource, restored, version);
    }

    /** One embedded finding. */
    @DynamoDbBean
    public static class FindingData {
        private BigDecimal numericValue;
        private Boolean booleanValue;
        private String claim;
        private String sourceUrl;
        private LocalDate asOfDate;

        static FindingData from(ResearchFinding f) {
            FindingData data = new FindingData();
            data.numericValue = f.getNumericValue();
            data.booleanValue = f.getBooleanValue();
            data.claim = f.getClaim();
            data.sourceUrl = f.getSourceUrl();
            data.asOfDate = f.getAsOfDate();
            return data;
        }

        ResearchFinding toDomain(ResearchCriterion criterion) {
            return new ResearchFinding(criterion, numericValue, booleanValue, claim, sourceUrl, asOfDate);
        }

        public BigDecimal getNumericValue() { return numericValue; }
        public void setNumericValue(BigDecimal numericValue) { this.numericValue = numericValue; }

        public Boolean getBooleanValue() { return booleanValue; }
        public void setBooleanValue(Boolean booleanValue) { this.booleanValue = booleanValue; }

        public String getClaim() { return claim; }
        public void setClaim(String claim) { this.claim = claim; }

        public String getSourceUrl() { return sourceUrl; }
        public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }

        public LocalDate getAsOfDate() { return asOfDate; }
        public void setAsOfDate(LocalDate asOfDate) { this.asOfDate = asOfDate; }
    }

    @DynamoDbPartitionKey
    public String getPk() { return pk; }
    public void setPk(String pk) { this.pk = pk; }

    @DynamoDbSortKey
    public String getSk() { return sk; }
    public void setSk(String sk) { this.sk = sk; }

    public String getIsin() { return isin; }
    public void setIsin(String isin) { this.isin = isin; }

    public String getTicker() { return ticker; }
    public void setTicker(String ticker) { this.ticker = ticker; }

    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }

    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }

    public String getBusinessDescription() { return businessDescription; }
    public void setBusinessDescription(String businessDescription) { this.businessDescription = businessDescription; }

    public String getGroupKey() { return groupKey; }
    public void setGroupKey(String groupKey) { this.groupKey = groupKey; }

    public GroupSource getGroupSource() { return groupSource; }
    public void setGroupSource(GroupSource groupSource) { this.groupSource = groupSource; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    public Map<String, FindingData> getFindings() { return findings; }
    public void setFindings(Map<String, FindingData> findings) { this.findings = findings; }
}
