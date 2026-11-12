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
class ResearchHistoryMigrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void historyTablesExistAndAreEmpty() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM research_finding_history", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM stock_group_history", Integer.class)).isZero();
    }

    @Test
    void findingHistoryRequiresExistingSnapshot() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO research_finding_history (company_snapshot_id, criterion_key, claim, as_of_date, replaced_at, origin)
                VALUES (999, 'PE_RATIO', 'x', '2026-01-01', now(), 'INGEST')"""))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void groupHistoryRequiresExistingSnapshot() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO stock_group_history (company_snapshot_id, new_group_key, new_group_source, changed_at, origin)
                VALUES (999, 'BANKS', 'AI', now(), 'INGEST')"""))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void groupHistoryRequiresExistingGroupKey() {
        jdbc.update("""
                INSERT INTO company_snapshot (id, ticker, isin, company_name, country, business_description)
                VALUES (1, 'AAPL', 'US0378331005', 'Apple', 'USA', 'x')""");

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO stock_group_history (company_snapshot_id, new_group_key, new_group_source, changed_at, origin)
                VALUES (1, 'NOPE', 'AI', now(), 'INGEST')"""))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
