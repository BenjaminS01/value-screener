package com.valuescreener.research.group;

import com.valuescreener.persistence.DynamoProperties;
import com.valuescreener.persistence.DynamoTableInitializer;
import com.valuescreener.persistence.DynamoTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class StockGroupSeederTest extends DynamoTestSupport {

    private final DynamoDbClient client = newClient();
    private DynamoProperties props;
    private StockGroupRepository repository;
    private StockGroupSeeder seeder;

    @BeforeEach
    void setUp() {
        props = new DynamoProperties(freshTableName(), endpoint(), "eu-central-1", true);
        new DynamoTableInitializer(client, props).run(null);
        repository = new DynamoStockGroupRepository(
                DynamoDbEnhancedClient.builder().dynamoDbClient(client).build(), props);
        seeder = new StockGroupSeeder(client, props, repository, false);
    }

    @Test
    void seedsThirtyFiveGroupsWithKeysNamesAndOrder() {
        seeder.run(null);

        List<StockGroup> groups = repository.findAll().stream()
                .sorted(java.util.Comparator.comparingInt(StockGroup::getSortOrder)).toList();
        assertThat(groups).hasSize(35);
        assertThat(groups).extracting(StockGroup::getKey).doesNotHaveDuplicates();
        assertThat(groups).extracting(g -> g.getName().toLowerCase(Locale.ROOT)).doesNotHaveDuplicates();
        List<Integer> expectedOrders = new ArrayList<>();
        for (int i = 1; i <= 35; i++) {
            expectedOrders.add(i * 10);
        }
        assertThat(groups).extracting(StockGroup::getSortOrder).containsExactlyElementsOf(expectedOrders);
        assertGroup("BANKS", "Banks", 10);
        assertGroup("HOLDING_COMPANIES", "Holding Companies", 60);
        assertGroup("CONSUMER_SERVICES", "Consumer Services", 190);
        assertGroup("HEALTHCARE_SERVICES", "Healthcare Services", 250);
        assertGroup("OTHER", "Other", 350);
        assertThat(repository.findById("DIVERSIFIED_OTHER")).isEmpty();
    }

    @Test
    void secondStartDoesNotReseedDeletedGroup() {
        seeder.run(null);
        repository.delete(repository.findById("BANKS").orElseThrow());

        seeder.run(null);

        assertThat(repository.findById("BANKS")).isEmpty();
        assertThat(repository.findAll()).hasSize(34);
    }

    @Test
    void interruptedSeedIsCompletedOnNextStart() {
        StockGroupRepository failing = new StockGroupRepository() {
            private int calls;
            public java.util.Optional<StockGroup> findById(String key) { return repository.findById(key); }
            public List<StockGroup> findAll() { return repository.findAll(); }
            public boolean existsByNameIgnoreCase(String name) { return repository.existsByNameIgnoreCase(name); }
            public int maxSortOrder() { return repository.maxSortOrder(); }
            public StockGroup create(StockGroup g) {
                if (++calls > 12) {
                    throw new IllegalStateException("throttled");
                }
                return repository.create(g);
            }
            public StockGroup update(StockGroup g, String previousName) { return repository.update(g, previousName); }
            public void delete(StockGroup g) { repository.delete(g); }
        };

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> new StockGroupSeeder(client, props, failing, false).run(null))
                .isInstanceOf(IllegalStateException.class);
        assertThat(repository.findAll()).hasSize(12);
        assertThat(markerPresent()).isFalse();

        seeder.run(null);

        assertThat(repository.findAll()).hasSize(35);
        assertThat(markerPresent()).isTrue();
    }

    private boolean markerPresent() {
        return client.getItem(r -> r.tableName(props.tableName()).consistentRead(true).key(java.util.Map.of(
                "pk", software.amazon.awssdk.services.dynamodb.model.AttributeValue.fromS("META"),
                "sk", software.amazon.awssdk.services.dynamodb.model.AttributeValue.fromS("SEED#stock-groups-v1"))))
                .hasItem();
    }

    @Test
    void skipsGroupsThatAlreadyExist() {
        repository.create(new StockGroup("BANKS", "Retail Banks", 5));

        seeder.run(null);

        assertThat(repository.findAll()).hasSize(35);
        assertThat(repository.findById("BANKS").orElseThrow().getName()).isEqualTo("Retail Banks");
    }

    @Test
    void doesNothingWithoutEndpointCreateTableOrSeedFlag() {
        DynamoProperties awsLike = new DynamoProperties(props.tableName(), "", "eu-central-1", false);

        new StockGroupSeeder(client, awsLike, repository, false).run(null);

        assertThat(repository.findAll()).isEmpty();
    }

    @Test
    void seedFlagEnablesSeedingWithoutEndpointOrCreateTable() {
        DynamoProperties awsLike = new DynamoProperties(props.tableName(), "", "eu-central-1", false);

        new StockGroupSeeder(client, awsLike, repository, true).run(null);

        assertThat(repository.findAll()).hasSize(35);
    }

    @Test
    void runsAfterTableInitializer() {
        List<ApplicationRunner> runners = new ArrayList<>(List.of(seeder, new DynamoTableInitializer(client, props)));

        AnnotationAwareOrderComparator.sort(runners);

        assertThat(runners.get(0)).isInstanceOf(DynamoTableInitializer.class);
        assertThat(runners.get(1)).isInstanceOf(StockGroupSeeder.class);
    }

    private void assertGroup(String key, String name, int order) {
        StockGroup group = repository.findById(key).orElseThrow();
        assertThat(group.getName()).isEqualTo(name);
        assertThat(group.getSortOrder()).isEqualTo(order);
    }
}
