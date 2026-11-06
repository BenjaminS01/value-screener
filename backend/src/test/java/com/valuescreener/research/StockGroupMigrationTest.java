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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class StockGroupMigrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void seeds35GroupsInOrder() {
        List<String> keys = jdbc.queryForList("SELECT key FROM stock_group ORDER BY sort_order", String.class);

        assertThat(keys).hasSize(35);
        assertThat(keys.get(0)).isEqualTo("BANKS");
        assertThat(keys.get(34)).isEqualTo("DIVERSIFIED_OTHER");
        assertThat(jdbc.queryForObject("SELECT name FROM stock_group WHERE key = 'SEMICONDUCTORS'", String.class))
                .isEqualTo("Semiconductors");
        assertThat(jdbc.queryForObject("SELECT min(sort_order) FROM stock_group", Integer.class)).isEqualTo(10);
        assertThat(jdbc.queryForObject("SELECT max(sort_order) FROM stock_group", Integer.class)).isEqualTo(350);
    }

    @Test
    void groupKeyAndSourceMustBeSetTogether() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO company_snapshot (ticker, isin, company_name, sector, country, business_description, group_key)
                VALUES ('AAPL', 'US0378331005', 'Apple', 'IT', 'USA', 'x', 'BANKS')"""))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void snapshotWithGroupKeyAndSourceIsAccepted() {
        jdbc.update("""
                INSERT INTO company_snapshot (ticker, isin, company_name, country, business_description, group_key, group_source)
                VALUES ('AAPL', 'US0378331005', 'Apple', 'USA', 'x', 'BANKS', 'AI')""");

        assertThat(jdbc.queryForObject("SELECT count(*) FROM company_snapshot", Integer.class)).isEqualTo(1);
    }
}
