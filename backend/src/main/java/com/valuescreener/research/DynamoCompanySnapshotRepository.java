package com.valuescreener.research;

import com.valuescreener.persistence.ConcurrentUpdateException;
import com.valuescreener.persistence.DynamoProperties;
import com.valuescreener.persistence.ItemConditions;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.CancellationReason;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.Put;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.Select;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Snapshots as items {@code pk="SNAPSHOT", sk="ISIN#"+isin}; history entries live in the partition
 * {@code "SNAPSHOT#"+isin} (see {@link FindingHistoryItem}, {@link GroupHistoryItem}).
 *
 * <p>{@link #save} is one TransactWriteItems with a fixed layout: index 0 is the snapshot, then one item per
 * finding history entry, then (only with a group change) the group's markReferenced update as the LAST item.
 * Cancellation reasons are mapped by those positions, see {@link #translate}. The request is built at the low
 * level because markReferenced is a low-level item; the snapshot version is maintained here.
 */
@Repository
public class DynamoCompanySnapshotRepository implements CompanySnapshotRepository {

    private final DynamoDbClient client;
    private final String tableName;

    public DynamoCompanySnapshotRepository(DynamoDbClient client, DynamoProperties properties) {
        this.client = client;
        this.tableName = properties.tableName();
    }

    @Override
    public Optional<CompanySnapshot> findByIsin(String isin) {
        GetItemResponse response = client.getItem(GetItemRequest.builder().tableName(tableName)
                .consistentRead(true).key(key(isin)).build());
        return response.hasItem() ? Optional.of(toDomain(response.item())) : Optional.empty();
    }

    @Override
    public List<CompanySnapshot> findAll() {
        return query(null, Map.of(), Map.of());
    }

    @Override
    public List<CompanySnapshot> findAllByGroupKey(String key) {
        return query("#g = :g", Map.of("#g", "groupKey"), Map.of(":g", AttributeValue.fromS(key)));
    }

    @Override
    public List<CompanySnapshot> findAllWithoutGroup() {
        return query("attribute_not_exists(#g)", Map.of("#g", "groupKey"), Map.of());
    }

    @Override
    public long countByGroupKey(String key) {
        return HistoryQuery.count(client, partitionRequest("#g = :g", Map.of("#g", "groupKey"),
                Map.of(":g", AttributeValue.fromS(key))).select(Select.COUNT).build());
    }

    /** One consistent GetItem per distinct ISIN (callers pass the handful of ISINs of a portfolio). */
    @Override
    public List<CompanySnapshot> findAllByIsinIn(Collection<String> isins) {
        List<CompanySnapshot> found = new ArrayList<>();
        for (String isin : new LinkedHashSet<>(isins)) {
            findByIsin(isin).ifPresent(found::add);
        }
        return found;
    }

    @Override
    public CompanySnapshot save(CompanySnapshot snapshot, List<ResearchFindingHistory> findingHistory,
                                Optional<StockGroupHistory> groupHistory) {
        String isin = snapshot.getIsin();
        requireSameSnapshot(isin, findingHistory, groupHistory, snapshot.getGroupKey());
        Long expected = snapshot.version();
        long next = ItemConditions.nextVersion(expected);

        List<TransactWriteItem> items = new ArrayList<>();
        items.add(snapshotPut(SnapshotItem.from(snapshot, next), expected));
        for (int i = 0; i < findingHistory.size(); i++) {
            items.add(historyPut(FindingHistoryItem.SCHEMA.itemToMap(
                    FindingHistoryItem.from(findingHistory.get(i), i), true)));
        }
        String referencedGroup = null;
        if (groupHistory.isPresent()) {
            items.add(historyPut(GroupHistoryItem.SCHEMA.itemToMap(GroupHistoryItem.from(groupHistory.get()), true)));
            referencedGroup = groupHistory.get().getNewGroupKey();
            items.add(StockGroupRepository.markReferenced(tableName, referencedGroup));
        }
        try {
            client.transactWriteItems(TransactWriteItemsRequest.builder().transactItems(items).build());
        } catch (TransactionCanceledException e) {
            throw translate(e, isin, referencedGroup, items.size());
        }
        snapshot.version(next);
        return snapshot;
    }

    /**
     * Maps a cancelled transaction by the position of the failed item: index 0 (snapshot) failing its
     * condition, or any item locked by a concurrent transaction, is a {@link ConcurrentUpdateException}
     * (409); the last item failing is the group's markReferenced, i.e. the group was deleted meanwhile
     * ({@link UnknownGroupException}, only when a group reference was part of the transaction). Anything
     * else is returned unchanged.
     *
     * @param referencedGroup key marked by the last item, null when the transaction has no such item
     */
    static RuntimeException translate(TransactionCanceledException e, String isin, String referencedGroup,
                                      int itemCount) {
        List<CancellationReason> reasons = e.cancellationReasons();
        if (failedCondition(reasons, 0)) {
            return new ConcurrentUpdateException("Snapshot " + isin + " was changed concurrently", e);
        }
        if (referencedGroup != null && failedCondition(reasons, itemCount - 1)) {
            return new UnknownGroupException(referencedGroup);
        }
        if (reasons.stream().anyMatch(r -> "TransactionConflict".equals(r.code()))) {
            return new ConcurrentUpdateException("Snapshot " + isin + " was changed concurrently", e);
        }
        return e;
    }

    private static boolean failedCondition(List<CancellationReason> reasons, int index) {
        return reasons.size() > index && "ConditionalCheckFailed".equals(reasons.get(index).code());
    }

    private static void requireSameSnapshot(String isin, List<ResearchFindingHistory> findingHistory,
                                            Optional<StockGroupHistory> groupHistory, String groupKey) {
        for (ResearchFindingHistory h : findingHistory) {
            if (!isin.equals(h.getSnapshotIsin())) {
                throw new IllegalArgumentException("history entry belongs to snapshot " + h.getSnapshotIsin());
            }
        }
        groupHistory.ifPresent(h -> {
            if (!isin.equals(h.getSnapshotIsin()) || !h.getNewGroupKey().equals(groupKey)) {
                throw new IllegalArgumentException("group history entry does not match the snapshot's group");
            }
        });
    }

    private TransactWriteItem snapshotPut(SnapshotItem item, Long expectedVersion) {
        Put.Builder put = Put.builder().tableName(tableName).item(SnapshotItem.SCHEMA.itemToMap(item, true));
        if (expectedVersion == null) {
            put.conditionExpression("attribute_not_exists(pk)");
        } else {
            put.conditionExpression("#v = :v").expressionAttributeNames(Map.of("#v", "version"))
                    .expressionAttributeValues(Map.of(":v", AttributeValue.fromN(expectedVersion.toString())));
        }
        return TransactWriteItem.builder().put(put.build()).build();
    }

    /** History items are only ever created: a put that fails if the key exists (append-only). */
    static String historyPutCondition() {
        return "attribute_not_exists(pk)";
    }

    private TransactWriteItem historyPut(Map<String, AttributeValue> item) {
        return TransactWriteItem.builder().put(Put.builder().tableName(tableName).item(item)
                .conditionExpression(historyPutCondition()).build()).build();
    }

    private List<CompanySnapshot> query(String filter, Map<String, String> names,
                                        Map<String, AttributeValue> values) {
        return HistoryQuery.all(client, partitionRequest(filter, names, values).build()).stream()
                .map(DynamoCompanySnapshotRepository::toDomain).toList();
    }

    private QueryRequest.Builder partitionRequest(String filter, Map<String, String> names,
                                                  Map<String, AttributeValue> values) {
        Map<String, AttributeValue> allValues = new java.util.HashMap<>(values);
        allValues.put(":pk", AttributeValue.fromS(SnapshotItem.PK));
        QueryRequest.Builder request = QueryRequest.builder().tableName(tableName).consistentRead(true)
                .keyConditionExpression("pk = :pk").expressionAttributeValues(allValues);
        if (filter != null) {
            request.filterExpression(filter);
        }
        if (!names.isEmpty()) {
            request.expressionAttributeNames(names);
        }
        return request;
    }

    private static CompanySnapshot toDomain(Map<String, AttributeValue> item) {
        return SnapshotItem.SCHEMA.mapToItem(item).toDomain();
    }

    private static Map<String, AttributeValue> key(String isin) {
        return Map.of("pk", AttributeValue.fromS(SnapshotItem.PK),
                "sk", AttributeValue.fromS(SnapshotItem.sortKey(isin)));
    }
}
