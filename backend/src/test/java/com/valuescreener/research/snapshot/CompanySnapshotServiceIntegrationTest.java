package com.valuescreener.research.snapshot;

import com.valuescreener.research.group.DynamoStockGroupRepository;
import com.valuescreener.research.group.StockGroup;
import com.valuescreener.research.group.StockGroupConflictException;
import com.valuescreener.research.group.StockGroupRepository;
import com.valuescreener.research.group.StockGroupService;
import com.valuescreener.research.group.StockGroupView;
import com.valuescreener.research.group.UnknownGroupException;
import com.valuescreener.research.history.DynamoResearchFindingHistoryRepository;
import com.valuescreener.research.history.HistoryOrigin;
import com.valuescreener.research.history.SnapshotHistoryView;
import com.valuescreener.persistence.DynamoProperties;
import com.valuescreener.persistence.DynamoTableInitializer;
import com.valuescreener.persistence.DynamoTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The snapshot service against a real DynamoDB (Local): history is written only for real changes. */
class CompanySnapshotServiceIntegrationTest extends DynamoTestSupport {

    private static final String ISIN = "US0378331005";
    private static final LocalDate AS_OF = LocalDate.of(2026, 8, 1);

    /** Moves forward on every call of {@link #tick()}, so history entries of separate saves have distinct times. */
    private static class SteppingClock extends Clock {
        private Instant now = Instant.parse("2026-11-12T10:00:00Z");

