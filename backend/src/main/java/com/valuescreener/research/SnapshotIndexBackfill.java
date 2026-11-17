package com.valuescreener.research;

import com.valuescreener.persistence.DynamoProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.model.TransactionConflictException;

import java.util.Map;

/**
 * Creates the snapshot index items for snapshots saved before the index existed. If the marker item
 * ({@code META / INDEX#snapshots-v1}) is absent, the SNAPSHOT partition is read once, one index item is put
 * per snapshot (idempotent: the put just rewrites the same content) and the marker is written last, so an
 * interrupted run is completed by the next start and two instances running together do no harm. With the
 * marker present nothing is read.
 *
 * <p>Same run rule and ordering as {@link StockGroupSeeder} (runs right after it): only when an endpoint is
 * set, {@code app.dynamodb.create-table=true} or {@code app.dynamodb.seed-groups=true}.
 */
@Component
@Order(SnapshotIndexBackfill.ORDER)
public class SnapshotIndexBackfill implements ApplicationRunner {

    /** Must stay after StockGroupSeeder.ORDER. */
    static final int ORDER = StockGroupSeeder.ORDER + 10;
    static final String MARKER_SK = "INDEX#snapshots-v1";

    private static final Logger log = LoggerFactory.getLogger(SnapshotIndexBackfill.class);

    private final DynamoDbClient client;
    private final DynamoProperties props;
    private final boolean seedGroups;

    public SnapshotIndexBackfill(DynamoDbClient client, DynamoProperties props,
                                 @Value("${app.dynamodb.seed-groups:false}") boolean seedGroups) {
        this.client = client;
        this.props = props;
        this.seedGroups = seedGroups;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean endpointSet = props.endpoint() != null && !props.endpoint().isBlank();
        if (!(endpointSet || props.createTable() || seedGroups)) {
            return;
        }
        try {
            if (markerExists()) {
                return;
            }
            QueryRequest request = QueryRequest.builder().tableName(props.tableName()).consistentRead(true)
                    .keyConditionExpression("pk = :pk")
                    .expressionAttributeValues(Map.of(":pk", AttributeValue.fromS(SnapshotItem.PK)))
                    .projectionExpression("sk, groupKey").build();
            int indexed = 0;
            for (Map<String, AttributeValue> snapshot : HistoryQuery.all(client, request)) {
                String isin = snapshot.get("sk").s().substring(SnapshotItem.SK_PREFIX.length());
                AttributeValue group = snapshot.get(SnapshotIndex.GROUP_KEY);
                try {
                    // never overwrite: an item that exists was written by a save and is newer than this read
                    client.putItem(PutItemRequest.builder().tableName(props.tableName())
                            .item(SnapshotIndex.item(isin, group == null ? null : group.s()))
                            .conditionExpression("attribute_not_exists(pk)").build());
                    indexed++;
                } catch (ConditionalCheckFailedException | TransactionConflictException alreadyIndexed) {
                    // fine: a save has written (or is writing) this item
                }
            }
            writeMarker();
            log.info("Snapshot index backfilled for {} snapshots", indexed);
        } catch (ResourceNotFoundException noTable) {
            log.warn("Table {} does not exist, snapshot index not backfilled", props.tableName());
        }
    }

    private boolean markerExists() {
        return client.getItem(GetItemRequest.builder().tableName(props.tableName()).consistentRead(true)
                .key(markerKey()).build()).hasItem();
    }

    private void writeMarker() {
        try {
            client.putItem(PutItemRequest.builder().tableName(props.tableName()).item(markerKey())
                    .conditionExpression("attribute_not_exists(pk)").build());
        } catch (ConditionalCheckFailedException anotherInstanceFinished) {
            // fine
        }
    }

    private static Map<String, AttributeValue> markerKey() {
        return Map.of("pk", AttributeValue.fromS("META"), "sk", AttributeValue.fromS(MARKER_SK));
    }
}
