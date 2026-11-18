package com.valuescreener.thesis;

import com.valuescreener.persistence.ItemConditions;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;
import software.amazon.awssdk.enhanced.dynamodb.extensions.annotations.DynamoDbVersionAttribute;

import java.time.Instant;

/** Storage shape of an {@link InvestmentThesis}: pk "THESIS", sk "ISIN#" + isin. */
@DynamoDbBean
public class ThesisItem {

    public static final TableSchema<ThesisItem> SCHEMA = TableSchema.fromBean(ThesisItem.class);
    static final String PK = "THESIS";

    private String pk;
    private String sk;
    private String isin;
    private String whyInteresting;
    private String expectedDevelopment;
    private String exitCriteria;
    private ThesisStatus status;
    private String statusReason;
    private Instant updatedAt;
    private Long version;

    public static String sortKey(String isin) {
        return "ISIN#" + isin;
    }

    public static ThesisItem from(InvestmentThesis t) {
        ThesisItem item = new ThesisItem();
        item.pk = PK;
        item.sk = sortKey(t.getIsin());
        item.isin = t.getIsin();
        item.whyInteresting = t.getWhyInteresting();
        item.expectedDevelopment = t.getExpectedDevelopment();
        item.exitCriteria = t.getExitCriteria();
        item.status = t.getStatus();
        item.statusReason = t.getStatusReason();
        item.updatedAt = t.getUpdatedAt();
        item.version = t.getVersion();
        return item;
    }

    public InvestmentThesis toDomain() {
        return InvestmentThesis.restore(isin, whyInteresting, expectedDevelopment, exitCriteria, status,
                statusReason, updatedAt, version);
    }

    /** Call after the item was written: the stored version is one higher than the one the object carried. */
    public static void markSaved(InvestmentThesis t) {
        t.setVersion(ItemConditions.nextVersion(t.getVersion()));
    }

    @DynamoDbPartitionKey
    public String getPk() { return pk; }
    public void setPk(String pk) { this.pk = pk; }

    @DynamoDbSortKey
    public String getSk() { return sk; }
    public void setSk(String sk) { this.sk = sk; }

    public String getIsin() { return isin; }
    public void setIsin(String isin) { this.isin = isin; }

    public String getWhyInteresting() { return whyInteresting; }
    public void setWhyInteresting(String whyInteresting) { this.whyInteresting = whyInteresting; }

    public String getExpectedDevelopment() { return expectedDevelopment; }
    public void setExpectedDevelopment(String expectedDevelopment) { this.expectedDevelopment = expectedDevelopment; }

    public String getExitCriteria() { return exitCriteria; }
    public void setExitCriteria(String exitCriteria) { this.exitCriteria = exitCriteria; }

    public ThesisStatus getStatus() { return status; }
    public void setStatus(ThesisStatus status) { this.status = status; }

    public String getStatusReason() { return statusReason; }
    public void setStatusReason(String statusReason) { this.statusReason = statusReason; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    @DynamoDbVersionAttribute
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
