package com.valuescreener.research;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class InvestmentThesisMigrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void tableExistsAndAcceptsValidRows() {
        jdbc.update("INSERT INTO investment_thesis (isin, status, status_reason, updated_at) "
                + "VALUES ('US0378331005', 'WATCHING', 'pricey', now())");
        jdbc.update("INSERT INTO investment_thesis (isin, updated_at) VALUES ('US5949181045', now())");

        assertThat(jdbc.queryForObject("SELECT count(*) FROM investment_thesis", Integer.class)).isEqualTo(2);
    }

    @Test
    void unknownStatusIsRejected() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO investment_thesis (isin, status, updated_at) "
                + "VALUES ('US0378331005', 'HOLDING', now())"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void statusReasonWithoutStatusIsRejected() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO investment_thesis (isin, status_reason, updated_at) "
                + "VALUES ('US0378331005', 'why', now())"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
