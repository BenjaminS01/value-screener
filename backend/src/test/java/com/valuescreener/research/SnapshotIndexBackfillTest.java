package com.valuescreener.research;

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

import java.util.List;
import java.util.Map;
import java.util.Optional;

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

    private void legacy(String isin, String groupKey) {
        CompanySnapshot s = new CompanySnapshot("T" + isin.substring(9), isin, "Inc " + isin, "USA", "x");
        if (groupKey != null) {
            s.assignGroup(new StockGroup(groupKey, "n", 10), GroupSource.AI);
        }
        client.putItem(PutItemRequest.builder().tableName(props.tableName())
                .item(SnapshotItem.SCHEMA.itemToMap(SnapshotItem.from(s, 1), true)).build());
    }

    private List<Map<String, AttributeValue>> indexItems() {
        return client.query(QueryRequest.builder().tableName(props.tableName()).consistentRead(true)
                .keyConditionExpression("pk = :pk")
                .expressionAttributeValues(Map.of(":pk", AttributeValue.fromS("SNAPINDEX"))).build()).items();
    }

    private boolean markerExists() {
        return client.getItem(GetItemRequest.builder().tableName(props.tableName()).consistentRead(true)
                .key(Map.of("pk", AttributeValue.fromS("META"), "sk", AttributeValue.fromS("INDEX#snapshots-v1")))
                .build()).hasItem();
    }

    @Test
    void createsMissingIndexItemsAndTheMarker() {
        assertThat(repository.findIndex()).isEmpty();

        backfill.run(null);

        assertThat(repository.findIndex()).containsExactlyInAnyOrder(
                new SnapshotIndexEntry("US0000000001", "BANKS"), new SnapshotIndexEntry("US0000000002", null),
                new SnapshotIndexEntry("US0000000003", "INSURANCE"));
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
    void interruptedRunIsCompletedAndNeverOverwritesNewerIndexItems() {
        // an earlier run died after one item (no marker); a save has meanwhile moved ISIN 3 to another group
        client.putItem(PutItemRequest.builder().tableName(props.tableName())
                .item(SnapshotIndex.item("US0000000001", "BANKS")).build());
        client.putItem(PutItemRequest.builder().tableName(props.tableName())
                .item(SnapshotIndex.item("US0000000003", "NEWER")).build());
        assertThat(markerExists()).isFalse();

        backfill.run(null);

        assertThat(repository.findIndex()).containsExactlyInAnyOrder(
                new SnapshotIndexEntry("US0000000001", "BANKS"), new SnapshotIndexEntry("US0000000002", null),
                new SnapshotIndexEntry("US0000000003", "NEWER"));
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
