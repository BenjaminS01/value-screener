package com.valuescreener.research;

import com.valuescreener.persistence.ConcurrentUpdateException;
import com.valuescreener.persistence.DynamoProperties;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.PutItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.UpdateItemEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactionConflictException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

/** Single-item writes colliding with an in-flight transaction (TransactionConflict) are a 409, not a 500. */
@SuppressWarnings("unchecked")
class StockGroupConflictMappingTest {

    @Test
    void sameNameUpdateMapsTransactionConflict() {
        DynamoDbEnhancedClient client = Mockito.mock(DynamoDbEnhancedClient.class);
        DynamoDbTable<StockGroupItem> groups = Mockito.mock(DynamoDbTable.class);
        DynamoDbTable<GroupNameItem> names = Mockito.mock(DynamoDbTable.class);
        Mockito.when(client.table(any(String.class), any(TableSchema.class))).thenReturn(groups, names);
        DynamoStockGroupRepository repository = new DynamoStockGroupRepository(client,
                new DynamoProperties("t", "", "eu-central-1", false));
        doThrow(TransactionConflictException.builder().message("c").build())
                .when(groups).updateItem(any(UpdateItemEnhancedRequest.class));
        StockGroup group = new StockGroup("BANKS", "Banks", 10);

        assertThatThrownBy(() -> repository.update(group, "Banks")).isInstanceOf(ConcurrentUpdateException.class);
    }

    @Test
    void thesisSaveMapsTransactionConflict() {
        DynamoDbEnhancedClient client = Mockito.mock(DynamoDbEnhancedClient.class);
        DynamoDbTable<ThesisItem> table = Mockito.mock(DynamoDbTable.class);
        Mockito.when(client.table(any(String.class), any(TableSchema.class))).thenReturn(table);
        DynamoInvestmentThesisRepository repository = new DynamoInvestmentThesisRepository(client,
                new DynamoProperties("t", "", "eu-central-1", false));
        doThrow(TransactionConflictException.builder().message("c").build())
                .when(table).putItem(any(PutItemEnhancedRequest.class));
        InvestmentThesis thesis = new InvestmentThesis("US0378331005", java.time.Instant.parse("2026-01-01T00:00:00Z"));

        assertThatThrownBy(() -> repository.save(thesis)).isInstanceOf(ConcurrentUpdateException.class);
    }
}
