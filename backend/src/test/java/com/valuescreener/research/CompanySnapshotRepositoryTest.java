package com.valuescreener.research;

import com.valuescreener.persistence.ConcurrentUpdateException;
import com.valuescreener.persistence.DynamoProperties;
import com.valuescreener.persistence.DynamoTableInitializer;
import com.valuescreener.persistence.DynamoTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.CancellationReason;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompanySnapshotRepositoryTest extends DynamoTestSupport {

    private static final String ISIN = "US0378331005";
    private static final LocalDate AS_OF = LocalDate.of(2026, 8, 1);
    private static final Instant T0 = Instant.parse("2026-11-12T10:00:00Z");

    private final DynamoDbClient client = newClient();
    private DynamoProperties props;
    private CompanySnapshotRepository repository;
    private ResearchFindingHistoryRepository findingHistory;
    private StockGroupHistoryRepository groupHistory;
    private StockGroupRepository groups;

    @BeforeEach
    void setUp() {
        props = new DynamoProperties(freshTableName(), endpoint(), "eu-central-1", true);
        new DynamoTableInitializer(client, props).run(null);
        repository = new DynamoCompanySnapshotRepository(client, props);
        findingHistory = new DynamoResearchFindingHistoryRepository(client, props);
        groupHistory = new DynamoStockGroupHistoryRepository(client, props);
        groups = new DynamoStockGroupRepository(DynamoDbEnhancedClient.builder().dynamoDbClient(client).build(), props);
        groups.create(new StockGroup("BANKS", "Banks", 10));
        groups.create(new StockGroup("INSURANCE", "Insurance", 20));
    }

    private static CompanySnapshot snapshot(String ticker, String isin, String groupKey) {
        CompanySnapshot s = new CompanySnapshot(ticker, isin, ticker + " Inc.", "USA", "Business of " + ticker);
        if (groupKey != null) {
            s.assignGroup(new StockGroup(groupKey, "Name of " + groupKey, 10), GroupSource.AI);
        }
        return s;
    }

    private static StockGroupHistory firstAssignment(String isin, String groupKey) {
        return new StockGroupHistory(isin, null, null, groupKey, GroupSource.AI, T0, HistoryOrigin.INGEST);
    }

    private CompanySnapshot saveWithGroup(String ticker, String isin, String groupKey) {
        CompanySnapshot s = snapshot(ticker, isin, groupKey);
        return repository.save(s, List.of(), groupKey == null ? Optional.empty()
                : Optional.of(firstAssignment(isin, groupKey)));
    }

    private static ResearchFindingHistory replaced(String isin, String claim, Instant at) {
        return new ResearchFindingHistory(isin, new ReplacedFinding(ResearchCriterion.PE_RATIO,
                new BigDecimal("1.5"), null, claim, null, AS_OF), at, HistoryOrigin.INGEST);
    }

    private Map<String, AttributeValue> rawItem(String pk, String sk) {
        return client.getItem(GetItemRequest.builder().tableName(props.tableName()).consistentRead(true)
                .key(Map.of("pk", AttributeValue.fromS(pk), "sk", AttributeValue.fromS(sk))).build()).item();
    }

    private List<Map<String, AttributeValue>> rawPartition(String pk) {
        return client.query(QueryRequest.builder().tableName(props.tableName()).consistentRead(true)
                .keyConditionExpression("pk = :pk").expressionAttributeValues(
                        Map.of(":pk", AttributeValue.fromS(pk))).build()).items();
    }

    @Test
    void savesSnapshotWithFindingsAndReadsBack() {
        CompanySnapshot s = snapshot("AAPL", ISIN, "BANKS");
        ResearchCriterion[] criteria = ResearchCriterion.values();
        for (int i = 0; i < criteria.length; i++) {
            boolean numeric = i % 2 == 0;
            s.upsertFinding(criteria[i], numeric ? new BigDecimal("12.3456").add(BigDecimal.valueOf(i)) : null,
                    numeric ? null : (i % 4 == 1), "Claim " + i + " ä€", i % 3 == 0 ? null : "https://example.com/" + i,
                    AS_OF.plusDays(i));
        }
        assertThat(criteria).hasSize(18);

        CompanySnapshot saved = repository.save(s, List.of(), Optional.of(firstAssignment(ISIN, "BANKS")));

        assertThat(saved).isSameAs(s);
        assertThat(saved.version()).isEqualTo(1L);
        CompanySnapshot read = repository.findByIsin(ISIN).orElseThrow();
        assertThat(read.version()).isEqualTo(1L);
        assertThat(read.getTicker()).isEqualTo("AAPL");
        assertThat(read.getIsin()).isEqualTo(ISIN);
        assertThat(read.getCompanyName()).isEqualTo("AAPL Inc.");
        assertThat(read.getCountry()).isEqualTo("USA");
        assertThat(read.getBusinessDescription()).isEqualTo("Business of AAPL");
        assertThat(read.getGroupKey()).isEqualTo("BANKS");
        assertThat(read.getGroupSource()).isEqualTo(GroupSource.AI);
        assertThat(read.getFindings()).hasSize(18);
        for (int i = 0; i < criteria.length; i++) {
            ResearchFinding expected = finding(s, criteria[i]);
            ResearchFinding actual = finding(read, criteria[i]);
            assertThat(actual.getNumericValue()).isEqualTo(expected.getNumericValue()); // scale 4 as well
            assertThat(actual.getBooleanValue()).isEqualTo(expected.getBooleanValue());
            assertThat(actual.getClaim()).isEqualTo(expected.getClaim());
            assertThat(actual.getSourceUrl()).isEqualTo(expected.getSourceUrl());
            assertThat(actual.getAsOfDate()).isEqualTo(expected.getAsOfDate());
        }
        assertThat(repository.findByIsin("US0000000000")).isEmpty();
    }

    private static ResearchFinding finding(CompanySnapshot s, ResearchCriterion criterion) {
        return s.getFindings().stream().filter(x -> x.getCriterionKey() == criterion).findFirst().orElseThrow();
    }

    @Test
    void itemLayoutIsPartitionSnapshotSortIsinPrefix() {
        saveWithGroup("AAPL", ISIN, "BANKS");

        Map<String, AttributeValue> item = rawItem("SNAPSHOT", "ISIN#" + ISIN);
        assertThat(item.get("groupKey").s()).isEqualTo("BANKS");
        assertThat(item.get("groupSource").s()).isEqualTo("AI");
        assertThat(item.get("version").n()).isEqualTo("1");
        assertThat(item).containsKey("findings");
    }

    @Test
    void numbersAreStoredAsNumbersAndScaleDoesNotMatter() {
        CompanySnapshot s = snapshot("AAPL", ISIN, null);
        s.upsertFinding(ResearchCriterion.PE_RATIO, new BigDecimal("1.5"), null, "c", null, AS_OF);
        repository.save(s, List.of(), Optional.empty());

        Map<String, AttributeValue> item = rawItem("SNAPSHOT", "ISIN#" + ISIN);
        assertThat(item.get("findings").m().get("PE_RATIO").m().get("numericValue").n()).isNotNull();
        ResearchFinding read = repository.findByIsin(ISIN).orElseThrow().getFindings().get(0);
        assertThat(read.getNumericValue()).isEqualByComparingTo("1.5000");
        // a re-send of 1.5 vs the stored 1.5000 is not a change
        CompanySnapshot again = repository.findByIsin(ISIN).orElseThrow();
        assertThat(again.upsertFinding(ResearchCriterion.PE_RATIO, new BigDecimal("1.5"), null, "c", null, AS_OF))
                .isEmpty();
        assertThat(again.upsertFinding(ResearchCriterion.PE_RATIO, new BigDecimal("1.50000"), null, "c", null, AS_OF))
                .isEmpty();
    }

    @Test
    void findAllByGroupKeyAndWithoutGroup() {
        saveWithGroup("AAA", "US0000000001", "BANKS");
        saveWithGroup("BBB", "US0000000002", "INSURANCE");
        saveWithGroup("CCC", "US0000000003", null);

        assertThat(repository.findAllByGroupKey("BANKS")).extracting(CompanySnapshot::getTicker).containsExactly("AAA");
        assertThat(repository.findAllByGroupKey("MISSING")).isEmpty();
        assertThat(repository.findAllWithoutGroup()).extracting(CompanySnapshot::getTicker).containsExactly("CCC");
        assertThat(repository.findAll()).extracting(CompanySnapshot::getTicker).containsExactly("AAA", "BBB", "CCC");
    }

    @Test
    void findAllByIsinIn() {
        saveWithGroup("AAA", "US0000000001", "BANKS");
        saveWithGroup("BBB", "US0000000002", null);
        saveWithGroup("CCC", "US0000000003", "BANKS");

        Collection<CompanySnapshot> found = repository.findAllByIsinIn(
                List.of("US0000000001", "US0000000002", "US0000000009", "US0000000001"));

        assertThat(found).extracting(CompanySnapshot::getIsin)
                .containsExactlyInAnyOrder("US0000000001", "US0000000002");
        assertThat(repository.findAllByIsinIn(List.of())).isEmpty();
    }

    @Test
    void countByGroupKey() {
        saveWithGroup("AAA", "US0000000001", "BANKS");
        saveWithGroup("BBB", "US0000000002", "BANKS");
        saveWithGroup("CCC", "US0000000003", null);

        assertThat(repository.countByGroupKey("BANKS")).isEqualTo(2);
        assertThat(repository.countByGroupKey("INSURANCE")).isZero();
    }

    @Test
    void wholePartitionReadsFollowPagination() {
        // 60 items of ~30 KB: the partition is well over the 1 MB a single query page returns
        String big = "x".repeat(30_000);
        for (int i = 0; i < 60; i++) {
            String isin = String.format("US00000000%02d", i);
            CompanySnapshot s = new CompanySnapshot("T" + i, isin, "Name", "USA", big);
            s.assignGroup(new StockGroup("BANKS", "Banks", 10), GroupSource.AI);
            repository.save(s, List.of(), i == 0 ? Optional.of(firstAssignment(isin, "BANKS")) : Optional.empty());
        }

        assertThat(repository.findAll()).hasSize(60);
        assertThat(repository.findAllByGroupKey("BANKS")).hasSize(60);
        assertThat(repository.countByGroupKey("BANKS")).isEqualTo(60);
    }

    @Test
    void historyQueriesFollowPagination() {
        saveWithGroup("AAPL", ISIN, "BANKS");
        String big = "y".repeat(6_000);
        // 12 saves x 18 entries x 6 KB = ~1.3 MB in one partition (one query page returns at most 1 MB)
        for (int round = 0; round < 12; round++) {
            CompanySnapshot current = repository.findByIsin(ISIN).orElseThrow();
            List<ResearchFindingHistory> entries = new ArrayList<>();
            for (int i = 0; i < 18; i++) {
                entries.add(replaced(ISIN, big + round + "-" + i, T0.plusSeconds(round)));
            }
            repository.save(current, entries, Optional.empty());
        }

        assertThat(findingHistory.findBySnapshotIsin(ISIN)).hasSize(12 * 18);
    }

    @Test
    void staleSaveIsRejected() {
        saveWithGroup("AAPL", ISIN, "BANKS");
        CompanySnapshot first = repository.findByIsin(ISIN).orElseThrow();
        CompanySnapshot stale = repository.findByIsin(ISIN).orElseThrow();

        first.applyUpdate("Apple Inc.", "USA", "Newer description");
        repository.save(first, List.of(), Optional.empty());
        stale.applyUpdate("Apple Inc.", "USA", "Stale description");

        assertThatThrownBy(() -> repository.save(stale, List.of(), Optional.empty()))
                .isInstanceOf(ConcurrentUpdateException.class);
        CompanySnapshot read = repository.findByIsin(ISIN).orElseThrow();
        assertThat(read.getBusinessDescription()).isEqualTo("Newer description");
        assertThat(read.version()).isEqualTo(2L);
    }

    @Test
    void creatingTheSameIsinTwiceIsRejected() {
        saveWithGroup("AAPL", ISIN, null);

        assertThatThrownBy(() -> repository.save(snapshot("AAPL", ISIN, null), List.of(), Optional.empty()))
                .isInstanceOf(ConcurrentUpdateException.class);
    }

    @Test
    void historyIsReturnedNewestFirst() {
        saveWithGroup("AAPL", ISIN, "BANKS");
        CompanySnapshot current = repository.findByIsin(ISIN).orElseThrow();
        // three entries with the very same timestamp, written in one transaction in order a, b, c
        Instant same = T0.plusSeconds(60);
        repository.save(current, List.of(replaced(ISIN, "a", same), replaced(ISIN, "b", same),
                replaced(ISIN, "c", same), replaced(ISIN, "oldest", T0.minusSeconds(60)),
                replaced(ISIN, "newest", same.plusNanos(1))), Optional.empty());

        List<String> claims = findingHistory.findBySnapshotIsin(ISIN).stream()
                .map(ResearchFindingHistory::getClaim).toList();

        // newest timestamp first; equal timestamps in reverse order of writing (like ORDER BY ... DESC, id DESC)
        assertThat(claims).containsExactly("newest", "c", "b", "a", "oldest");
        assertThat(findingHistory.findBySnapshotIsin(ISIN).get(0).getReplacedAt()).isEqualTo(same.plusNanos(1));
    }

    @Test
    void historySortKeysAreFixedWidthUtcSoLexicalOrderIsTimeOrder() {
        saveWithGroup("AAPL", ISIN, "BANKS");
        CompanySnapshot current = repository.findByIsin(ISIN).orElseThrow();
        repository.save(current, List.of(replaced(ISIN, "x", Instant.parse("2026-11-12T10:00:00Z")),
                replaced(ISIN, "y", Instant.parse("2026-11-12T10:00:00.5Z")),
                replaced(ISIN, "z", Instant.parse("2026-11-12T10:00:00.000000001Z"))), Optional.empty());

        List<String> sortKeys = rawPartition("SNAPSHOT#" + ISIN).stream().map(i -> i.get("sk").s())
                .filter(k -> k.startsWith("FHIST#")).toList();

        assertThat(sortKeys).hasSize(3).allMatch(k -> k.matches(
                "FHIST#\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{9}Z#\\d{3}#[0-9a-f-]{36}"));
        assertThat(sortKeys.stream().map(k -> k.substring(6, 36)).toList())
                .containsExactly("2026-11-12T10:00:00.000000000Z", "2026-11-12T10:00:00.000000001Z",
                        "2026-11-12T10:00:00.500000000Z");
    }

    @Test
    void historyKeepsAllFieldsAndNumbersWithoutFalseScaleDifferences() {
        saveWithGroup("AAPL", ISIN, "BANKS");
        CompanySnapshot current = repository.findByIsin(ISIN).orElseThrow();
        ResearchFindingHistory entry = new ResearchFindingHistory(ISIN, new ReplacedFinding(
                ResearchCriterion.MOAT_ASSESSMENT, new BigDecimal("28.4712"), true, "Old claim",
                "https://example.com/x", AS_OF), T0, HistoryOrigin.SYNC);
        repository.save(current, List.of(entry), Optional.empty());

        ResearchFindingHistory read = findingHistory.findBySnapshotIsin(ISIN).get(0);

        assertThat(read.getSnapshotIsin()).isEqualTo(ISIN);
        assertThat(read.getCriterionKey()).isEqualTo(ResearchCriterion.MOAT_ASSESSMENT);
        assertThat(read.getNumericValue()).isEqualTo(new BigDecimal("28.4712"));
        assertThat(read.getBooleanValue()).isTrue();
        assertThat(read.getClaim()).isEqualTo("Old claim");
        assertThat(read.getSourceUrl()).isEqualTo("https://example.com/x");
        assertThat(read.getAsOfDate()).isEqualTo(AS_OF);
        assertThat(read.getReplacedAt()).isEqualTo(T0);
        assertThat(read.getOrigin()).isEqualTo(HistoryOrigin.SYNC);
    }

    @Test
    void historyNumbersComeBackWithScaleFour() {
        saveWithGroup("AAPL", ISIN, "BANKS");
        repository.save(repository.findByIsin(ISIN).orElseThrow(), List.of(replaced(ISIN, "c", T0)), Optional.empty());

        assertThat(findingHistory.findBySnapshotIsin(ISIN).get(0).getNumericValue())
                .isEqualTo(new BigDecimal("1.5000"));
    }

    @Test
    void groupHistoryIsStoredAndReadNewestFirst() {
        saveWithGroup("AAPL", ISIN, "BANKS");
        CompanySnapshot current = repository.findByIsin(ISIN).orElseThrow();
        current.assignGroup(new StockGroup("INSURANCE", "Insurance", 20), GroupSource.MANUAL);
        repository.save(current, List.of(), Optional.of(new StockGroupHistory(ISIN, "BANKS", GroupSource.AI,
                "INSURANCE", GroupSource.MANUAL, T0.plusSeconds(5), HistoryOrigin.MANUAL)));

        List<StockGroupHistory> read = groupHistory.findBySnapshotIsin(ISIN);

        assertThat(read).hasSize(2);
        assertThat(read.get(0).getPreviousGroupKey()).isEqualTo("BANKS");
        assertThat(read.get(0).getPreviousSource()).isEqualTo(GroupSource.AI);
        assertThat(read.get(0).getNewGroupKey()).isEqualTo("INSURANCE");
        assertThat(read.get(0).getNewSource()).isEqualTo(GroupSource.MANUAL);
        assertThat(read.get(0).getOrigin()).isEqualTo(HistoryOrigin.MANUAL);
        assertThat(read.get(0).getChangedAt()).isEqualTo(T0.plusSeconds(5));
        assertThat(read.get(1).getPreviousGroupKey()).isNull();
        assertThat(read.get(1).getPreviousSource()).isNull();
        assertThat(read.get(1).getSnapshotIsin()).isEqualTo(ISIN);
        assertThat(groupHistory.findBySnapshotIsin("US0000000000")).isEmpty();
    }

    @Test
    void historyAndSnapshotAreWrittenAtomically() {
        saveWithGroup("AAPL", ISIN, "BANKS");
        CompanySnapshot first = repository.findByIsin(ISIN).orElseThrow();
        CompanySnapshot stale = repository.findByIsin(ISIN).orElseThrow();
        repository.save(first, List.of(), Optional.empty());
        stale.assignGroup(new StockGroup("INSURANCE", "Insurance", 20), GroupSource.MANUAL);

        assertThatThrownBy(() -> repository.save(stale, List.of(replaced(ISIN, "lost", T0)),
                Optional.of(new StockGroupHistory(ISIN, "BANKS", GroupSource.AI, "INSURANCE",
                        GroupSource.MANUAL, T0, HistoryOrigin.MANUAL))))
                .isInstanceOf(ConcurrentUpdateException.class);

        assertThat(findingHistory.findBySnapshotIsin(ISIN)).isEmpty();
        assertThat(groupHistory.findBySnapshotIsin(ISIN)).hasSize(1); // only the first assignment
        assertThat(repository.findByIsin(ISIN).orElseThrow().getGroupKey()).isEqualTo("BANKS");
        // the reference mark of the group was part of the failed transaction too
        assertThat(rawItem("GROUP", "KEY#INSURANCE")).doesNotContainKey("referenced");
    }

    @Test
    void upsertFailsWhenGroupWasDeletedMeanwhile() {
        groups.create(new StockGroup("TEMP", "Temporary", 30));
        groups.delete(groups.findById("TEMP").orElseThrow());
        CompanySnapshot s = snapshot("AAPL", ISIN, "TEMP");

        assertThatThrownBy(() -> repository.save(s, List.of(replaced(ISIN, "x", T0)),
                Optional.of(firstAssignment(ISIN, "TEMP"))))
                .isInstanceOf(UnknownGroupException.class)
                .hasMessage("Unknown group key: TEMP");

        assertThat(repository.findByIsin(ISIN)).isEmpty();
        assertThat(rawPartition("SNAPSHOT#" + ISIN)).isEmpty();
        assertThat(s.version()).isNull();
    }

    @Test
    void markingAGroupReferencedBlocksItsDeletion() {
        saveWithGroup("AAPL", ISIN, "BANKS");
        CompanySnapshot moved = repository.findByIsin(ISIN).orElseThrow();
        moved.assignGroup(new StockGroup("INSURANCE", "Insurance", 20), GroupSource.MANUAL);
        repository.save(moved, List.of(), Optional.of(new StockGroupHistory(ISIN, "BANKS", GroupSource.AI,
                "INSURANCE", GroupSource.MANUAL, T0, HistoryOrigin.MANUAL)));

        // BANKS holds no snapshot any more but appears in the history
        assertThatThrownBy(() -> groups.delete(groups.findById("BANKS").orElseThrow()))
                .isInstanceOf(StockGroupConflictException.class);
    }

    @Test
    void worstCaseTransactionFits() {
        saveWithGroup("AAPL", ISIN, "BANKS");
        CompanySnapshot current = repository.findByIsin(ISIN).orElseThrow();
        // realistic worst case: every criterion replaced, claims of 5000 characters (snapshot item and the
        // 18 history items stay far below 400 KB each, the whole transaction far below 4 MB / 100 items)
        String claim = "z".repeat(5_000);
        List<ResearchFindingHistory> entries = new ArrayList<>();
        for (ResearchCriterion criterion : ResearchCriterion.values()) {
            current.upsertFinding(criterion, new BigDecimal("1.0"), null, claim, "https://example.com", AS_OF);
            entries.add(new ResearchFindingHistory(ISIN, new ReplacedFinding(criterion, new BigDecimal("2.0"),
                    null, claim, "https://example.com", AS_OF), T0, HistoryOrigin.INGEST));
        }
        current.assignGroup(new StockGroup("INSURANCE", "Insurance", 20), GroupSource.MANUAL);

        repository.save(current, entries, Optional.of(new StockGroupHistory(ISIN, "BANKS", GroupSource.AI,
                "INSURANCE", GroupSource.MANUAL, T0, HistoryOrigin.MANUAL)));

        assertThat(findingHistory.findBySnapshotIsin(ISIN)).hasSize(18);
        assertThat(groupHistory.findBySnapshotIsin(ISIN)).hasSize(2);
        assertThat(repository.findByIsin(ISIN).orElseThrow().getFindings()).hasSize(18);
    }

    @Test
    void cancellationReasonsAreMappedByIndex() {
        TransactionCanceledException snapshotFailed = canceled("ConditionalCheckFailed", "None", "None");
        TransactionCanceledException groupMissing = canceled("None", "None", "ConditionalCheckFailed");
        TransactionCanceledException locked = canceled("None", "TransactionConflict", "None");
        TransactionCanceledException lockedSnapshot = canceled("TransactionConflict", "None", "None");
        TransactionCanceledException other = canceled("None", "ValidationError", "None");

        assertThat(DynamoCompanySnapshotRepository.translate(snapshotFailed, "AAPL", "G", 3))
                .isInstanceOf(ConcurrentUpdateException.class);
        assertThat(DynamoCompanySnapshotRepository.translate(groupMissing, "AAPL", "G", 3))
                .isInstanceOf(UnknownGroupException.class).hasMessage("Unknown group key: G");
        assertThat(DynamoCompanySnapshotRepository.translate(locked, "AAPL", "G", 3))
                .isInstanceOf(ConcurrentUpdateException.class);
        assertThat(DynamoCompanySnapshotRepository.translate(lockedSnapshot, "AAPL", "G", 3))
                .isInstanceOf(ConcurrentUpdateException.class);
        assertThat(DynamoCompanySnapshotRepository.translate(other, "AAPL", "G", 3)).isSameAs(other);
        // without a group reference the last index is a history item: never "unknown group"
        assertThat(DynamoCompanySnapshotRepository.translate(groupMissing, "AAPL", null, 3)).isSameAs(groupMissing);
    }

    private static TransactionCanceledException canceled(String... codes) {
        List<CancellationReason> reasons = java.util.Arrays.stream(codes)
                .map(c -> CancellationReason.builder().code(c).build()).toList();
        return (TransactionCanceledException) TransactionCanceledException.builder()
                .cancellationReasons(reasons).message("canceled").build();
    }
}
