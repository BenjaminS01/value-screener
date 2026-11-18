package com.valuescreener.research.group;

import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

import java.util.Locale;

/** Uniqueness guard for group names: pk "GROUPNAME", sk = lowercase(trimmed name). */
@DynamoDbBean
public class GroupNameItem {

    public static final TableSchema<GroupNameItem> SCHEMA = TableSchema.fromBean(GroupNameItem.class);
    static final String PK = "GROUPNAME";

    private String pk;
    private String sk;
    private String groupKey;

    public static String sortKey(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }

    public static GroupNameItem of(String name, String groupKey) {
        GroupNameItem item = new GroupNameItem();
        item.pk = PK;
        item.sk = sortKey(name);
        item.groupKey = groupKey;
        return item;
    }

    @DynamoDbPartitionKey
    public String getPk() { return pk; }
    public void setPk(String pk) { this.pk = pk; }

    @DynamoDbSortKey
    public String getSk() { return sk; }
    public void setSk(String sk) { this.sk = sk; }

    public String getGroupKey() { return groupKey; }
    public void setGroupKey(String groupKey) { this.groupKey = groupKey; }
}
