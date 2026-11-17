package com.valuescreener.portfolio;

import com.valuescreener.persistence.ConcurrentUpdateException;
import com.valuescreener.persistence.DynamoProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.model.DeleteItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.PutItemEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.TransactionConflictException;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

/** A single-item write colliding with an in-flight transaction (TransactionConflict) is a 409, not a 500. */
@SuppressWarnings("unchecked")
class PortfolioConflictMappingTest {

    private DynamoDbTable<PositionItem> table;
    private DynamoPortfolioPositionRepository repository;
    private PortfolioPosition position;

    @BeforeEach
    void setUp() {
        DynamoDbEnhancedClient client = Mockito.mock(DynamoDbEnhancedClient.class);
        table = Mockito.mock(DynamoDbTable.class);
        Mockito.when(client.table(any(String.class), any(software.amazon.awssdk.enhanced.dynamodb.TableSchema.class)))
                .thenReturn(table);
        repository = new DynamoPortfolioPositionRepository(client, new DynamoProperties("t", "", "eu-central-1", false));
        position = new PortfolioPosition("AAPL", "US0378331005", "Apple Inc.", new BigDecimal("1"),
                new BigDecimal("1.00"), LocalDate.of(2026, 1, 1));
        PositionItem.markSaved(position);
    }

    @Test
    void saveMapsTransactionConflictAndFailedCondition() {
        doThrow(TransactionConflictException.builder().message("c").build())
                .when(table).putItem(any(PutItemEnhancedRequest.class));
        assertThatThrownBy(() -> repository.save(position)).isInstanceOf(ConcurrentUpdateException.class);

        doThrow(ConditionalCheckFailedException.builder().message("c").build())
                .when(table).putItem(any(PutItemEnhancedRequest.class));
        assertThatThrownBy(() -> repository.save(position)).isInstanceOf(ConcurrentUpdateException.class);
    }

    @Test
    void deleteMapsTransactionConflict() {
        doThrow(TransactionConflictException.builder().message("c").build())
                .when(table).deleteItem(any(DeleteItemEnhancedRequest.class));
        assertThatThrownBy(() -> repository.delete(position)).isInstanceOf(ConcurrentUpdateException.class);
    }
}
