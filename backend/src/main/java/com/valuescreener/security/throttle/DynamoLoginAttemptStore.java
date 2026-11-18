package com.valuescreener.security.throttle;

import com.valuescreener.persistence.ConcurrentUpdateException;
import com.valuescreener.persistence.DynamoProperties;
import com.valuescreener.persistence.TableDefinition;
import com.valuescreener.persistence.Ttl;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ReturnValue;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

import java.io.IOException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;
import com.valuescreener.security.session.SerializationSupport;

/**
 * Login throttle state in DynamoDB: pk = "LOGIN#" + key, sk = "STATE"; attributes state (binary, Java
 * serialization), version (optimistic lock) and ttl (from {@link State#expiresAt()}, absent when the
 * state never expires). Reads treat an item whose ttl has passed as absent, because DynamoDB deletes
 * expired items late.
 */
@Component
public class DynamoLoginAttemptStore implements LoginAttemptStore {

    static final int MAX_ATTEMPTS = 5;
    private static final String SORT_KEY = "STATE";

    private final DynamoDbClient dynamo;
    private final String table;
    private final Clock clock;

    public DynamoLoginAttemptStore(DynamoDbClient dynamo, DynamoProperties props, Clock clock) {
        this.dynamo = dynamo;
        this.table = props.tableName();
        this.clock = clock;
    }

