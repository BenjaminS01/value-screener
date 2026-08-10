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
                "AAPL", "US0378331005", "Apple Inc.", "Information Technology", "USA",
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
        freshCopy.applyUpdate("Apple Inc.", "Information Technology", "USA",
                "Designs and sells consumer electronics.");
        repository.saveAndFlush(freshCopy);

        staleCopy.applyUpdate("Apple Inc.", "Information Technology", "USA",
                "Designs and sells consumer electronics.");
        assertThatThrownBy(() -> repository.saveAndFlush(staleCopy))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }
}
