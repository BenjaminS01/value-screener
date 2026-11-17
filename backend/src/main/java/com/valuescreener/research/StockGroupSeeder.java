package com.valuescreener.research;

import com.valuescreener.persistence.DynamoProperties;
import com.valuescreener.persistence.DynamoTableInitializer;
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
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;

import java.util.Map;

/**
 * Seeds the initial stock group catalog once: if the marker item is absent, all missing groups are created
 * (existing ones are skipped) and the marker is written last, so an interrupted seed is completed by the next
 * start while groups the owner deleted after a completed seed are never re-created.
 *
 * <p>Runs after {@code DynamoTableInitializer} (order 10) and only when DynamoDB is reachable by configuration:
 * an endpoint is set (DynamoDB Local), {@code app.dynamodb.create-table=true}, or
 * {@code app.dynamodb.seed-groups=true} (set in AWS, where the table exists already). Contexts without any of
 * these make no AWS call at startup.
 */
@Component
@Order(StockGroupSeeder.ORDER)
public class StockGroupSeeder implements ApplicationRunner {

    /** Must stay after DynamoTableInitializer.ORDER. */
    static final int ORDER = DynamoTableInitializer.ORDER + 10;

    private static final Logger log = LoggerFactory.getLogger(StockGroupSeeder.class);

    private final DynamoDbClient client;
    private final DynamoProperties props;
    private final StockGroupRepository groups;
    private final boolean seedGroups;

    public StockGroupSeeder(DynamoDbClient client, DynamoProperties props, StockGroupRepository groups,
                            @Value("${app.dynamodb.seed-groups:false}") boolean seedGroups) {
        this.client = client;
        this.props = props;
        this.groups = groups;
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
            int created = 0;
            for (StockGroup seed : StockGroupSeed.GROUPS) {
                try {
                    groups.create(new StockGroup(seed.getKey(), seed.getName(), seed.getSortOrder()));
                    created++;
                } catch (StockGroupConflictException alreadyThere) {
                    // key or name exists: skip
                }
            }
            // marker last: an interrupted seed is completed by the next start
            writeMarker();
            log.info("Seeded {} stock groups", created);
        } catch (ResourceNotFoundException noTable) {
            log.warn("Table {} does not exist, stock groups not seeded", props.tableName());
        }
    }

    private boolean markerExists() {
        return client.getItem(GetItemRequest.builder().tableName(props.tableName()).consistentRead(true)
                .key(markerKey()).build()).hasItem();
    }

    private void writeMarker() {
        try {
            client.putItem(PutItemRequest.builder()
                    .tableName(props.tableName())
                    .item(markerKey())
                    .conditionExpression("attribute_not_exists(pk)")
                    .build());
        } catch (ConditionalCheckFailedException anotherInstanceFinished) {
            // fine
        }
    }

    private static Map<String, AttributeValue> markerKey() {
        return Map.of("pk", AttributeValue.fromS("META"), "sk", AttributeValue.fromS(StockGroupSeed.MARKER_SK));
    }
}