    @Override
    public <S extends State> S update(String key, Class<S> type, UnaryOperator<S> change) {
        ConditionalCheckFailedException lastConflict = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            Map<String, AttributeValue> item = read(key);
            Long version = item.isEmpty() ? null : Long.parseLong(item.get("version").n());
            S next = change.apply(item.isEmpty() ? null : liveState(item, type));
            try {
                write(key, next, version);
                return next;
            } catch (ConditionalCheckFailedException e) {
                lastConflict = e;
                if (attempt < MAX_ATTEMPTS) {
                    pause(attempt);
                }
            }
        }
        throw new ConcurrentUpdateException("Login state " + keyKind(key) + " kept changing concurrently", lastConflict);
    }

    @Override
    public <S extends State> Optional<S> get(String key, Class<S> type) {
        Map<String, AttributeValue> item = read(key);
        return item.isEmpty() ? Optional.empty() : Optional.ofNullable(liveState(item, type));
    }

    @Override
    public void delete(String key) {
        dynamo.deleteItem(DeleteItemRequest.builder().tableName(table).key(key(key)).build());
    }

    /**
     * Account failures are plain attributes (failures, lockedUntil in epoch millis, ttl = lock end) and
     * are changed by UpdateItem expressions, so there is nothing to conflict on. Steps: (1) a lock that has
     * ended resets the count (conditional, only one writer per lock lifetime has anything to reset);
     * (2) ADD 1 to the count; (3) at the limit, set the lock. Without a lock no ttl is stored.
     */
    @Override
    public void recordAccountFailure(String key, Instant now, int lockAfter, Duration lockFor) {
        try {
            dynamo.updateItem(UpdateItemRequest.builder().tableName(table).key(key(key))
                    .updateExpression("SET failures = :zero REMOVE lockedUntil, #ttl")
                    .conditionExpression("attribute_exists(lockedUntil) AND lockedUntil <= :now")
                    .expressionAttributeNames(Map.of("#ttl", TableDefinition.TTL))
                    .expressionAttributeValues(Map.of(":zero", AttributeValue.fromN("0"),
                            ":now", AttributeValue.fromN(Long.toString(now.toEpochMilli()))))
                    .build());
        } catch (ConditionalCheckFailedException e) {
            // no lock, or still locked: nothing to reset
        }
        int failures = Integer.parseInt(dynamo.updateItem(UpdateItemRequest.builder().tableName(table).key(key(key))
                .updateExpression("ADD failures :one")
                .expressionAttributeValues(Map.of(":one", AttributeValue.fromN("1")))
                .returnValues(ReturnValue.UPDATED_NEW).build()).attributes().get("failures").n());
        if (failures >= lockAfter) {
            Instant until = now.plus(lockFor);
            dynamo.updateItem(UpdateItemRequest.builder().tableName(table).key(key(key))
                    .updateExpression("SET lockedUntil = :until, #ttl = :ttl")
                    .expressionAttributeNames(Map.of("#ttl", TableDefinition.TTL))
                    .expressionAttributeValues(Map.of(
                            ":until", AttributeValue.fromN(Long.toString(until.toEpochMilli())),
                            ":ttl", AttributeValue.fromN(Long.toString(Ttl.epochSeconds(until)))))
                    .build());
        }
    }

    @Override
    public Optional<Instant> accountLockedUntil(String key) {
        Map<String, AttributeValue> item = read(key);
        AttributeValue until = item.get("lockedUntil");
        AttributeValue ttl = item.get(TableDefinition.TTL);
        if (until == null || (ttl != null && Ttl.isExpired(Long.parseLong(ttl.n()), clock.instant()))) {
            return Optional.empty();
        }
        return Optional.of(Instant.ofEpochMilli(Long.parseLong(until.n())));
    }

    private Map<String, AttributeValue> read(String key) {
        return dynamo.getItem(GetItemRequest.builder().tableName(table).key(key(key)).consistentRead(true).build()).item();
    }

    /** The stored state, or null when its ttl has passed or it cannot be read. */
    private <S extends State> S liveState(Map<String, AttributeValue> item, Class<S> type) {
        AttributeValue ttl = item.get(TableDefinition.TTL);
        if (ttl != null && Ttl.isExpired(Long.parseLong(ttl.n()), clock.instant())) {
            return null;
        }
        try {
            return type.cast(SerializationSupport.deserialize(item.get("state").b().asByteArray()));
        } catch (IOException | ClassNotFoundException | ClassCastException e) {
            return null; // damaged or from an incompatible version: start over
        }
    }

    private void write(String key, State state, Long version) {
        Map<String, AttributeValue> item = new HashMap<>(key(key));
        item.put("state", AttributeValue.fromB(SdkBytes.fromByteArray(SerializationSupport.serialize(state))));
        item.put("version", AttributeValue.fromN(Long.toString(version == null ? 1 : version + 1)));
        Instant expiresAt = state.expiresAt();
        if (expiresAt != null) {
            item.put(TableDefinition.TTL, AttributeValue.fromN(Long.toString(Ttl.epochSeconds(expiresAt))));
        }
        PutItemRequest.Builder request = PutItemRequest.builder().tableName(table).item(item);
        if (version == null) {
            request.conditionExpression("attribute_not_exists(pk)");
        } else {
            request.conditionExpression("#v = :v")
                    .expressionAttributeNames(Map.of("#v", "version"))
                    .expressionAttributeValues(Map.of(":v", AttributeValue.fromN(version.toString())));
        }
        dynamo.putItem(request.build());
    }

    /**
     * SecureRandom, not ThreadLocalRandom: with Lambda SnapStart the state of ThreadLocalRandom is part of the
     * snapshot, so every restored instance would replay the same jitter. The Lambda Java runtime reseeds
     * SecureRandom after a restore.
     */
    private static final SecureRandom JITTER = new SecureRandom();

    /** Short random pause, so writers that collided do not collide again in lock step. */
    private static void pause(int attempt) {
        try {
            Thread.sleep(JITTER.nextLong(1, 50L << attempt));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Key without the identifier (an IP or account name) so errors and logs do not carry it. */
    private static String keyKind(String key) {
        int hash = key.indexOf('#');
        return hash < 0 ? "?" : key.substring(0, hash);
    }

    private static Map<String, AttributeValue> key(String key) {
        return Map.of(TableDefinition.PK, AttributeValue.fromS("LOGIN#" + key),
                TableDefinition.SK, AttributeValue.fromS(SORT_KEY));
    }
}
