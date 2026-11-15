package com.valuescreener.research;

import com.valuescreener.persistence.DynamoProperties;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.util.List;

@Repository
public class DynamoResearchFindingHistoryRepository implements ResearchFindingHistoryRepository {

    private final DynamoDbClient client;
    private final String tableName;

    public DynamoResearchFindingHistoryRepository(DynamoDbClient client, DynamoProperties properties) {
        this.client = client;
        this.tableName = properties.tableName();
    }

    @Override
    public List<ResearchFindingHistory> findBySnapshotIsin(String isin) {
        return HistoryQuery.items(client, tableName, FindingHistoryItem.partitionKey(isin),
                        FindingHistoryItem.SK_PREFIX, true).stream()
                .map(i -> FindingHistoryItem.SCHEMA.mapToItem(i).toDomain()).toList();
    }
}
