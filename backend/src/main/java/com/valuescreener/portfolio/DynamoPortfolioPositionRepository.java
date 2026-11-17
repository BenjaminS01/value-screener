package com.valuescreener.portfolio;

import com.valuescreener.persistence.DynamoProperties;
import com.valuescreener.persistence.ItemConditions;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.DeleteItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.GetItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.PutItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;

import java.util.List;
import java.util.Optional;

@Repository
class DynamoPortfolioPositionRepository implements PortfolioPositionRepository {

    private final DynamoDbTable<PositionItem> table;

    DynamoPortfolioPositionRepository(DynamoDbEnhancedClient client, DynamoProperties properties) {
        this.table = client.table(properties.tableName(), PositionItem.SCHEMA);
    }

    @Override
    public Optional<PortfolioPosition> findByIsin(String isin) {
        PositionItem item = table.getItem(GetItemEnhancedRequest.builder()
                .key(key(isin)).consistentRead(true).build());
        return Optional.ofNullable(item).map(PositionItem::toDomain);
    }

    @Override
    public List<PortfolioPosition> findAll() {
        return table.query(QueryEnhancedRequest.builder()
                        .queryConditional(QueryConditional.keyEqualTo(
                                Key.builder().partitionValue(PositionItem.PK).build()))
                        .consistentRead(false).build()) // read-only listing
                .items().stream().map(PositionItem::toDomain).toList();
    }

    @Override
    public PortfolioPosition save(PortfolioPosition position) {
        ItemConditions.guarded("Position " + position.getIsin() + " was changed concurrently",
                () -> table.putItem(PutItemEnhancedRequest.builder(PositionItem.class)
                        .item(PositionItem.from(position))
                        .conditionExpression(ItemConditions.createOnlyIfNew(position.version()))
                        .build()));
        PositionItem.markSaved(position);
        return position;
    }

    @Override
    public void delete(PortfolioPosition position) {
        ItemConditions.guarded("Position " + position.getIsin() + " was changed concurrently",
                () -> table.deleteItem(DeleteItemEnhancedRequest.builder()
                        .key(key(position.getIsin()))
                        .conditionExpression(ItemConditions.versionEquals(position.version()))
                        .build()));
    }

    private static Key key(String isin) {
        return Key.builder().partitionValue(PositionItem.PK).sortValue(PositionItem.sortKey(isin)).build();
    }
}