        void tick() {
            now = now.plusSeconds(1);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final DynamoDbClient client = newClient();
    private final SteppingClock clock = new SteppingClock();
    private CompanySnapshotService service;
    private StockGroupService groupService;
    private StockGroupRepository groupRepository;
    private CompanySnapshotRepository snapshots;

    @BeforeEach
    void setUp() {
        DynamoProperties props = new DynamoProperties(freshTableName(), endpoint(), "eu-central-1", true);
        new DynamoTableInitializer(client, props).run(null);
        groupRepository = new DynamoStockGroupRepository(
                DynamoDbEnhancedClient.builder().dynamoDbClient(client).build(), props);
        groupRepository.create(new StockGroup("BANKS", "Banks", 10));
        groupRepository.create(new StockGroup("INSURANCE", "Insurance", 20));
        snapshots = new DynamoCompanySnapshotRepository(client, props);
        groupService = new StockGroupService(groupRepository, snapshots);
        service = new CompanySnapshotService(snapshots, groupService,
                new ResearchFreshness(clock, java.time.Period.ofMonths(9)),
                new DynamoResearchFindingHistoryRepository(client, props), clock);
    }

    private UpsertCompanySnapshotRequest request(String groupKey, FindingRequest... findings) {
        return new UpsertCompanySnapshotRequest("AAPL", ISIN, "Apple", "USA", "Devices", groupKey, null,
                List.of(findings));
    }

    private FindingRequest pe(String value, String claim) {
        return new FindingRequest(ResearchCriterion.PE_RATIO, new BigDecimal(value), null, claim, null, AS_OF);
    }

    private CompanySnapshotView upsert(UpsertCompanySnapshotRequest request) {
        clock.tick();
        return service.upsert(request, GroupSource.AI, HistoryOrigin.INGEST);
    }

    @Test
    void historyIsWrittenOnlyForRealChangesAgainstARealDatabase() {
        // (a) new ISIN with group G1: no finding history
        upsert(request("BANKS", pe("24.1", "P/E is 24.1")));
        assertThat(service.history(ISIN).findings()).isEmpty();

        // (b) same findings, 24.10 vs 24.1, claim padded: no history, also after a round trip through DynamoDB
        upsert(request("BANKS", pe("24.10", "  P/E is 24.1  ")));
        assertThat(service.history(ISIN).findings()).isEmpty();

        // (c) more than 4 decimals, sent twice: rounded to 4 places, the second send is no change
        CompanySnapshotView first = upsert(request("BANKS", pe("28.47123", "P/E is 28.47")));
        assertThat(first.findings().get(0).numericValue()).isEqualByComparingTo("28.4712");
        int afterFirst = service.history(ISIN).findings().size();
        upsert(request("BANKS", pe("28.47123", "P/E is 28.47")));
        assertThat(service.history(ISIN).findings()).hasSize(afterFirst);
        assertThat(service.getByIsin(ISIN).findings().get(0).numericValue()).isEqualByComparingTo("28.4712");

        // (d) real change -> one more history entry with the OLD values
        upsert(request("BANKS", pe("30.0", "P/E is 30")));
        List<SnapshotHistoryView.FindingHistoryEntry> findingRows = service.history(ISIN).findings();
        assertThat(findingRows).hasSize(afterFirst + 1);
        SnapshotHistoryView.FindingHistoryEntry latest = findingRows.get(0);
        assertThat(latest.numericValue()).isEqualByComparingTo("28.4712");
        assertThat(latest.claim()).isEqualTo("P/E is 28.47");
        assertThat(latest.origin()).isEqualTo(HistoryOrigin.INGEST);
        assertThat(service.getByIsin(ISIN).findings().get(0).numericValue()).isEqualByComparingTo("30.0");

        // (e) manual assignment moves the stock; repeating it changes nothing and writes no finding history
        clock.tick();
        service.assignGroupManually(ISIN, "INSURANCE");
        clock.tick();
        service.assignGroupManually(ISIN, "INSURANCE");
        assertThat(service.getByIsin(ISIN).group())
                .isEqualTo(new SnapshotGroupView("INSURANCE", "Insurance", GroupSource.MANUAL));
        assertThat(service.history(ISIN).findings()).hasSize(afterFirst + 1);

        // (f) BANKS holds no stock now but was assigned once: it cannot be deleted
        assertThatThrownBy(() -> groupService.delete("BANKS"))
                .isInstanceOf(StockGroupConflictException.class)
                .hasMessage("Group BANKS was assigned to a stock and cannot be deleted");
    }

    @Test
    void aiProposalIsIgnoredWhenAManualGroupExists() {
        upsert(request("BANKS", pe("24.1", "P/E is 24.1")));
        clock.tick();
        service.assignGroupManually(ISIN, "INSURANCE");

        CompanySnapshotView view = upsert(request("BANKS", pe("24.1", "P/E is 24.1")));

        assertThat(view.group()).isEqualTo(new SnapshotGroupView("INSURANCE", "Insurance", GroupSource.MANUAL));
        assertThat(service.getByIsin(ISIN).group().source()).isEqualTo(GroupSource.MANUAL);
    }

    @Test
    void listsAndFiltersGroupsWithNamesFromTheCatalog() {
        upsert(request("BANKS", pe("24.1", "x")));
        service.upsert(new UpsertCompanySnapshotRequest("MSFT", "US5949181045", "Microsoft", "USA", "Software",
                "INSURANCE", null, List.of()), GroupSource.AI, HistoryOrigin.INGEST);

        assertThat(service.list(null)).hasSize(2);
        assertThat(service.list("banks")).singleElement().satisfies(v -> {
            assertThat(v.ticker()).isEqualTo("AAPL");
            assertThat(v.group().name()).isEqualTo("Banks");
        });
        assertThat(service.list("none")).isEmpty();
        assertThat(groupService.list()).extracting(StockGroupView::stockCount).containsExactly(1L, 1L);
    }

    @Test
    void groupWithAssignedStocksCannotBeDeleted() {
        upsert(request("BANKS", pe("24.1", "x")));

        assertThatThrownBy(() -> groupService.delete("BANKS"))
                .isInstanceOf(StockGroupConflictException.class)
                .hasMessage("Group BANKS still has 1 stock(s) assigned");
    }

    @Test
    void upsertIntoAGroupThatNoLongerExistsIsUnknownGroupAndWritesNothing() {
        groupRepository.create(new StockGroup("TEMP", "Temporary", 30));
        groupRepository.delete(groupRepository.findById("TEMP").orElseThrow());

        assertThatThrownBy(() -> upsert(request("TEMP", pe("24.1", "x"))))
                .isInstanceOf(UnknownGroupException.class);
        assertThat(snapshots.findAll()).isEmpty();
    }
}
