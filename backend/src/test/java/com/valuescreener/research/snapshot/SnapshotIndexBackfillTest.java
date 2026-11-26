package com.valuescreener.research.snapshot;

import com.valuescreener.research.group.StockGroup;
import com.valuescreener.research.group.StockGroupSeeder;
import com.valuescreener.persistence.DynamoProperties;
import com.valuescreener.persistence.DynamoTableInitializer;
import com.valuescreener.persistence.DynamoTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SnapshotIndexBackfillTest extends DynamoTestSupport {

    private final DynamoDbClient client = newClient();
    private DynamoProperties props;
    private DynamoCompanySnapshotRepository repository;
    private SnapshotIndexBackfill backfill;

    @BeforeEach
    void setUp() {
        props = new DynamoProperties(freshTableName(), endpoint(), "eu-central-1", true);
        new DynamoTableInitializer(client, props).run(null);
        repository = new DynamoCompanySnapshotRepository(client, props);
        backfill = new SnapshotIndexBackfill(client, props, false);
        // snapshots as saved before the index existed: written without index items
        legacy("US0000000001", "BANKS");
        legacy("US0000000002", null);
        legacy("US0000000003", "INSURANCE");
    }

    private static final LocalDate AS_OF = LocalDate.of(2026, 5, 10);

    private void legacy(String isin, String groupKey) {
        CompanySnapshot s = new CompanySnapshot("T" + isin.substring(9), isin, "Inc " + isin, "USA", "x");
        if (groupKey != null) {
            s.assignGroup(new StockGroup(groupKey, "n", 10), GroupSource.AI);
        }
        s.upsertFinding(ResearchCriterion.PE_RATIO, new BigDecimal("1.5"), null, "c", null, AS_OF);
        client.putItem(PutItemRequest.builder().tableName(props.tableName())
                .item(SnapshotItem.SCHEMA.itemToMap(SnapshotItem.from(s, 1), true)).build());
    }

    /** The full index entry the backfill must produce for a {@link #legacy} snapshot. */
    private static SnapshotIndexEntry full(String isin, String groupKey) {
        return new SnapshotIndexEntry(isin, "T" + isin.substring(9), "Inc " + isin, "USA", groupKey,
                groupKey == null ? null : GroupSource.AI, AS_OF);
    }

    /** An index item as the v1 code wrote it: only the group. */
    private void v1IndexItem(String isin, String groupKey) {
        Map<String, AttributeValue> item = new HashMap<>(Map.of("pk", AttributeValue.fromS("SNAPINDEX"),
                "sk", AttributeValue.fromS("ISIN#" + isin)));
        if (groupKey != null) {
            item.put("groupKey", AttributeValue.fromS(groupKey));
        }
        client.putItem(PutItemRequest.builder().tableName(props.tableName()).item(item).build());
    }

    private void putMarker(String sk) {
        client.putItem(PutItemRequest.builder().tableName(props.tableName()).item(Map.of(
                "pk", AttributeValue.fromS("META"), "sk", AttributeValue.fromS(sk))).build());
    }

    private List<Map<String, AttributeValue>> indexItems() {
        return client.query(QueryRequest.builder().tableName(props.tableName()).consistentRead(true)
                .keyConditionExpression("pk = :pk")
                .expressionAttributeValues(Map.of(":pk", AttributeValue.fromS("SNAPINDEX"))).build()).items();
    }

    private boolean markerExists() {
        return client.getItem(GetItemRequest.builder().tableName(props.tableName()).consistentRead(true)
                .key(Map.of("pk", AttributeValue.fromS("META"), "sk", AttributeValue.fromS("INDEX#snapshots-v2")))
                .build()).hasItem();
    }

    @Test
    void createsMissingIndexItemsAndTheMarker() {
        assertThat(repository.findIndex()).isEmpty();

        backfill.run(null);

        assertThat(repository.findIndex()).containsExactlyInAnyOrder(full("US0000000001", "BANKS"),
                full("US0000000002", null), full("US0000000003", "INSURANCE"));
        assertThat(markerExists()).isTrue();
    }

    @Test
    void upgradesV1IndexItemsEvenWhenTheV1MarkerExists() {
        // live state before this change: v1 marker and v1 index items with only the group
        putMarker("INDEX#snapshots-v1");
        v1IndexItem("US0000000001", "BANKS");
        v1IndexItem("US0000000002", null);
        v1IndexItem("US0000000003", "INSURANCE");

        backfill.run(null);

        assertThat(repository.findIndex()).containsExactlyInAnyOrder(full("US0000000001", "BANKS"),
                full("US0000000002", null), full("US0000000003", "INSURANCE"));
        assertThat(markerExists()).isTrue();
    }

    @Test
    void secondRunIsANoOp() {
        backfill.run(null);
        client.deleteItem(DeleteItemRequest.builder().tableName(props.tableName()).key(Map.of(
                "pk", AttributeValue.fromS("SNAPINDEX"), "sk", AttributeValue.fromS("ISIN#US0000000001"))).build());

        backfill.run(null);

        // marker present: nothing is read or written, the removed item stays removed
        assertThat(indexItems()).hasSize(2);
    }

    @Test
    void neverOverwritesAnIndexItemWrittenByASave() {
        // an earlier run died (no marker); a save has meanwhile written ISIN 3 with a newer name and group
        CompanySnapshot saved = new CompanySnapshot("T3", "US0000000003", "Renamed", "USA", "x");
        saved.assignGroup(new StockGroup("NEWER", "n", 10), GroupSource.MANUAL);
        client.putItem(PutItemRequest.builder().tableName(props.tableName())
                .item(SnapshotIndex.item(saved)).build());
        assertThat(markerExists()).isFalse();

        backfill.run(null);

        assertThat(repository.findIndex()).containsExactlyInAnyOrder(full("US0000000001", "BANKS"),
                full("US0000000002", null),
                new SnapshotIndexEntry("US0000000003", "T3", "Renamed", "USA", "NEWER", GroupSource.MANUAL, null));
        assertThat(markerExists()).isTrue();
    }

    @Test
    void doesNothingWithoutAnyDynamoConfiguration() {
        DynamoProperties none = new DynamoProperties(props.tableName(), null, "eu-central-1", false);

        new SnapshotIndexBackfill(client, none, false).run(null);

        assertThat(indexItems()).isEmpty();
        assertThat(markerExists()).isFalse();
    }

    @Test
    void runsAfterTheGroupSeeder() {
        assertThat(SnapshotIndexBackfill.ORDER).isGreaterThan(StockGroupSeeder.ORDER);
    }
}
