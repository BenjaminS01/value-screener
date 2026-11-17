package com.valuescreener.persistence;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DescribeTimeToLiveRequest;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.TableDescription;
import software.amazon.awssdk.services.dynamodb.model.TimeToLiveStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class DynamoTableInitializerTest extends DynamoTestSupport {

    private final DynamoDbClient client = newClient();

    private DynamoTableInitializer initializer(String table, boolean create) {
        return new DynamoTableInitializer(client, new DynamoProperties(table, endpoint(), "eu-central-1", create));
    }

    @Test
    void createsTableWithKeysAndTtl() {
        String table = freshTableName();
        initializer(table, true).run(null);

        TableDescription d = client.describeTable(DescribeTableRequest.builder().tableName(table).build()).table();
        assertThat(d.keySchema()).anySatisfy(k -> {
            assertThat(k.attributeName()).isEqualTo("pk");
            assertThat(k.keyType()).isEqualTo(KeyType.HASH);
        }).anySatisfy(k -> {
            assertThat(k.attributeName()).isEqualTo("sk");
            assertThat(k.keyType()).isEqualTo(KeyType.RANGE);
        });
        assertThat(d.provisionedThroughput().readCapacityUnits()).isEqualTo(20L);
        assertThat(d.provisionedThroughput().writeCapacityUnits()).isEqualTo(20L);

        var ttl = client.describeTimeToLive(DescribeTimeToLiveRequest.builder().tableName(table).build())
                .timeToLiveDescription();
        assertThat(ttl.attributeName()).isEqualTo("ttl");
        assertThat(ttl.timeToLiveStatus()).isIn(TimeToLiveStatus.ENABLED, TimeToLiveStatus.ENABLING);
    }

    @Test
    void secondRunIsNoOp() {
        String table = freshTableName();
        DynamoTableInitializer init = initializer(table, true);
        init.run(null);
        assertThatCode(() -> init.run(null)).doesNotThrowAnyException();
    }

    @Test
    void doesNothingWhenCreateTableIsFalse() {
        String table = freshTableName();
        initializer(table, false).run(null);
        assertThat(client.listTables().tableNames()).doesNotContain(table);
    }
}
