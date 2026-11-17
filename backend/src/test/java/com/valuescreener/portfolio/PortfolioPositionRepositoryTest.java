package com.valuescreener.portfolio;

import com.valuescreener.persistence.ConcurrentUpdateException;
import com.valuescreener.persistence.DynamoProperties;
import com.valuescreener.persistence.DynamoTableInitializer;
import com.valuescreener.persistence.DynamoTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PortfolioPositionRepositoryTest extends DynamoTestSupport {

    private final DynamoDbClient client = newClient();
    private PortfolioPositionRepository repository;

    @BeforeEach
    void setUp() {
        DynamoProperties props = new DynamoProperties(freshTableName(), endpoint(), "eu-central-1", true);
        new DynamoTableInitializer(client, props).run(null);
        repository = new DynamoPortfolioPositionRepository(
                DynamoDbEnhancedClient.builder().dynamoDbClient(client).build(), props);
    }

    private static PortfolioPosition position(String isin, String ticker) {
        return new PortfolioPosition(ticker, isin, "Company " + ticker, new BigDecimal("5"),
                new BigDecimal("300.00"), LocalDate.of(2026, 2, 1));
    }

    @Test
    void savesAndFindsByIsin() {
        PortfolioPosition saved = repository.save(position("US5949181045", "MSFT"));

        PortfolioPosition found = repository.findByIsin("US5949181045").orElseThrow();
        assertThat(found.getTicker()).isEqualTo("MSFT");
        assertThat(found.getCompanyName()).isEqualTo("Company MSFT");
        assertThat(found.getQuantity()).isEqualByComparingTo("5");
        assertThat(found.getEntryPrice()).isEqualByComparingTo("300.00");
        assertThat(found.getPurchaseDate()).isEqualTo(LocalDate.of(2026, 2, 1));
        assertThat(found.version()).isEqualTo(1L).isEqualTo(saved.version());
    }

    @Test
    void returnsEmptyWhenIsinNotFound() {
        assertThat(repository.findByIsin("US0000000000")).isEmpty();
    }

    @Test
    void findAllReturnsAllPositions() {
        repository.save(position("US5949181045", "MSFT"));
        repository.save(position("US0378331005", "AAPL"));

        assertThat(repository.findAll()).extracting(PortfolioPosition::getIsin)
                .containsExactlyInAnyOrder("US5949181045", "US0378331005");
    }

    @Test
    void secondCreateWithSameIsinIsRejected() {
        repository.save(position("US0378331005", "AAPL"));

        assertThatThrownBy(() -> repository.save(position("US0378331005", "AAPL")))
                .isInstanceOf(ConcurrentUpdateException.class);
    }

    @Test
    void staleSaveIsRejected() {
        repository.save(position("US0378331005", "AAPL"));
        PortfolioPosition first = repository.findByIsin("US0378331005").orElseThrow();
        PortfolioPosition second = repository.findByIsin("US0378331005").orElseThrow();

        first.recordPurchase(new BigDecimal("5"), new BigDecimal("160.00"));
        repository.save(first);
        assertThat(first.version()).isEqualTo(2L);

        second.recordPurchase(new BigDecimal("3"), new BigDecimal("170.00"));
        assertThatThrownBy(() -> repository.save(second)).isInstanceOf(ConcurrentUpdateException.class);
        assertThat(repository.findByIsin("US0378331005").orElseThrow().getQuantity()).isEqualByComparingTo("10");
    }

    @Test
    void repeatedSaveOfSameObjectKeepsWorking() {
        PortfolioPosition p = repository.save(position("US0378331005", "AAPL"));
        p.recordPurchase(new BigDecimal("1"), new BigDecimal("100"));
        repository.save(p);
        p.recordPurchase(new BigDecimal("1"), new BigDecimal("100"));
        repository.save(p);

        assertThat(repository.findByIsin("US0378331005").orElseThrow().version()).isEqualTo(3L);
    }

    @Test
    void deleteRemovesPosition() {
        PortfolioPosition saved = repository.save(position("US0378331005", "AAPL"));

        repository.delete(saved);

        assertThat(repository.findByIsin("US0378331005")).isEmpty();
    }

    @Test
    void deleteOfStaleCopyIsRejected() {
        repository.save(position("US0378331005", "AAPL"));
        PortfolioPosition stale = repository.findByIsin("US0378331005").orElseThrow();
        PortfolioPosition fresh = repository.findByIsin("US0378331005").orElseThrow();
        fresh.recordPurchase(new BigDecimal("1"), new BigDecimal("100"));
        repository.save(fresh);

        assertThatThrownBy(() -> repository.delete(stale)).isInstanceOf(ConcurrentUpdateException.class);
        assertThat(repository.findByIsin("US0378331005")).isPresent();
    }

    @Test
    void decimalsRoundTripExactly() {
        PortfolioPosition p = new PortfolioPosition("AAPL", "US0378331005", "Apple", new BigDecimal("0.123456"),
                new BigDecimal("12345.678901"), LocalDate.of(2026, 1, 1));
        repository.save(p);

        PortfolioPosition found = repository.findByIsin("US0378331005").orElseThrow();
        assertThat(found.getQuantity()).isEqualByComparingTo(new BigDecimal("0.123456"));
        assertThat(found.getEntryPrice()).isEqualByComparingTo(new BigDecimal("12345.678901"));
    }
}
