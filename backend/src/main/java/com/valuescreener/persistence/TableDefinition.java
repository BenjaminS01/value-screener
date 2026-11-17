package com.valuescreener.persistence;

import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ProvisionedThroughput;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;

/** Single source of the table shape (the Terraform definition copies these values). */
public final class TableDefinition {

    public static final String PK = "pk";
    public static final String SK = "sk";
    public static final String TTL = "ttl";
    public static final long READ_CAPACITY = 20L;
    public static final long WRITE_CAPACITY = 20L;

    private TableDefinition() {
    }

    public static CreateTableRequest createTableRequest(String tableName) {
        return CreateTableRequest.builder()
                .tableName(tableName)
                .attributeDefinitions(
                        AttributeDefinition.builder().attributeName(PK).attributeType(ScalarAttributeType.S).build(),
                        AttributeDefinition.builder().attributeName(SK).attributeType(ScalarAttributeType.S).build())
                .keySchema(
                        KeySchemaElement.builder().attributeName(PK).keyType(KeyType.HASH).build(),
                        KeySchemaElement.builder().attributeName(SK).keyType(KeyType.RANGE).build())
                .provisionedThroughput(ProvisionedThroughput.builder()
                        .readCapacityUnits(READ_CAPACITY).writeCapacityUnits(WRITE_CAPACITY).build())
                .build();
    }
}
