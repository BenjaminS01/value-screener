package com.valuescreener.research.history;

import com.valuescreener.research.snapshot.GroupSource;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

import java.time.Instant;

/**
 * Storage shape of a {@link StockGroupHistory}: pk "SNAPSHOT#" + isin, sk "GHIST#" + timestamp + "#" +
 * index + "#" + uuid (timestamp format and ordering as described at {@link FindingHistoryItem}).
 */
@DynamoDbBean
public class GroupHistoryItem {

    public static final TableSchema<GroupHistoryItem> SCHEMA = TableSchema.fromBean(GroupHistoryItem.class);
    static final String SK_PREFIX = "GHIST#";

    private String pk;
    private String sk;
    private String previousGroupKey;
    private GroupSource previousGroupSource;
    private String newGroupKey;
    private GroupSource newGroupSource;
    private Instant changedAt;
    private HistoryOrigin origin;

    public static GroupHistoryItem from(StockGroupHistory h) {
        GroupHistoryItem item = new GroupHistoryItem();
        item.pk = FindingHistoryItem.partitionKey(h.getSnapshotIsin());
        item.sk = FindingHistoryItem.sortKey(SK_PREFIX, h.getChangedAt(), 0);
        item.previousGroupKey = h.getPreviousGroupKey();
        item.previousGroupSource = h.getPreviousSource();
        item.newGroupKey = h.getNewGroupKey();
        item.newGroupSource = h.getNewSource();
        item.changedAt = h.getChangedAt();
        item.origin = h.getOrigin();
        return item;
    }

    StockGroupHistory toDomain() {
        return new StockGroupHistory(pk.substring("SNAPSHOT#".length()), previousGroupKey, previousGroupSource,
                newGroupKey, newGroupSource, changedAt, origin);
    }

    @DynamoDbPartitionKey
    public String getPk() { return pk; }
    public void setPk(String pk) { this.pk = pk; }

    @DynamoDbSortKey
    public String getSk() { return sk; }
    public void setSk(String sk) { this.sk = sk; }

    public String getPreviousGroupKey() { return previousGroupKey; }
    public void setPreviousGroupKey(String previousGroupKey) { this.previousGroupKey = previousGroupKey; }

    public GroupSource getPreviousGroupSource() { return previousGroupSource; }
    public void setPreviousGroupSource(GroupSource previousGroupSource) { this.previousGroupSource = previousGroupSource; }

    public String getNewGroupKey() { return newGroupKey; }
    public void setNewGroupKey(String newGroupKey) { this.newGroupKey = newGroupKey; }

    public GroupSource getNewGroupSource() { return newGroupSource; }
    public void setNewGroupSource(GroupSource newGroupSource) { this.newGroupSource = newGroupSource; }

    public Instant getChangedAt() { return changedAt; }
    public void setChangedAt(Instant changedAt) { this.changedAt = changedAt; }

    public HistoryOrigin getOrigin() { return origin; }
    public void setOrigin(HistoryOrigin origin) { this.origin = origin; }
}
