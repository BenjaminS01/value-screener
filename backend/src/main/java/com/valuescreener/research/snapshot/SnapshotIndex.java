package com.valuescreener.research.snapshot;

import com.valuescreener.persistence.HistoryQuery;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.Put;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tiny per-snapshot index items in their own partition: {@code pk="SNAPINDEX", sk="ISIN#"+isin}, attribute
 * {@code groupKey} (absent when ungrouped). They are written in the same transaction as every snapshot save, so
 * group counts and the portfolio's group lookup read ~100 bytes per snapshot instead of the 5-10 KB snapshot
 * items. Existing data without index items is filled by {@link SnapshotIndexBackfill}.
 */
final class SnapshotIndex {

    static final String PK = "SNAPINDEX";
    static final String GROUP_KEY = "groupKey";

    private SnapshotIndex() {
    }

    static Map<String, AttributeValue> item(String isin, String groupKey) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("pk", AttributeValue.fromS(PK));
        item.put("sk", AttributeValue.fromS(SnapshotItem.sortKey(isin)));
        if (groupKey != null) {
            item.put(GROUP_KEY, AttributeValue.fromS(groupKey));
        }
        return item;
    }

    /** Unconditional put: the snapshot item's own condition already guards the transaction. */
    static TransactWriteItem put(String tableName, String isin, String groupKey) {
        return TransactWriteItem.builder()
                .put(Put.builder().tableName(tableName).item(item(isin, groupKey)).build()).build();
    }

    /** The whole index partition, eventually consistent, all pages. */
    static List<SnapshotIndexEntry> readAll(DynamoDbClient client, String tableName) {
        QueryRequest request = QueryRequest.builder().tableName(tableName).consistentRead(false)
                .keyConditionExpression("pk = :pk")
                .expressionAttributeValues(Map.of(":pk", AttributeValue.fromS(PK))).build();
        return HistoryQuery.all(client, request).stream().map(SnapshotIndex::toEntry).toList();
    }

    static SnapshotIndexEntry toEntry(Map<String, AttributeValue> item) {
        String sk = item.get("sk").s();
        AttributeValue group = item.get(GROUP_KEY);
        return new SnapshotIndexEntry(sk.substring(SnapshotItem.SK_PREFIX.length()),
                group == null ? null : group.s());
    }
}
