package com.valuescreener.research.group;

import com.valuescreener.persistence.ConcurrentUpdateException;
import com.valuescreener.persistence.DynamoProperties;
import com.valuescreener.persistence.ItemConditions;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.GetItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactDeleteItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactPutItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactUpdateItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactWriteItemsEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.UpdateItemEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.CancellationReason;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Repository
public class DynamoStockGroupRepository implements StockGroupRepository {

    private static final Expression ABSENT = Expression.builder().expression("attribute_not_exists(pk)").build();

    private final DynamoDbEnhancedClient client;
    private final DynamoDbTable<StockGroupItem> groups;
    private final DynamoDbTable<GroupNameItem> names;

    public DynamoStockGroupRepository(DynamoDbEnhancedClient client, DynamoProperties properties) {
        this.client = client;
        this.groups = client.table(properties.tableName(), StockGroupItem.SCHEMA);
        this.names = client.table(properties.tableName(), GroupNameItem.SCHEMA);
    }

    @Override
    public Optional<StockGroup> findById(String key) {
        StockGroupItem item = groups.getItem(GetItemEnhancedRequest.builder()
                .key(groupKey(key)).consistentRead(true).build());
        return Optional.ofNullable(item).map(StockGroupItem::toDomain);
    }

    /** Eventually consistent: read-only views (list, names). */
    @Override
    public List<StockGroup> findAll() {
        return readAll(false);
    }

    private List<StockGroup> readAll(boolean consistent) {
        return groups.query(QueryEnhancedRequest.builder()
                        .queryConditional(QueryConditional.keyEqualTo(
                                Key.builder().partitionValue(StockGroupItem.PK).build()))
                        .consistentRead(consistent).build())
                .items().stream().map(StockGroupItem::toDomain).toList();
    }

    @Override
    public boolean existsByNameIgnoreCase(String name) {
        if (name == null || name.isBlank()) {
            return false; // an empty sort key is invalid in DynamoDB; a blank name can never be stored
        }
        return names.getItem(GetItemEnhancedRequest.builder().key(nameKey(name)).consistentRead(true).build()) != null;
    }

    @Override
    public int maxSortOrder() {
        return readAll(true).stream().map(StockGroup::getSortOrder).max(Comparator.naturalOrder()).orElse(0);
    }

    @Override
    public StockGroup create(StockGroup group) {
        try {
            client.transactWriteItems(TransactWriteItemsEnhancedRequest.builder()
                    .addPutItem(groups, TransactPutItemEnhancedRequest.builder(StockGroupItem.class)
                            .item(StockGroupItem.from(group))
                            .conditionExpression(ABSENT).build())
                    .addPutItem(names, TransactPutItemEnhancedRequest.builder(GroupNameItem.class)
                            .item(GroupNameItem.of(group.getName(), group.getKey()))
                            .conditionExpression(ABSENT).build())
                    .build());
        } catch (TransactionCanceledException e) {
            if (failed(e, 0)) {
                throw new StockGroupConflictException("Group key already exists: " + group.getKey());
            }
            if (failed(e, 1)) {
                throw new StockGroupConflictException("Group name already exists: " + group.getName());
            }
            throw e;
        }
        StockGroupItem.markSaved(group);
        return group;
    }

    @Override
    public StockGroup update(StockGroup group, String previousName) {
        StockGroupItem item = StockGroupItem.from(group);
        if (GroupNameItem.sortKey(previousName).equals(GroupNameItem.sortKey(group.getName()))) {
            // same guard (also a case-only rename): one item, no guard operations
            ItemConditions.guarded("Group " + group.getKey() + " was changed concurrently",
                    () -> groups.updateItem(UpdateItemEnhancedRequest.builder(StockGroupItem.class)
                            .item(item).ignoreNulls(true).build()));
        } else {
            try {
                client.transactWriteItems(TransactWriteItemsEnhancedRequest.builder()
                        .addUpdateItem(groups, TransactUpdateItemEnhancedRequest.builder(StockGroupItem.class)
                                .item(item).ignoreNulls(true).build())
                        .addDeleteItem(names, nameKey(previousName))
                        .addPutItem(names, TransactPutItemEnhancedRequest.builder(GroupNameItem.class)
                                .item(GroupNameItem.of(group.getName(), group.getKey()))
                                .conditionExpression(ABSENT).build())
                        .build());
            } catch (TransactionCanceledException e) {
                if (failed(e, 0)) {
                    throw concurrent(group, e);
                }
                if (failed(e, 2)) {
                    throw new StockGroupConflictException("Group name already exists: " + group.getName());
                }
                if (ItemConditions.isConflict(e)) {
                    throw concurrent(group, e);
                }
                throw e;
            }
        }
        StockGroupItem.markSaved(group);
        return group;
    }

    @Override
    public void delete(StockGroup group) {
        Expression neverReferencedAndUnchanged = Expression.builder()
                .expression("attribute_not_exists(#r) AND #v = :v")
                .putExpressionName("#r", "referenced").putExpressionName("#v", "version")
                .putExpressionValue(":v", AttributeValue.fromN(requireVersion(group).toString()))
                .build();
        try {
            client.transactWriteItems(TransactWriteItemsEnhancedRequest.builder()
                    .addDeleteItem(groups, TransactDeleteItemEnhancedRequest.builder()
                            .key(groupKey(group.getKey()))
                            .conditionExpression(neverReferencedAndUnchanged).build())
                    .addDeleteItem(names, nameKey(group.getName()))
                    .build());
        } catch (TransactionCanceledException e) {
            if (ItemConditions.isConflict(e)) {
                throw new StockGroupConflictException(
                        "Group " + group.getKey() + " appears in the group history and cannot be deleted");
            }
            throw e;
        }
    }

    private static Long requireVersion(StockGroup group) {
        if (group.version() == null) {
            throw new IllegalArgumentException("cannot delete a group that was never saved");
        }
        return group.version();
    }

    private static boolean failed(TransactionCanceledException e, int index) {
        List<CancellationReason> reasons = e.cancellationReasons();
        return reasons.size() > index && "ConditionalCheckFailed".equals(reasons.get(index).code());
    }

    private static ConcurrentUpdateException concurrent(StockGroup group, Exception cause) {
        return new ConcurrentUpdateException("Group " + group.getKey() + " was changed concurrently", cause);
    }

    private static Key groupKey(String key) {
        return Key.builder().partitionValue(StockGroupItem.PK).sortValue(StockGroupItem.sortKey(key)).build();
    }

    private static Key nameKey(String name) {
        return Key.builder().partitionValue(GroupNameItem.PK).sortValue(GroupNameItem.sortKey(name)).build();
    }
}
