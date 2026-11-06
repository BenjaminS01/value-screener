package com.valuescreener.research;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CompanySnapshotRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private CompanySnapshotRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    private static CompanySnapshot newSnapshot() {
        return new CompanySnapshot(
                "AAPL", "US0378331005", "Apple Inc.", "USA",
                "Designs and sells consumer electronics.");
    }

    @Test
    void savesAndFindsSnapshotByIsin() {
        repository.save(newSnapshot());

        assertThat(repository.findByIsin("US0378331005")).isPresent();
    }

    @Test
    void returnsEmptyWhenIsinNotFound() {
        assertThat(repository.findByIsin("US0000000000")).isEmpty();
    }

    @Test
    void rejectsSaveOfAStaleCopyAfterAConcurrentUpdate() {
        CompanySnapshot saved = repository.saveAndFlush(newSnapshot());
        Long id = saved.getId();

        CompanySnapshot staleCopy = repository.findById(id).orElseThrow();
        entityManager.detach(staleCopy);

        CompanySnapshot freshCopy = repository.findById(id).orElseThrow();
        freshCopy.applyUpdate("Apple Inc.", "USA",
                "Designs and sells consumer electronics and wearables.");
        repository.saveAndFlush(freshCopy);

        staleCopy.applyUpdate("Apple Inc.", "USA",
                "Designs and sells consumer electronics and services.");
        assertThatThrownBy(() -> repository.saveAndFlush(staleCopy))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    private StockGroup group(String key, String name, int order) {
        return entityManager.persist(new StockGroup(key, name, order));
    }

    private CompanySnapshot snapshot(String ticker, String isin, StockGroup group) {
        CompanySnapshot s = new CompanySnapshot(ticker, isin, ticker + " Inc.", "USA", "Business.");
        if (group != null) {
            s.assignGroup(group, GroupSource.AI);
        }
        return entityManager.persist(s);
    }

    @Test
    void findsByIsinInWithGroupLoaded() {
        StockGroup test1 = group("TEST_ONE", "Test one", 9001);
        snapshot("AAA", "US0000000001", test1);
        snapshot("BBB", "US0000000002", null);
        snapshot("CCC", "US0000000003", test1);
        entityManager.flush();
        entityManager.clear();

        var found = repository.findAllByIsinIn(java.util.List.of("US0000000001", "US0000000002"));

        assertThat(found).extracting(CompanySnapshot::getIsin)
                .containsExactlyInAnyOrder("US0000000001", "US0000000002");
        assertThat(found).filteredOn(s -> s.getStockGroup() != null)
                .allMatch(s -> org.hibernate.Hibernate.isInitialized(s.getStockGroup()));
    }

    @Test
    void findsAllByGroupKey() {
        StockGroup test1 = group("TEST_ONE", "Test one", 9001);
        StockGroup test2 = group("TEST_TWO", "Test two", 9002);
        snapshot("AAA", "US0000000001", test1);
        snapshot("BBB", "US0000000002", test2);
        entityManager.flush();
        entityManager.clear();

        assertThat(repository.findAllByStockGroup_Key("TEST_ONE")).extracting(CompanySnapshot::getTicker)
                .containsExactly("AAA");
        assertThat(repository.findAllByStockGroup_Key("MISSING")).isEmpty();
    }

    @Test
    void findsAllWithoutGroup() {
        StockGroup test1 = group("TEST_ONE", "Test one", 9001);
        snapshot("AAA", "US0000000001", test1);
        snapshot("BBB", "US0000000002", null);
        entityManager.flush();
        entityManager.clear();

        assertThat(repository.findAllByStockGroupIsNull()).extracting(CompanySnapshot::getTicker)
                .containsExactly("BBB");
    }
}
