package com.valuescreener.portfolio;

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

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PortfolioPositionRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private PortfolioPositionRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void savesAndFindsPositionByIsin() {
        repository.save(new PortfolioPosition(
                "MSFT", "US5949181045", "Microsoft Corp.", new BigDecimal("5"), new BigDecimal("300.00"), LocalDate.of(2026, 2, 1)));

        assertThat(repository.findByIsin("US5949181045")).isPresent();
    }

    @Test
    void returnsEmptyWhenIsinNotFound() {
        assertThat(repository.findByIsin("US0000000000")).isEmpty();
    }

    @Test
    void rejectsSaveOfAStaleCopyAfterAConcurrentUpdate() {
        PortfolioPosition saved = repository.saveAndFlush(new PortfolioPosition(
                "AAPL", "US0378331005", "Apple Inc.", new BigDecimal("10"), new BigDecimal("150.00"), LocalDate.of(2026, 1, 15)));
        Long id = saved.getId();

        PortfolioPosition staleCopy = repository.findById(id).orElseThrow();
        entityManager.detach(staleCopy);

        PortfolioPosition freshCopy = repository.findById(id).orElseThrow();
        freshCopy.recordPurchase(new BigDecimal("5"), new BigDecimal("160.00"));
        repository.saveAndFlush(freshCopy);

        staleCopy.recordPurchase(new BigDecimal("3"), new BigDecimal("170.00"));
        assertThatThrownBy(() -> repository.saveAndFlush(staleCopy))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }
}
