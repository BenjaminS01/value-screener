package com.valuescreener.limits;

import com.valuescreener.persistence.ConcurrentUpdateException;
import com.valuescreener.persistence.DynamoProperties;
import com.valuescreener.persistence.TableDefinition;
import com.valuescreener.persistence.Ttl;
import com.valuescreener.security.Role;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;
import software.amazon.awssdk.services.dynamodb.model.Update;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;

/**
 * Daily usage limits counted in DynamoDB. One transaction increments the account counter and the
 * global counter, each guarded by a condition, so a rejected call leaves no trace in either.
 * Item layout: pk = "USAGE#" + day, sk = account + "#" + jobType, attributes count and ttl.
 */
@Service
@EnableConfigurationProperties(LimitsProperties.class)
public class UsageLimiter {

    static final String GLOBAL_ACCOUNT = "*";
    static final String GLOBAL_JOB_TYPE = "ALL";

    private static final String CONDITION_FAILED = "ConditionalCheckFailed";
    private static final long TTL_DAYS = 2;
    private static final int MAX_ATTEMPTS = 3;

    private final DynamoDbClient dynamo;
    private final String table;
    private final LimitsProperties props;
    private final Clock clock;

    public UsageLimiter(DynamoDbClient dynamo, DynamoProperties dynamoProps, LimitsProperties props, Clock clock) {
        this.dynamo = dynamo;
        this.table = dynamoProps.tableName();
        this.props = props;
        this.clock = clock;
    }

    public Quota consume(String account, Role role, JobType type) {
        String acct = account.toLowerCase(Locale.ROOT);
        int limit = limitFor(role);
        LocalDate day = LocalDate.now(clock);

        // No write at all when a limit is zero: account is checked first, then global.
        if (limit <= 0) {
            throw new LimitReachedException(LimitReachedException.Scope.ACCOUNT);
        }
        if (props.globalPerDay() <= 0) {
            throw new LimitReachedException(LimitReachedException.Scope.GLOBAL);
        }

        TransactWriteItemsRequest request = TransactWriteItemsRequest.builder().transactItems(
                increment(day, acct, type.name(), limit),
                increment(day, GLOBAL_ACCOUNT, GLOBAL_JOB_TYPE, props.globalPerDay())).build();
        // A cancelled transaction writes nothing, so a retry after a conflict cannot double count.
        for (int attempt = 1; ; attempt++) {
            try {
                dynamo.transactWriteItems(request);
                break;
            } catch (TransactionCanceledException e) {
                RuntimeException failure = translate(e);
                if (!(failure instanceof ConcurrentUpdateException) || attempt >= MAX_ATTEMPTS) {
                    throw failure;
                }
            }
        }
        return new Quota(Math.max(0, limit - used(day, acct, type.name())), limit);
    }

    public Quota quota(String account, Role role, JobType type) {
        int limit = limitFor(role);
        int used = used(LocalDate.now(clock), account.toLowerCase(Locale.ROOT), type.name());
        return new Quota(Math.max(0, limit - used), limit);
    }

    private int limitFor(Role role) {
        return role == Role.ADMIN ? props.deepAnalysisPerAdmin() : props.deepAnalysisPerUser();
    }

    private TransactWriteItem increment(LocalDate day, String account, String jobType, int limit) {
        long ttl = Ttl.epochSeconds(day.plusDays(TTL_DAYS).atStartOfDay(clock.getZone()).toInstant());
        return TransactWriteItem.builder().update(Update.builder()
                .tableName(table)
                .key(key(day, account, jobType))
                .updateExpression("ADD #c :one SET #t = :ttl")
                .conditionExpression("attribute_not_exists(#c) OR #c < :limit")
                .expressionAttributeNames(Map.of("#c", "count", "#t", TableDefinition.TTL))
                .expressionAttributeValues(Map.of(
                        ":one", AttributeValue.fromN("1"),
                        ":limit", AttributeValue.fromN(Integer.toString(limit)),
                        ":ttl", AttributeValue.fromN(Long.toString(ttl))))
                .build()).build();
    }

    /** Limit reached for a failed condition; anything else (e.g. TransactionConflict) is a retryable conflict. */
    private static RuntimeException translate(TransactionCanceledException e) {
        var reasons = e.cancellationReasons();
        if (reasons.size() > 0 && CONDITION_FAILED.equals(reasons.get(0).code())) {
            return new LimitReachedException(LimitReachedException.Scope.ACCOUNT);
        }
        if (reasons.size() > 1 && CONDITION_FAILED.equals(reasons.get(1).code())) {
            return new LimitReachedException(LimitReachedException.Scope.GLOBAL);
        }
        return new ConcurrentUpdateException("Usage counter update conflicted with a concurrent request", e);
    }

    private int used(LocalDate day, String account, String jobType) {
        Map<String, AttributeValue> item = dynamo.getItem(GetItemRequest.builder()
                .tableName(table)
                .key(key(day, account, jobType))
                .consistentRead(true)
                .build()).item();
        if (item.isEmpty() || !item.containsKey("count")) {
            return 0;
        }
        AttributeValue ttl = item.get(TableDefinition.TTL);
        if (ttl != null && Ttl.isExpired(Long.parseLong(ttl.n()), clock.instant())) {
            return 0;
        }
        return Integer.parseInt(item.get("count").n());
    }

    private static Map<String, AttributeValue> key(LocalDate day, String account, String jobType) {
        return Map.of(TableDefinition.PK, AttributeValue.fromS("USAGE#" + day),
                TableDefinition.SK, AttributeValue.fromS(account + "#" + jobType));
    }
}
