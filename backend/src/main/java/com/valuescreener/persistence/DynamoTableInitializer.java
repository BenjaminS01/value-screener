package com.valuescreener.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DescribeTimeToLiveRequest;
import software.amazon.awssdk.services.dynamodb.model.ResourceInUseException;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.model.TimeToLiveSpecification;
import software.amazon.awssdk.services.dynamodb.model.TimeToLiveStatus;
import software.amazon.awssdk.services.dynamodb.model.UpdateTimeToLiveRequest;

/** Creates the table (with TTL) at startup when {@code app.dynamodb.create-table=true}. Idempotent. */
@Component
@Order(DynamoTableInitializer.ORDER)
public class DynamoTableInitializer implements ApplicationRunner {

    /** Runs before the data seeders (StockGroupSeeder). */
    public static final int ORDER = 10;

    private static final Logger log = LoggerFactory.getLogger(DynamoTableInitializer.class);

    private final DynamoDbClient client;
    private final DynamoProperties props;

    public DynamoTableInitializer(DynamoDbClient client, DynamoProperties props) {
        this.client = client;
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!props.createTable()) {
            return;
        }
        String table = props.tableName();
        if (!exists(table)) {
            try {
                client.createTable(TableDefinition.createTableRequest(table));
                log.info("Created DynamoDB table {}", table);
            } catch (ResourceInUseException alreadyCreated) {
                // created concurrently: fine
            }
        }
        TimeToLiveStatus status = client.describeTimeToLive(
                DescribeTimeToLiveRequest.builder().tableName(table).build())
                .timeToLiveDescription().timeToLiveStatus();
        if (status == TimeToLiveStatus.DISABLED || status == TimeToLiveStatus.DISABLING) {
            client.updateTimeToLive(UpdateTimeToLiveRequest.builder()
                    .tableName(table)
                    .timeToLiveSpecification(TimeToLiveSpecification.builder()
                            .enabled(true).attributeName(TableDefinition.TTL).build())
                    .build());
        }
    }

    private boolean exists(String table) {
        try {
            client.describeTable(DescribeTableRequest.builder().tableName(table).build());
            return true;
        } catch (ResourceNotFoundException e) {
            return false;
        }
    }
}
