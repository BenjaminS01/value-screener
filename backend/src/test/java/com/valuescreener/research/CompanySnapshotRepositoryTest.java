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

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

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

    private CompanySnapshot snapshot(String ticker, String isin, String groupKey) {
        CompanySnapshot s = new CompanySnapshot(ticker, isin, ticker + " Inc.", "USA", "Business.");
        if (groupKey != null) {
            s.assignGroup(new StockGroup(groupKey, "Name of " + groupKey, 10), GroupSource.AI);
        }
        return entityManager.persist(s);
    }

    @Test
    void findsByIsinInWithGroupKey() {
        snapshot("AAA", "US0000000001", "TEST_ONE");
        snapshot("BBB", "US0000000002", null);
        snapshot("CCC", "US0000000003", "TEST_ONE");
        entityManager.flush();
        entityManager.clear();

        var found = repository.findAllByIsinIn(java.util.List.of("US0000000001", "US0000000002"));

        assertThat(found).extracting(CompanySnapshot::getIsin)
                .containsExactlyInAnyOrder("US0000000001", "US0000000002");
        assertThat(found).filteredOn(s -> s.getGroupKey() != null)
                .extracting(CompanySnapshot::getGroupKey).containsExactly("TEST_ONE");
    }

    @Test
    void findsAllByGroupKey() {
        snapshot("AAA", "US0000000001", "TEST_ONE");
        snapshot("BBB", "US0000000002", "TEST_TWO");
        entityManager.flush();
        entityManager.clear();

        assertThat(repository.findAllByGroupKey("TEST_ONE")).extracting(CompanySnapshot::getTicker)
                .containsExactly("AAA");
        assertThat(repository.findAllByGroupKey("MISSING")).isEmpty();
    }

    @Test
    void findsAllWithoutGroup() {
        snapshot("AAA", "US0000000001", "TEST_ONE");
        snapshot("BBB", "US0000000002", null);
        entityManager.flush();
        entityManager.clear();

        assertThat(repository.findAllByGroupKeyIsNull()).extracting(CompanySnapshot::getTicker)
                .containsExactly("BBB");
    }

    @Test
    void countsByGroupKey() {
        snapshot("AAA", "US0000000001", "TEST_ONE");
        snapshot("BBB", "US0000000002", "TEST_ONE");
        snapshot("CCC", "US0000000003", null);
        entityManager.flush();

        assertThat(repository.countByGroupKey("TEST_ONE")).isEqualTo(2);
        assertThat(repository.countByGroupKey("MISSING")).isZero();
    }

    @Test
    void groupKeysAreNotForeignKeysToThePostgresGroupTable() {
        // groups live in DynamoDB: a snapshot may carry any key, no stock_group row required
        snapshot("AAA", "US0000000001", "NOT_IN_POSTGRES");
        entityManager.flush();

        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.table_constraints
                WHERE constraint_type = 'FOREIGN KEY'
                  AND table_name IN ('company_snapshot', 'stock_group_history')
                  AND constraint_name LIKE '%group_key_fkey'""", Integer.class)).isZero();
    }
}
