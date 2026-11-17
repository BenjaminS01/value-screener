package com.valuescreener.research;

import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.UUID;

/**
 * Storage shape of a {@link ResearchFindingHistory}: pk "SNAPSHOT#" + isin,
 * sk "FHIST#" + timestamp + "#" + index + "#" + uuid.
 *
 * <p>The timestamp is UTC ISO-8601 with exactly 9 fractional digits ({@code 2026-11-12T10:00:00.000000000Z}),
 * so the lexical order of sort keys is the time order. The index is the position of the entry within one save
 * (three digits, zero padded): entries with the same timestamp sort in writing order and are returned in
 * reverse writing order when read newest first (like the former ORDER BY ... DESC, id DESC). The uuid only
 * makes the key unique; entries of separate saves with the very same nanosecond have no defined order.
 */
@DynamoDbBean
public class FindingHistoryItem {

    public static final TableSchema<FindingHistoryItem> SCHEMA = TableSchema.fromBean(FindingHistoryItem.class);
    static final String SK_PREFIX = "FHIST#";

    private static final DateTimeFormatter FIXED_WIDTH_UTC =
            new DateTimeFormatterBuilder().appendInstant(9).toFormatter();

    private String pk;
    private String sk;
    private ResearchCriterion criterionKey;
    private BigDecimal numericValue;
    private Boolean booleanValue;
    private String claim;
    private String sourceUrl;
    private LocalDate asOfDate;
    private Instant replacedAt;
    private HistoryOrigin origin;

    static String partitionKey(String isin) {
        return "SNAPSHOT#" + isin;
    }

    /** Fixed-width UTC timestamp for sort keys (always 30 characters for years 0000-9999). */
    static String sortTimestamp(Instant instant) {
        return FIXED_WIDTH_UTC.format(instant);
    }

    static String sortKey(String prefix, Instant at, int index) {
        return prefix + sortTimestamp(at) + "#" + String.format("%03d", index) + "#" + UUID.randomUUID();
    }

    static FindingHistoryItem from(ResearchFindingHistory h, int index) {
        FindingHistoryItem item = new FindingHistoryItem();
        item.pk = partitionKey(h.getSnapshotIsin());
        item.sk = sortKey(SK_PREFIX, h.getReplacedAt(), index);
        item.criterionKey = h.getCriterionKey();
        item.numericValue = h.getNumericValue();
        item.booleanValue = h.getBooleanValue();
        item.claim = h.getClaim();
        item.sourceUrl = h.getSourceUrl();
        item.asOfDate = h.getAsOfDate();
        item.replacedAt = h.getReplacedAt();
        item.origin = h.getOrigin();
        return item;
    }

    ResearchFindingHistory toDomain() {
        return new ResearchFindingHistory(pk.substring("SNAPSHOT#".length()),
                new ReplacedFinding(criterionKey,
                        numericValue == null ? null : numericValue.setScale(4, java.math.RoundingMode.HALF_UP),
                        booleanValue, claim, sourceUrl, asOfDate),
                replacedAt, origin);
    }

    @DynamoDbPartitionKey
    public String getPk() { return pk; }
    public void setPk(String pk) { this.pk = pk; }

    @DynamoDbSortKey
    public String getSk() { return sk; }
    public void setSk(String sk) { this.sk = sk; }

    public ResearchCriterion getCriterionKey() { return criterionKey; }
    public void setCriterionKey(ResearchCriterion criterionKey) { this.criterionKey = criterionKey; }

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

    public Instant getReplacedAt() { return replacedAt; }
    public void setReplacedAt(Instant replacedAt) { this.replacedAt = replacedAt; }

    public HistoryOrigin getOrigin() { return origin; }
    public void setOrigin(HistoryOrigin origin) { this.origin = origin; }
}
