package com.valuescreener.research;

import com.valuescreener.persistence.DynamoProperties;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.util.List;

@Repository
public class DynamoStockGroupHistoryRepository implements StockGroupHistoryRepository {

    private final DynamoDbClient client;
    private final String tableName;

    public DynamoStockGroupHistoryRepository(DynamoDbClient client, DynamoProperties properties) {
        this.client = client;
        this.tableName = properties.tableName();
    }

    @Override
    public List<StockGroupHistory> findBySnapshotIsin(String isin) {
        return HistoryQuery.items(client, tableName, FindingHistoryItem.partitionKey(isin),
                        GroupHistoryItem.SK_PREFIX, true).stream()
                .map(i -> GroupHistoryItem.SCHEMA.mapToItem(i).toDomain()).toList();
    }
}
