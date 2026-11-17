package com.valuescreener.security;

import com.valuescreener.persistence.DynamoProperties;
import com.valuescreener.persistence.TableDefinition;
import com.valuescreener.persistence.Ttl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.session.MapSession;
import org.springframework.session.SessionRepository;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.Delete;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.Put;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;

/**
 * Spring Session store in DynamoDB, so a login made on one Lambda instance is valid on all others.
 * Item layout: pk = "SESSION#" + id, sk = "SESSION"; created and lastAccessed (epoch millis),
 * maxInactiveSeconds, attrs (binary: Java-serialized HashMap of the attributes) and ttl
 * (lastAccessed + maxInactive, epoch seconds).
 *
 * <p>Writes are kept rare. Spring Session saves on every request, but a save only writes when the session
 * is new, its id changed (session fixation protection on login: old item deleted and new one put in one
 * transaction), an attribute changed, or the stored lastAccessed is at least {@code refreshInterval} old.
 * Expiry is therefore sliding with a granularity of the refresh interval: the stored ttl can be up to that
 * much earlier than the true last access. Writes of already stored sessions are conditional on the item
 * still existing, so a save that races with a logout on another instance is dropped instead of reviving it. MapSession does not track attribute changes, so
 * {@link DynamoSession} remembers the serialized attributes at load time, to compare at save.
 */
public class DynamoSessionRepository implements SessionRepository<DynamoSession> {

    private static final Logger log = LoggerFactory.getLogger(DynamoSessionRepository.class);
    private static final String PREFIX = "SESSION#";
    private static final String SK = "SESSION";

    private final DynamoDbClient dynamo;
    private final String table;
    private final Clock clock;
    private final Duration maxInactive;
    private final Duration refreshInterval;

    public DynamoSessionRepository(DynamoDbClient dynamo, DynamoProperties props, Clock clock,
                                   Duration maxInactive, Duration refreshInterval) {
        this.dynamo = dynamo;
        this.table = props.tableName();
        this.clock = clock;
        this.maxInactive = maxInactive;
        this.refreshInterval = refreshInterval;
    }

    @Override
    public DynamoSession createSession() {
        MapSession session = new MapSession();
        Instant now = clock.instant();
        session.setCreationTime(now);
        session.setLastAccessedTime(now);
        session.setMaxInactiveInterval(maxInactive);
        return new DynamoSession(session);
    }

    @Override
    public void save(DynamoSession session) {
        byte[] attributes = serializeAttributes(session);
        boolean idChanged = session.isStored() && !session.storedId.equals(session.getId());
        boolean write = !session.isStored() || idChanged
                || !Arrays.equals(attributes, session.storedAttributes)
                || !session.getLastAccessedTime().isBefore(session.storedLastAccessed.plus(refreshInterval));
        if (!write) {
            return;
        }
        Map<String, AttributeValue> item = toItem(session, attributes);
        try {
            if (idChanged) {
                // Both conditions: the old item must still exist (not logged out meanwhile) and the new id is free.
                dynamo.transactWriteItems(TransactWriteItemsRequest.builder().transactItems(
                        TransactWriteItem.builder().delete(Delete.builder().tableName(table).key(key(session.storedId))
                                .conditionExpression("attribute_exists(pk)").build()).build(),
                        TransactWriteItem.builder().put(Put.builder().tableName(table).item(item)
                                .conditionExpression("attribute_not_exists(pk)").build()).build()
                ).build());
            } else {
                PutItemRequest.Builder put = PutItemRequest.builder().tableName(table).item(item);
                if (session.isStored()) {
                    // A stored session that vanished was logged out (or expired) meanwhile: never revive it.
                    put.conditionExpression("attribute_exists(pk)");
                }
                dynamo.putItem(put.build());
            }
        } catch (ConditionalCheckFailedException e) {
            return; // invalidated by another instance
        } catch (TransactionCanceledException e) {
            if (e.cancellationReasons().stream().anyMatch(r -> "ConditionalCheckFailed".equals(r.code()))) {
                return; // invalidated by another instance
            }
            throw e;
        }
        session.storedId = session.getId();
        session.storedLastAccessed = Instant.ofEpochMilli(session.getLastAccessedTime().toEpochMilli());
        session.storedAttributes = attributes;
    }

    @Override
    public DynamoSession findById(String id) {
        Map<String, AttributeValue> item = dynamo.getItem(GetItemRequest.builder()
                .tableName(table).key(key(id)).consistentRead(true).build()).item();
        if (item.isEmpty()) {
            return null;
        }
        Instant lastAccessed = Instant.ofEpochMilli(Long.parseLong(item.get("lastAccessed").n()));
        Duration inactive = Duration.ofSeconds(Long.parseLong(item.get("maxInactiveSeconds").n()));
        if (!lastAccessed.plus(inactive).isAfter(clock.instant())) {
            return null;
        }
        MapSession map = new MapSession(id);
        map.setCreationTime(Instant.ofEpochMilli(Long.parseLong(item.get("created").n())));
        map.setLastAccessedTime(lastAccessed);
        map.setMaxInactiveInterval(inactive);
        DynamoSession session = new DynamoSession(map);
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> attributes = (Map<String, Object>) SerializationSupport.deserialize(
                    item.get("attrs").b().asByteArray());
            attributes.forEach(session::setAttribute);
        } catch (IOException | ClassNotFoundException | RuntimeException e) {
            // Rejected by the allow-list, written by an incompatible version or damaged: not a session.
            log.warn("A stored session cannot be read and is treated as missing: {}", e.toString());
            return null;
        }
        // Baseline is a fresh serialization of what was read, not the stored bytes, so the comparison in
        // save() compares like with like even if re-serialization is not byte-identical to the original.
        session.storedId = id;
        session.storedLastAccessed = lastAccessed;
        session.storedAttributes = serializeAttributes(session);
        return session;
    }

    @Override
    public void deleteById(String id) {
        dynamo.deleteItem(DeleteItemRequest.builder().tableName(table).key(key(id)).build());
    }

    private Map<String, AttributeValue> toItem(DynamoSession session, byte[] attributes) {
        Instant lastAccessed = session.getLastAccessedTime();
        Duration inactive = session.getMaxInactiveInterval();
        Map<String, AttributeValue> item = new HashMap<>(key(session.getId()));
        item.put("created", AttributeValue.fromN(Long.toString(session.getCreationTime().toEpochMilli())));
        item.put("lastAccessed", AttributeValue.fromN(Long.toString(lastAccessed.toEpochMilli())));
        item.put("maxInactiveSeconds", AttributeValue.fromN(Long.toString(inactive.toSeconds())));
        item.put("attrs", AttributeValue.fromB(SdkBytes.fromByteArray(attributes)));
        item.put(TableDefinition.TTL, AttributeValue.fromN(Long.toString(Ttl.epochSeconds(lastAccessed.plus(inactive)))));
        return item;
    }

    /** Attributes in a HashMap filled in sorted key order, so equal content gives equal bytes. */
    private static byte[] serializeAttributes(DynamoSession session) {
        HashMap<String, Object> attributes = new HashMap<>();
        for (String name : new TreeSet<>(session.getAttributeNames())) {
            attributes.put(name, session.getAttribute(name));
        }
        return SerializationSupport.serialize(attributes);
    }

    private static Map<String, AttributeValue> key(String id) {
        return Map.of(TableDefinition.PK, AttributeValue.fromS(PREFIX + id), TableDefinition.SK, AttributeValue.fromS(SK));
    }
}
