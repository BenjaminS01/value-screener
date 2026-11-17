package com.valuescreener.research;

import com.valuescreener.persistence.DynamoProperties;
import com.valuescreener.persistence.DynamoTableInitializer;
import com.valuescreener.persistence.DynamoTestSupport;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.Put;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Spec 6.1: history is append-only, there is no update or delete code path. */
class HistoryAppendOnlyTest extends DynamoTestSupport {

    @Test
    void historyRepositoriesDeclareOnlyTheirReadMethod() {
        assertThat(Arrays.stream(ResearchFindingHistoryRepository.class.getDeclaredMethods()).map(Method::getName))
                .containsExactly("findBySnapshotIsin");
        assertThat(Arrays.stream(StockGroupHistoryRepository.class.getDeclaredMethods()).map(Method::getName))
                .containsExactly("findBySnapshotIsin");
        assertThat(Arrays.stream(DynamoResearchFindingHistoryRepository.class.getDeclaredMethods())
                .filter(m -> java.lang.reflect.Modifier.isPublic(m.getModifiers())).map(Method::getName))
                .containsExactly("findBySnapshotIsin");
        assertThat(Arrays.stream(DynamoStockGroupHistoryRepository.class.getDeclaredMethods())
                .filter(m -> java.lang.reflect.Modifier.isPublic(m.getModifiers())).map(Method::getName))
                .containsExactly("findBySnapshotIsin");
    }

    @Test
    void historyItemIsNeverOverwritten() {
        DynamoDbClient client = newClient();
        DynamoProperties props = new DynamoProperties(freshTableName(), endpoint(), "eu-central-1", true);
        new DynamoTableInitializer(client, props).run(null);
        String isin = "US0378331005";
        DynamoCompanySnapshotRepository repository = new DynamoCompanySnapshotRepository(client, props);
        CompanySnapshot snapshot = new CompanySnapshot("AAPL", isin, "Apple", "USA", "x");
        repository.save(snapshot, List.of(new ResearchFindingHistory(isin, new ReplacedFinding(
                ResearchCriterion.PE_RATIO, new BigDecimal("1"), null, "original", null, LocalDate.of(2026, 8, 1)),
                Instant.parse("2026-11-12T10:00:00Z"), HistoryOrigin.INGEST)), Optional.empty());
        Map<String, AttributeValue> stored = client.query(b -> b.tableName(props.tableName()).consistentRead(true)
                .keyConditionExpression("pk = :pk AND begins_with(sk, :p)")
                .expressionAttributeValues(Map.of(":pk", AttributeValue.fromS("SNAPSHOT#" + isin),
                        ":p", AttributeValue.fromS("FHIST#")))).items().get(0);

        // the very same key again, written the way the save path writes history items: must fail
        Map<String, AttributeValue> overwrite = new java.util.HashMap<>(stored);
        overwrite.put("claim", AttributeValue.fromS("tampered"));
        TransactWriteItem put = TransactWriteItem.builder().put(Put.builder().tableName(props.tableName())
                .item(overwrite).conditionExpression("attribute_not_exists(pk)").build()).build();
        assertThatThrownBy(() -> client.transactWriteItems(TransactWriteItemsRequest.builder()
                .transactItems(put).build())).isInstanceOf(TransactionCanceledException.class);

        // and the production code really builds history puts with that condition: asserting the produced item
        assertThat(DynamoCompanySnapshotRepository.historyPutCondition()).isEqualTo("attribute_not_exists(pk)");
        assertThat(client.getItem(GetItemRequest.builder().tableName(props.tableName()).consistentRead(true)
                .key(Map.of("pk", stored.get("pk"), "sk", stored.get("sk"))).build()).item().get("claim").s())
                .isEqualTo("original");
    }
}
