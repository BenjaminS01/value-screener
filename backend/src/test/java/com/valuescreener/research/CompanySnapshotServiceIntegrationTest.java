package com.valuescreener.research;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=none")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({CompanySnapshotService.class, StockGroupService.class, ResearchFreshness.class,
        CompanySnapshotServiceIntegrationTest.ClockConfig.class})
class CompanySnapshotServiceIntegrationTest {

    @TestConfiguration
    static class ClockConfig {
        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-11-12T10:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String ISIN = "US0378331005";
    private static final LocalDate AS_OF = LocalDate.of(2026, 8, 1);

    @Autowired
    private CompanySnapshotService service;
    @Autowired
    private StockGroupService groupService;
    @Autowired
    private EntityManager em;
    @Autowired
    private JdbcTemplate jdbc;

    private UpsertCompanySnapshotRequest request(String groupKey, FindingRequest... findings) {
        return new UpsertCompanySnapshotRequest("AAPL", ISIN, "Apple", "USA", "Devices", groupKey, null,
                List.of(findings));
    }

    private FindingRequest pe(String value, String claim) {
        return new FindingRequest(ResearchCriterion.PE_RATIO, new BigDecimal(value), null, claim, null, AS_OF);
    }

    private CompanySnapshotView upsert(UpsertCompanySnapshotRequest request) {
        CompanySnapshotView view = service.upsert(request, GroupSource.AI, HistoryOrigin.INGEST);
        em.flush();
        em.clear();
        return view;
    }

    private long count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
    }

    @Test
    void historyIsWrittenOnlyForRealChangesAgainstARealDatabase() {
        // (a) new ISIN with group G1
        CompanySnapshotView created = upsert(request("BANKS", pe("24.1", "P/E is 24.1")));
        List<Map<String, Object>> groupRows = jdbc.queryForList("SELECT * FROM stock_group_history");
        assertThat(groupRows).hasSize(1);
        assertThat(((Number) groupRows.get(0).get("company_snapshot_id")).longValue()).isEqualTo(created.id());
        assertThat(groupRows.get(0).get("previous_group_key")).isNull();
        assertThat(groupRows.get(0).get("previous_source")).isNull();
        assertThat(groupRows.get(0).get("new_group_key")).isEqualTo("BANKS");
        assertThat(groupRows.get(0).get("origin")).isEqualTo("INGEST");
        assertThat(count("research_finding_history")).isZero();

        // (b) same findings, 24.10 vs 24.1, claim padded
        upsert(request("BANKS", pe("24.10", "  P/E is 24.1  ")));
        assertThat(count("research_finding_history")).isZero();
        assertThat(count("stock_group_history")).isEqualTo(1);

        // (c) more than 4 decimals, sent twice
        CompanySnapshotView first = upsert(request("BANKS", pe("28.47123", "P/E is 28.47")));
        assertThat(first.findings().get(0).numericValue()).isEqualByComparingTo("28.4712");
        long afterFirst = count("research_finding_history");
        upsert(request("BANKS", pe("28.47123", "P/E is 28.47")));
        assertThat(count("research_finding_history")).isEqualTo(afterFirst);
        assertThat(jdbc.queryForObject("SELECT numeric_value FROM research_finding", BigDecimal.class))
                .isEqualByComparingTo("28.4712");

        // (d) real change -> one history row with the OLD values
        upsert(request("BANKS", pe("30.0", "P/E is 30")));
        List<Map<String, Object>> findingRows = jdbc.queryForList(
                "SELECT * FROM research_finding_history ORDER BY id");
        assertThat(findingRows).hasSize((int) afterFirst + 1);
        Map<String, Object> last = findingRows.get(findingRows.size() - 1);
        assertThat((BigDecimal) last.get("numeric_value")).isEqualByComparingTo("28.4712");
        assertThat(last.get("claim")).isEqualTo("P/E is 28.47");
        assertThat(jdbc.queryForObject("SELECT numeric_value FROM research_finding", BigDecimal.class))
                .isEqualByComparingTo("30.0");

        // (e) manual assignment
        service.assignGroupManually(ISIN, "INSURANCE");
        em.flush();
        em.clear();
        assertThat(count("stock_group_history")).isEqualTo(2);
        service.assignGroupManually(ISIN, "INSURANCE");
        em.flush();
        em.clear();
        assertThat(count("stock_group_history")).isEqualTo(2);
        SnapshotHistoryView history = service.history(ISIN);
        assertThat(history.groups()).hasSize(2);
        assertThat(history.groups().get(0).origin()).isEqualTo(HistoryOrigin.MANUAL);
        assertThat(history.groups().get(0).newSource()).isEqualTo(GroupSource.MANUAL);
        assertThat(history.groups().get(0).newGroup().key()).isEqualTo("INSURANCE");
        assertThat(history.groups().get(1).origin()).isEqualTo(HistoryOrigin.INGEST);
        assertThat(history.findings()).hasSize((int) afterFirst + 1);

        // (f) BANKS is unused now but appears in the group history
        assertThatThrownBy(() -> groupService.delete("BANKS"))
                .isInstanceOf(StockGroupConflictException.class);
    }
}
