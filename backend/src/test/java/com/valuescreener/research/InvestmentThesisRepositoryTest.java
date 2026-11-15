package com.valuescreener.research;

import com.valuescreener.persistence.ConcurrentUpdateException;
import com.valuescreener.persistence.DynamoProperties;
import com.valuescreener.persistence.DynamoTableInitializer;
import com.valuescreener.persistence.DynamoTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InvestmentThesisRepositoryTest extends DynamoTestSupport {

    private static final String ISIN = "US0378331005";
    private static final Instant NOW = Instant.parse("2026-11-12T10:00:00Z");

    private final DynamoDbClient client = newClient();
    private InvestmentThesisRepository repository;

    @BeforeEach
    void setUp() {
        DynamoProperties props = new DynamoProperties(freshTableName(), endpoint(), "eu-central-1", true);
        new DynamoTableInitializer(client, props).run(null);
        repository = new DynamoInvestmentThesisRepository(
                DynamoDbEnhancedClient.builder().dynamoDbClient(client).build(), props);
    }

    private static InvestmentThesis thesis() {
        InvestmentThesis t = new InvestmentThesis(ISIN, NOW);
        t.replace("why", "expected", "exit", ThesisStatus.WATCHING, "pricey", NOW);
        return t;
    }

    @Test
    void savesAndFindsById() {
        InvestmentThesis saved = repository.save(thesis());

        InvestmentThesis found = repository.findById(ISIN).orElseThrow();
        assertThat(found.getIsin()).isEqualTo(ISIN);
        assertThat(found.getWhyInteresting()).isEqualTo("why");
        assertThat(found.getExpectedDevelopment()).isEqualTo("expected");
        assertThat(found.getExitCriteria()).isEqualTo("exit");
        assertThat(found.getStatus()).isEqualTo(ThesisStatus.WATCHING);
        assertThat(found.getStatusReason()).isEqualTo("pricey");
        assertThat(found.getUpdatedAt()).isEqualTo(NOW);
        assertThat(found.getVersion()).isEqualTo(1L).isEqualTo(saved.getVersion());
        assertThat(repository.findById("US5949181045")).isEmpty();
    }

    @Test
    void concurrentFirstSaveIsRejected() {
        repository.save(thesis());

        assertThatThrownBy(() -> repository.save(thesis())).isInstanceOf(ConcurrentUpdateException.class);
    }

    @Test
    void staleSaveIsRejected() {
        repository.save(thesis());
        InvestmentThesis first = repository.findById(ISIN).orElseThrow();
        InvestmentThesis second = repository.findById(ISIN).orElseThrow();

        first.replace("a", null, null, null, null, NOW);
        repository.save(first);
        assertThat(first.getVersion()).isEqualTo(2L);

        second.replace("b", null, null, null, null, NOW);
        assertThatThrownBy(() -> repository.save(second)).isInstanceOf(ConcurrentUpdateException.class);
        assertThat(repository.findById(ISIN).orElseThrow().getWhyInteresting()).isEqualTo("a");
    }

    @Test
    void nullTextsRoundTrip() {
        repository.save(new InvestmentThesis(ISIN, NOW));

        InvestmentThesis found = repository.findById(ISIN).orElseThrow();
        assertThat(found.getWhyInteresting()).isNull();
        assertThat(found.getExpectedDevelopment()).isNull();
        assertThat(found.getExitCriteria()).isNull();
        assertThat(found.getStatus()).isNull();
        assertThat(found.getStatusReason()).isNull();
    }
}
