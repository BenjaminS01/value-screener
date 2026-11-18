package com.valuescreener.limits;

import com.valuescreener.persistence.ConcurrentUpdateException;
import com.valuescreener.persistence.DynamoProperties;
import com.valuescreener.MutableClock;
import com.valuescreener.security.auth.Role;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.CancellationReason;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsResponse;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Transaction conflicts (not limit hits) must never become a LimitReachedException. */
class UsageLimiterConflictTest {

    private final DynamoDbClient client = mock(DynamoDbClient.class);
    private final UsageLimiter limiter = new UsageLimiter(client,
            new DynamoProperties("t", "", "eu-central-1", false), new LimitsProperties(5, 20, 100), new MutableClock());

    private static TransactionCanceledException conflict() {
        return TransactionCanceledException.builder().message("cancelled")
                .cancellationReasons(CancellationReason.builder().code("TransactionConflict").build(),
                        CancellationReason.builder().code("None").build())
                .build();
    }

    @Test
    void persistentConflictBecomesConcurrentUpdateAfterBoundedRetries() {
        when(client.transactWriteItems(any(TransactWriteItemsRequest.class))).thenThrow(conflict());

        assertThatThrownBy(() -> limiter.consume("alice", Role.USER, JobType.DEEP_ANALYSIS_DEMO))
                .isInstanceOf(ConcurrentUpdateException.class)
                .hasCauseInstanceOf(TransactionCanceledException.class);
        verify(client, times(3)).transactWriteItems(any(TransactWriteItemsRequest.class));
    }

    @Test
    void conflictOnTheGlobalItemIsAlsoAConflict() {
        var reasons = new CancellationReason[]{CancellationReason.builder().code("None").build(),
                CancellationReason.builder().code("TransactionConflict").build()};
        when(client.transactWriteItems(any(TransactWriteItemsRequest.class)))
                .thenThrow(TransactionCanceledException.builder().cancellationReasons(reasons).build());

        assertThatThrownBy(() -> limiter.consume("alice", Role.USER, JobType.DEEP_ANALYSIS_DEMO))
                .isInstanceOf(ConcurrentUpdateException.class);
    }

    @Test
    void succeedsWhenALaterAttemptGoesThrough() {
        when(client.transactWriteItems(any(TransactWriteItemsRequest.class)))
                .thenThrow(conflict())
                .thenReturn(TransactWriteItemsResponse.builder().build());
        when(client.getItem(any(GetItemRequest.class))).thenReturn(GetItemResponse.builder()
                .item(Map.of("count", AttributeValue.fromN("1"))).build());

        assertThat(limiter.consume("alice", Role.USER, JobType.DEEP_ANALYSIS_DEMO)).isEqualTo(new Quota(4, 5));
        verify(client, times(2)).transactWriteItems(any(TransactWriteItemsRequest.class));
    }
}
