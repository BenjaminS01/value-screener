package com.valuescreener.research.group;

import com.valuescreener.persistence.ConcurrentUpdateException;
import com.valuescreener.persistence.DynamoProperties;
import com.valuescreener.persistence.DynamoTableInitializer;
import com.valuescreener.persistence.DynamoTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.CancellationReason;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StockGroupRepositoryTest extends DynamoTestSupport {

    private final DynamoDbClient client = newClient();
    private DynamoProperties props;
    private DynamoDbEnhancedClient enhanced;
    private StockGroupRepository repository;

    @BeforeEach
    void setUp() {
        props = new DynamoProperties(freshTableName(), endpoint(), "eu-central-1", true);
        new DynamoTableInitializer(client, props).run(null);
        enhanced = DynamoDbEnhancedClient.builder().dynamoDbClient(client).build();
        repository = new DynamoStockGroupRepository(enhanced, props);
    }

    @Test
    void createsAndFindsGroup() {
        StockGroup saved = repository.create(new StockGroup("BANKS", "Banks", 10));

        StockGroup found = repository.findById("BANKS").orElseThrow();
        assertThat(found.getName()).isEqualTo("Banks");
        assertThat(found.getSortOrder()).isEqualTo(10);
        assertThat(saved.version()).isEqualTo(1L).isEqualTo(found.version());
        assertThat(repository.findById("NOPE")).isEmpty();
        assertThat(repository.findAll()).extracting(StockGroup::getKey).containsExactly("BANKS");
    }

    @Test
    void createRejectsDuplicateKey() {
        repository.create(new StockGroup("BANKS", "Banks", 10));

        assertThatThrownBy(() -> repository.create(new StockGroup("BANKS", "Other name", 20)))
                .isInstanceOf(StockGroupConflictException.class)
                .hasMessage("Group key already exists: BANKS");
        assertThat(repository.existsByNameIgnoreCase("Other name")).isFalse();
    }

    @Test
    void createRejectsDuplicateNameIgnoringCase() {
        repository.create(new StockGroup("BANKS", "Banks", 10));

        assertThat(repository.existsByNameIgnoreCase("  bANKS ")).isTrue();
        assertThatThrownBy(() -> repository.create(new StockGroup("BANK2", "bAnKs", 20)))
                .isInstanceOf(StockGroupConflictException.class)
                .hasMessage("Group name already exists: bAnKs");
        assertThat(repository.findById("BANK2")).isEmpty();
    }

    @Test
    void renameFreesOldNameAndTakesNewOne() {
        StockGroup banks = repository.create(new StockGroup("BANKS", "Banks", 10));
        banks.rename("Banking");
        repository.update(banks, "Banks");

        assertThat(repository.findById("BANKS").orElseThrow().getName()).isEqualTo("Banking");
        assertThat(repository.existsByNameIgnoreCase("Banks")).isFalse();
        assertThat(repository.existsByNameIgnoreCase("Banking")).isTrue();
        repository.create(new StockGroup("OLD", "Banks", 20));
    }

    @Test
    void renameOntoExistingNameIsConflictAndChangesNothing() {
        StockGroup banks = repository.create(new StockGroup("BANKS", "Banks", 10));
        repository.create(new StockGroup("INSURANCE", "Insurance", 20));
        banks.rename("insurance");

        assertThatThrownBy(() -> repository.update(banks, "Banks"))
                .isInstanceOf(StockGroupConflictException.class)
                .hasMessage("Group name already exists: insurance");
        assertThat(repository.findById("BANKS").orElseThrow().getName()).isEqualTo("Banks");
        assertThat(repository.existsByNameIgnoreCase("Banks")).isTrue();
    }

    @Test
    void caseOnlyRenameAndSortOrderChangeWork() {
        StockGroup banks = repository.create(new StockGroup("BANKS", "Banks", 10));
        banks.rename("BANKS!");
        banks.rename("BANKS");
        banks.moveTo(15);
        repository.update(banks, "Banks");

        StockGroup found = repository.findById("BANKS").orElseThrow();
        assertThat(found.getName()).isEqualTo("BANKS");
        assertThat(found.getSortOrder()).isEqualTo(15);
        assertThat(found.version()).isEqualTo(2L).isEqualTo(banks.version());
        assertThat(repository.existsByNameIgnoreCase("banks")).isTrue();
    }

    @Test
    void staleSaveIsRejected() {
        repository.create(new StockGroup("BANKS", "Banks", 10));
        StockGroup first = repository.findById("BANKS").orElseThrow();
        StockGroup stale = repository.findById("BANKS").orElseThrow();

        first.moveTo(20);
        repository.update(first, "Banks");
        stale.moveTo(30);
        assertThatThrownBy(() -> repository.update(stale, "Banks")).isInstanceOf(ConcurrentUpdateException.class);

        StockGroup staleRename = repository.findById("BANKS").orElseThrow();
        StockGroup other = repository.findById("BANKS").orElseThrow();
        other.moveTo(40);
        repository.update(other, "Banks");
        staleRename.rename("Banking");
        assertThatThrownBy(() -> repository.update(staleRename, "Banks"))
                .isInstanceOf(ConcurrentUpdateException.class);
        assertThat(repository.existsByNameIgnoreCase("Banking")).isFalse();
        assertThat(repository.findById("BANKS").orElseThrow().getSortOrder()).isEqualTo(40);
    }

    @Test
    void updateKeepsReferencedFlag() {
        repository.create(new StockGroup("BANKS", "Banks", 10));
        mark("BANKS");
        StockGroup banks = repository.findById("BANKS").orElseThrow();
        banks.moveTo(99);
        repository.update(banks, "Banks");

        assertThatThrownBy(() -> repository.delete(repository.findById("BANKS").orElseThrow()))
                .isInstanceOf(StockGroupConflictException.class);
    }

    @Test
    void deleteSucceedsForUnreferencedGroupAndFreesName() {
        StockGroup banks = repository.create(new StockGroup("BANKS", "Banks", 10));

        repository.delete(banks);

        assertThat(repository.findById("BANKS")).isEmpty();
        assertThat(repository.existsByNameIgnoreCase("Banks")).isFalse();
        repository.create(new StockGroup("BANKS", "Banks", 10));
    }

    @Test
    void deleteFailsOnceGroupWasEverReferenced() {
        repository.create(new StockGroup("BANKS", "Banks", 10));
        mark("BANKS");
        StockGroup banks = repository.findById("BANKS").orElseThrow();

        assertThatThrownBy(() -> repository.delete(banks))
                .isInstanceOf(StockGroupConflictException.class)
                .hasMessage("Group BANKS appears in the group history and cannot be deleted");
        assertThat(repository.findById("BANKS")).isPresent();
        assertThat(repository.existsByNameIgnoreCase("Banks")).isTrue();
    }

    @Test
    void deleteWithStaleVersionIsRejected() {
        StockGroup stale = repository.create(new StockGroup("BANKS", "Banks", 10));
        StockGroup fresh = repository.findById("BANKS").orElseThrow();
        fresh.moveTo(20);
        repository.update(fresh, "Banks");

        assertThatThrownBy(() -> repository.delete(stale)).isInstanceOf(StockGroupConflictException.class);
        assertThat(repository.findById("BANKS")).isPresent();
    }

    @Test
    void existsByNameIsFalseForBlankOrNull() {
        assertThat(repository.existsByNameIgnoreCase("  ")).isFalse();
        assertThat(repository.existsByNameIgnoreCase("")).isFalse();
        assertThat(repository.existsByNameIgnoreCase(null)).isFalse();
    }

    @Test
    void markReferencedFailsForMissingGroupAndCreatesNothing() {
        assertThatThrownBy(() -> mark("GHOST"))
                .isInstanceOfSatisfying(TransactionCanceledException.class, e ->
                        assertThat(e.cancellationReasons()).extracting(CancellationReason::code)
                                .containsExactly("ConditionalCheckFailed"));
        assertThat(repository.findById("GHOST")).isEmpty();
        assertThat(client.getItem(r -> r.tableName(props.tableName()).consistentRead(true).key(java.util.Map.of(
                "pk", AttributeValue.fromS("GROUP"), "sk", AttributeValue.fromS("KEY#GHOST")))).hasItem()).isFalse();
    }

    @Test
    void maxSortOrderIsZeroWhenEmpty() {
        assertThat(repository.maxSortOrder()).isZero();
        repository.create(new StockGroup("A1", "A", 30));
        repository.create(new StockGroup("B1", "B", 70));
        assertThat(repository.maxSortOrder()).isEqualTo(70);
    }

    private void mark(String key) {
        client.transactWriteItems(r -> r.transactItems(
                StockGroupRepository.markReferenced(props.tableName(), key)));
    }
}
