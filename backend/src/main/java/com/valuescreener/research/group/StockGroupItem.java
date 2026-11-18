package com.valuescreener.research.group;

import com.valuescreener.persistence.ItemConditions;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.extensions.annotations.DynamoDbVersionAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.Update;

import java.util.Map;

/**
 * Storage shape of a {@link StockGroup}: pk "GROUP", sk "KEY#" + key. {@code referenced} is set (and never
 * removed) once something stores a reference to the group; it is not part of the domain object, so updates
 * must ignore null attributes to keep it.
 */
@DynamoDbBean
public class StockGroupItem {

    public static final TableSchema<StockGroupItem> SCHEMA = TableSchema.fromBean(StockGroupItem.class);
    static final String PK = "GROUP";

    private String pk;
    private String sk;
    private String name;
    private Integer sortOrder;
    private Long version;
    private Boolean referenced;

    public static String sortKey(String key) {
        return "KEY#" + key;
    }

    public static StockGroupItem from(StockGroup g) {
        StockGroupItem item = new StockGroupItem();
        item.pk = PK;
        item.sk = sortKey(g.getKey());
        item.name = g.getName();
        item.sortOrder = g.getSortOrder();
        item.version = g.version();
        return item;
    }

    public StockGroup toDomain() {
        return StockGroup.restore(sk.substring("KEY#".length()), name, sortOrder, version);
    }

    /** Call after the item was written: the stored version is one higher than the one the object carried. */
    public static void markSaved(StockGroup g) {
        g.version(ItemConditions.nextVersion(g.version()));
    }

    static TransactWriteItem markReferenced(String tableName, String groupKey) {
        return TransactWriteItem.builder().update(Update.builder()
                .tableName(tableName)
                .key(Map.of("pk", AttributeValue.fromS(PK), "sk", AttributeValue.fromS(sortKey(groupKey))))
                .updateExpression("SET #r = :true")
                .conditionExpression("attribute_exists(pk)")
                .expressionAttributeNames(Map.of("#r", "referenced"))
                .expressionAttributeValues(Map.of(":true", AttributeValue.fromBool(true)))
                .build()).build();
    }

    @DynamoDbPartitionKey
    public String getPk() { return pk; }
    public void setPk(String pk) { this.pk = pk; }

    @DynamoDbSortKey
    public String getSk() { return sk; }
    public void setSk(String sk) { this.sk = sk; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    @DynamoDbVersionAttribute
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    public Boolean getReferenced() { return referenced; }
    public void setReferenced(Boolean referenced) { this.referenced = referenced; }
}
