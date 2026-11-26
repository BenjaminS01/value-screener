package com.valuescreener.research.snapshot;

import com.valuescreener.persistence.HistoryQuery;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.Put;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Small per-snapshot index items in their own partition: {@code pk="SNAPINDEX", sk="ISIN#"+isin} with what the
 * research list shows ({@code ticker}, {@code companyName}, {@code country}, {@code groupKey}, {@code groupSource},
 * {@code lastResearchedAt} as ISO date; absent values are left out). They are written in the same transaction as
 * every snapshot save, so the list, group counts and the portfolio's group lookup read ~200 bytes per snapshot
 * instead of the 5-10 KB snapshot items. Existing data is filled by {@link SnapshotIndexBackfill}.
 */
final class SnapshotIndex {

    static final String PK = "SNAPINDEX";
    static final String TICKER = "ticker";
    static final String COMPANY_NAME = "companyName";
    static final String COUNTRY = "country";
    static final String GROUP_KEY = "groupKey";
    static final String GROUP_SOURCE = "groupSource";
    static final String LAST_RESEARCHED_AT = "lastResearchedAt";

    private SnapshotIndex() {
    }

    static Map<String, AttributeValue> item(CompanySnapshot snapshot) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("pk", AttributeValue.fromS(PK));
        item.put("sk", AttributeValue.fromS(SnapshotItem.sortKey(snapshot.getIsin())));
        putIfPresent(item, TICKER, snapshot.getTicker());
        putIfPresent(item, COMPANY_NAME, snapshot.getCompanyName());
        putIfPresent(item, COUNTRY, snapshot.getCountry());
        putIfPresent(item, GROUP_KEY, snapshot.getGroupKey());
        GroupSource source = snapshot.getGroupSource();
        putIfPresent(item, GROUP_SOURCE, source == null ? null : source.name());
        LocalDate date = snapshot.lastResearchedAt();
        putIfPresent(item, LAST_RESEARCHED_AT, date == null ? null : date.toString());
        return item;
    }

    private static void putIfPresent(Map<String, AttributeValue> item, String name, String value) {
        if (value != null) {
            item.put(name, AttributeValue.fromS(value));
        }
    }

    /** Unconditional put: the snapshot item's own condition already guards the transaction. */
    static TransactWriteItem put(String tableName, CompanySnapshot snapshot) {
        return TransactWriteItem.builder()
                .put(Put.builder().tableName(tableName).item(item(snapshot)).build()).build();
    }

    /** The whole index partition, eventually consistent, all pages. */
    static List<SnapshotIndexEntry> readAll(DynamoDbClient client, String tableName) {
        QueryRequest request = QueryRequest.builder().tableName(tableName).consistentRead(false)
                .keyConditionExpression("pk = :pk")
                .expressionAttributeValues(Map.of(":pk", AttributeValue.fromS(PK))).build();
        return HistoryQuery.all(client, request).stream().map(SnapshotIndex::toEntry).toList();
    }

    /** Reads v1 items (only {@code groupKey}) too: the missing attributes become null. */
    static SnapshotIndexEntry toEntry(Map<String, AttributeValue> item) {
        String sk = item.get("sk").s();
        String source = string(item, GROUP_SOURCE);
        String date = string(item, LAST_RESEARCHED_AT);
        return new SnapshotIndexEntry(sk.substring(SnapshotItem.SK_PREFIX.length()), string(item, TICKER),
                string(item, COMPANY_NAME), string(item, COUNTRY), string(item, GROUP_KEY),
                source == null ? null : GroupSource.valueOf(source), date == null ? null : LocalDate.parse(date));
    }

    private static String string(Map<String, AttributeValue> item, String name) {
        AttributeValue value = item.get(name);
        return value == null ? null : value.s();
    }
}
