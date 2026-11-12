package com.valuescreener.research;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanySnapshotServiceTest {

    private static final StockGroup SEMIS = new StockGroup("SEMICONDUCTORS", "Semiconductors", 10);
    private static final StockGroup BANKS = new StockGroup("BANKS", "Banks", 20);

    @Mock
    private CompanySnapshotRepository repository;

    @Mock
    private StockGroupService groups;

    private final ResearchFreshness freshness = new ResearchFreshness(
            Clock.fixed(Instant.parse("2026-11-12T10:00:00Z"), ZoneOffset.UTC), Period.ofMonths(9));

    @Mock
    private ResearchFindingHistoryRepository findingHistory;

    @Mock
    private StockGroupHistoryRepository groupHistory;

    private final Clock clock = Clock.fixed(Instant.parse("2026-11-12T10:00:00Z"), ZoneOffset.UTC);

    private CompanySnapshotService service;

    @BeforeEach
    void setUp() {
        service = new CompanySnapshotService(repository, groups, freshness, findingHistory, groupHistory, clock);
    }

    private static FindingRequest peRatioFinding(String value) {
        return new FindingRequest(ResearchCriterion.PE_RATIO, new BigDecimal(value), null,
                "Trailing P/E of " + value + ".", "https://example.com/aapl-key-stats",
                LocalDate.of(2026, 8, 1));
    }

    private static UpsertCompanySnapshotRequest request(String groupKey, String description,
                                                        List<FindingRequest> findings) {
        return new UpsertCompanySnapshotRequest("aapl", "US0378331005", "Apple Inc.", "USA", description,
                groupKey, null, findings);
    }

    private static CompanySnapshot existing() {
        return new CompanySnapshot("AAPL", "US0378331005", "Apple Inc.", "USA",
                "Designs and sells consumer electronics.");
    }

    @Test
    void upsertCreatesNewSnapshotWhenIsinUnknown() {
        when(repository.findByIsin("US0378331005")).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(groups.require("SEMICONDUCTORS")).thenReturn(SEMIS);

        CompanySnapshotView result = service.upsert(request("SEMICONDUCTORS",
                "Designs and sells consumer electronics.", List.of(peRatioFinding("28.0"))), GroupSource.AI, HistoryOrigin.INGEST);

        assertThat(result.ticker()).isEqualTo("AAPL");
        assertThat(result.group()).isEqualTo(new SnapshotGroupView("SEMICONDUCTORS", "Semiconductors", GroupSource.AI));
        assertThat(result.findings()).hasSize(1);
        assertThat(result.findings().get(0).criterionKey()).isEqualTo(ResearchCriterion.PE_RATIO);
        assertThat(result.findings().get(0).numericValue()).isEqualByComparingTo("28.0");
    }

    @Test
    void upsertOnExistingSnapshotPreservesFindingsNotIncludedInThisRequest() {
        CompanySnapshot existing = existing();
        existing.upsertFinding(ResearchCriterion.PE_RATIO, new BigDecimal("28.0"), null,
                "Trailing P/E of 28.0.", "https://example.com/first-source", LocalDate.of(2026, 8, 1));
        when(repository.findByIsin("US0378331005")).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(groups.require("SEMICONDUCTORS")).thenReturn(SEMIS);

        CompanySnapshotView result = service.upsert(request("SEMICONDUCTORS",
                "Designs and sells consumer electronics and services.",
                List.of(new FindingRequest(ResearchCriterion.MOAT_ASSESSMENT, null, null,
                        "Wide moat from ecosystem lock-in.", "https://example.com/moat-analysis",
                        LocalDate.of(2026, 8, 5)))), GroupSource.AI, HistoryOrigin.INGEST);

        assertThat(result.businessDescription())
                .isEqualTo("Designs and sells consumer electronics and services.");
        assertThat(result.findings()).hasSize(2);
        assertThat(result.findings())
                .extracting(FindingView::criterionKey)
                .containsExactlyInAnyOrder(ResearchCriterion.PE_RATIO, ResearchCriterion.MOAT_ASSESSMENT);

        ArgumentCaptor<CompanySnapshot> captor = ArgumentCaptor.forClass(CompanySnapshot.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue()).isSameAs(existing);
    }

    @Test
    void upsertOfSameCriterionAgainReplacesRatherThanDuplicates() {
        CompanySnapshot existing = existing();
        existing.upsertFinding(ResearchCriterion.PE_RATIO, new BigDecimal("28.0"), null,
                "Trailing P/E of 28.0.", "https://example.com/first-source", LocalDate.of(2026, 8, 1));
        when(repository.findByIsin("US0378331005")).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(groups.require("SEMICONDUCTORS")).thenReturn(SEMIS);

        CompanySnapshotView result = service.upsert(request("SEMICONDUCTORS",
                "Designs and sells consumer electronics.", List.of(peRatioFinding("29.5"))), GroupSource.AI, HistoryOrigin.INGEST);

        assertThat(result.findings()).hasSize(1);
        assertThat(result.findings().get(0).numericValue()).isEqualByComparingTo("29.5");
    }

    @Test
    void upsertUnknownGroupThrowsUnknownGroup() {
        when(repository.findByIsin("US0378331005")).thenReturn(Optional.empty());
        when(groups.require("NOPE")).thenThrow(new UnknownGroupException("NOPE"));

        assertThatThrownBy(() -> service.upsert(request("NOPE", "x", List.of()), GroupSource.AI, HistoryOrigin.INGEST))
                .isInstanceOf(UnknownGroupException.class);
    }

    @Test
    void upsertNormalizesGroupKey() {
        when(repository.findByIsin("US0378331005")).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(groups.require("SEMICONDUCTORS")).thenReturn(SEMIS);

        CompanySnapshotView result = service.upsert(request(" semiconductors ", "x", List.of()), GroupSource.AI, HistoryOrigin.INGEST);

        assertThat(result.group().key()).isEqualTo("SEMICONDUCTORS");
        verify(groups).require("SEMICONDUCTORS");
    }

    @Test
    void secondAiResearchKeepsManualGroup() {
        CompanySnapshot existing = existing();
        existing.assignGroup(BANKS, GroupSource.MANUAL);
        when(repository.findByIsin("US0378331005")).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(groups.require("SEMICONDUCTORS")).thenReturn(SEMIS);

        CompanySnapshotView result = service.upsert(request("SEMICONDUCTORS", "x", List.of()), GroupSource.AI, HistoryOrigin.INGEST);

        assertThat(result.group()).isEqualTo(new SnapshotGroupView("BANKS", "Banks", GroupSource.MANUAL));
    }

    @Test
    void assignGroupManuallySetsManual() {
        CompanySnapshot existing = existing();
        existing.assignGroup(SEMIS, GroupSource.AI);
        when(repository.findByIsin("US0378331005")).thenReturn(Optional.of(existing));
        when(groups.require("BANKS")).thenReturn(BANKS);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CompanySnapshotView result = service.assignGroupManually("US0378331005", "BANKS");

        assertThat(result.group()).isEqualTo(new SnapshotGroupView("BANKS", "Banks", GroupSource.MANUAL));
    }

    @Test
    void assignGroupManuallyNormalizesIsin() {
        CompanySnapshot existing = existing();
        when(repository.findByIsin("US0378331005")).thenReturn(Optional.of(existing));
        when(groups.require("BANKS")).thenReturn(BANKS);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CompanySnapshotView result = service.assignGroupManually(" us0378331005 ", "BANKS");

        assertThat(result.isin()).isEqualTo("US0378331005");
        assertThat(result.group().source()).isEqualTo(GroupSource.MANUAL);
    }

    @Test
    void assignGroupManuallyThrowsWhenSnapshotMissing() {
        when(repository.findByIsin("US0378331005")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assignGroupManually("US0378331005", "BANKS"))
                .isInstanceOf(CompanySnapshotNotFoundException.class);
    }

    @Test
    void listFiltersByGroupAndNone() {
        CompanySnapshot grouped = existing();
        grouped.assignGroup(SEMIS, GroupSource.AI);
        CompanySnapshot ungrouped = new CompanySnapshot("MSFT", "US5949181045", "Microsoft", "USA", "Software.");
        when(repository.findAll()).thenReturn(List.of(grouped, ungrouped));
        when(repository.findAllByStockGroupIsNull()).thenReturn(List.of(ungrouped));
        when(repository.findAllByStockGroup_Key("SEMICONDUCTORS")).thenReturn(List.of(grouped));
        when(repository.findAllByStockGroup_Key("NOPE")).thenReturn(List.of());

        assertThat(service.list(null)).hasSize(2);
        assertThat(service.list("none")).extracting(CompanySnapshotView::ticker).containsExactly("MSFT");
        assertThat(service.list(" semiconductors ")).extracting(CompanySnapshotView::ticker).containsExactly("AAPL");
        assertThat(service.list("nope")).isEmpty();
    }

    @Test
    void getByIsinReturnsSnapshotWhenFound() {
        when(repository.findByIsin("US0378331005")).thenReturn(Optional.of(existing()));

        CompanySnapshotView view = service.getByIsin("US0378331005");

        assertThat(view.isin()).isEqualTo("US0378331005");
        assertThat(view.group()).isNull();
    }

    @Test
    void getByIsinThrowsWhenNotFound() {
        when(repository.findByIsin("US0378331005")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getByIsin("US0378331005"))
                .isInstanceOf(CompanySnapshotNotFoundException.class);
    }

    @Test
    void exportRoundTripsThroughUpsert() {
        CompanySnapshot stored = existing();
        stored.assignGroup(SEMIS, GroupSource.AI);
        stored.upsertFinding(ResearchCriterion.PE_RATIO, new BigDecimal("28.0"), null,
                "Trailing P/E of 28.0.", "https://example.com/aapl-key-stats", LocalDate.of(2026, 8, 1));
        stored.upsertFinding(ResearchCriterion.MOAT_ASSESSMENT, null, true,
                "Wide moat.", null, LocalDate.of(2026, 8, 5));
        when(repository.findByIsin("US0378331005")).thenReturn(Optional.of(stored));

        UpsertCompanySnapshotRequest exported = service.export(" us0378331005 ");

        assertThat(exported.groupKey()).isEqualTo("SEMICONDUCTORS");
        assertThat(exported.groupSource()).isEqualTo(GroupSource.AI);
        assertThat(exported.findings()).hasSize(2);

        when(repository.findByIsin("US0378331005")).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(groups.require("SEMICONDUCTORS")).thenReturn(SEMIS);

        CompanySnapshotView imported = service.upsert(exported, exported.groupSource(), HistoryOrigin.SYNC);

        assertThat(imported).isEqualTo(CompanySnapshotView.from(stored, freshness));
    }

    @Test
    void viewReportsLastResearchedDateAndStaleFlags() {
        CompanySnapshot stored = existing();
        stored.upsertFinding(ResearchCriterion.PE_RATIO, new BigDecimal("20"), null, "Old.", null,
                LocalDate.of(2026, 1, 10));
        stored.upsertFinding(ResearchCriterion.MOAT_ASSESSMENT, null, true, "Fresh.", null, LocalDate.of(2026, 6, 1));
        when(repository.findByIsin("US0378331005")).thenReturn(Optional.of(stored));

        CompanySnapshotView view = service.getByIsin("US0378331005");

        assertThat(view.lastResearchedAt()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(view.stale()).isFalse();
        assertThat(view.findings()).filteredOn(f -> f.asOfDate().equals(LocalDate.of(2026, 1, 10)))
                .singleElement().satisfies(f -> assertThat(f.stale()).isTrue());
        assertThat(view.findings()).filteredOn(f -> f.asOfDate().equals(LocalDate.of(2026, 6, 1)))
                .singleElement().satisfies(f -> assertThat(f.stale()).isFalse());
    }

    @Test
    void viewWithoutFindingsHasNoDateAndIsStale() {
        when(repository.findByIsin("US0378331005")).thenReturn(Optional.of(existing()));

        CompanySnapshotView view = service.getByIsin("US0378331005");

        assertThat(view.lastResearchedAt()).isNull();
        assertThat(view.stale()).isTrue();
    }

    @Test
    void exportWithoutGroupIsConflict() {
        when(repository.findByIsin("US0378331005")).thenReturn(Optional.of(existing()));

        assertThatThrownBy(() -> service.export("US0378331005"))
                .isInstanceOf(StockGroupConflictException.class)
                .hasMessage("Assign a group before exporting");
    }

    @Test
    void exportUnknownIsinThrowsNotFound() {
        when(repository.findByIsin("US0378331005")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.export("US0378331005"))
                .isInstanceOf(CompanySnapshotNotFoundException.class);
    }

    @Test
    void exportCarriesManualSource() {
        CompanySnapshot stored = existing();
        stored.assignGroup(BANKS, GroupSource.MANUAL);
        when(repository.findByIsin("US0378331005")).thenReturn(Optional.of(stored));

        UpsertCompanySnapshotRequest exported = service.export("US0378331005");

        assertThat(exported.groupKey()).isEqualTo("BANKS");
        assertThat(exported.groupSource()).isEqualTo(GroupSource.MANUAL);
    }

    private void stubSaveAndGroups(CompanySnapshot existing) {
        when(repository.findByIsin("US0378331005")).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(groups.require("SEMICONDUCTORS")).thenReturn(SEMIS);
    }

    @Test
    void changedFindingWritesOneHistoryRowWithOldValues() {
        CompanySnapshot existing = existing();
        existing.assignGroup(SEMIS, GroupSource.AI);
        existing.upsertFinding(ResearchCriterion.PE_RATIO, new BigDecimal("28.0"), null,
                "Trailing P/E of 28.0.", "https://example.com/aapl-key-stats", LocalDate.of(2026, 8, 1));
        stubSaveAndGroups(existing);

        service.upsert(request("SEMICONDUCTORS", "x", List.of(peRatioFinding("29.5"))), GroupSource.AI,
                HistoryOrigin.INGEST);

        ArgumentCaptor<ResearchFindingHistory> captor = ArgumentCaptor.forClass(ResearchFindingHistory.class);
        verify(findingHistory).save(captor.capture());
        ResearchFindingHistory row = captor.getValue();
        assertThat(row.getCriterionKey()).isEqualTo(ResearchCriterion.PE_RATIO);
        assertThat(row.getNumericValue()).isEqualByComparingTo("28.0");
        assertThat(row.getClaim()).isEqualTo("Trailing P/E of 28.0.");
        assertThat(row.getReplacedAt()).isEqualTo(Instant.parse("2026-11-12T10:00:00Z"));
        assertThat(row.getOrigin()).isEqualTo(HistoryOrigin.INGEST);
        verify(groupHistory, never()).save(any());
    }

    @Test
    void identicalResendWritesNoHistory() {
        CompanySnapshot existing = existing();
        existing.assignGroup(SEMIS, GroupSource.AI);
        existing.upsertFinding(ResearchCriterion.PE_RATIO, new BigDecimal("28.0000"), null,
                "Trailing P/E of 28.0.", "https://example.com/aapl-key-stats", LocalDate.of(2026, 8, 1));
        stubSaveAndGroups(existing);

        service.upsert(request("SEMICONDUCTORS", "x", List.of(peRatioFinding("28.0"))), GroupSource.AI,
                HistoryOrigin.INGEST);

        verify(findingHistory, never()).save(any());
        verify(groupHistory, never()).save(any());
    }

    @Test
    void syncPassesOriginSync() {
        CompanySnapshot existing = existing();
        existing.assignGroup(SEMIS, GroupSource.AI);
        existing.upsertFinding(ResearchCriterion.PE_RATIO, new BigDecimal("20"), null, "Old.", null,
                LocalDate.of(2026, 8, 1));
        stubSaveAndGroups(existing);

        service.upsert(request("SEMICONDUCTORS", "x", List.of(peRatioFinding("29.5"))), GroupSource.AI,
                HistoryOrigin.SYNC);

        ArgumentCaptor<ResearchFindingHistory> captor = ArgumentCaptor.forClass(ResearchFindingHistory.class);
        verify(findingHistory).save(captor.capture());
        assertThat(captor.getValue().getOrigin()).isEqualTo(HistoryOrigin.SYNC);
    }

    @Test
    void aiGroupChangeWritesGroupHistoryRow() {
        CompanySnapshot existing = existing();
        existing.assignGroup(BANKS, GroupSource.AI);
        stubSaveAndGroups(existing);

        service.upsert(request("SEMICONDUCTORS", "x", List.of()), GroupSource.AI, HistoryOrigin.INGEST);

        ArgumentCaptor<StockGroupHistory> captor = ArgumentCaptor.forClass(StockGroupHistory.class);
        verify(groupHistory).save(captor.capture());
        StockGroupHistory row = captor.getValue();
        assertThat(row.getPreviousGroup()).isSameAs(BANKS);
        assertThat(row.getPreviousSource()).isEqualTo(GroupSource.AI);
        assertThat(row.getNewGroup()).isSameAs(SEMIS);
        assertThat(row.getNewSource()).isEqualTo(GroupSource.AI);
        assertThat(row.getChangedAt()).isEqualTo(Instant.parse("2026-11-12T10:00:00Z"));
        assertThat(row.getOrigin()).isEqualTo(HistoryOrigin.INGEST);
    }

    @Test
    void firstGroupAssignmentWritesRowWithoutPreviousGroup() {
        when(repository.findByIsin("US0378331005")).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(groups.require("SEMICONDUCTORS")).thenReturn(SEMIS);

        service.upsert(request("SEMICONDUCTORS", "x", List.of()), GroupSource.AI, HistoryOrigin.INGEST);

        ArgumentCaptor<StockGroupHistory> captor = ArgumentCaptor.forClass(StockGroupHistory.class);
        verify(groupHistory).save(captor.capture());
        assertThat(captor.getValue().getPreviousGroup()).isNull();
        assertThat(captor.getValue().getPreviousSource()).isNull();
        assertThat(captor.getValue().getNewGroup()).isSameAs(SEMIS);
    }

    @Test
    void ignoredAiProposalWhileManualWritesNoGroupHistory() {
        CompanySnapshot existing = existing();
        existing.assignGroup(BANKS, GroupSource.MANUAL);
        stubSaveAndGroups(existing);

        service.upsert(request("SEMICONDUCTORS", "x", List.of()), GroupSource.AI, HistoryOrigin.INGEST);

        verify(groupHistory, never()).save(any());
    }

    @Test
    void syncWithManualSourceOverAiGroupWritesRow() {
        CompanySnapshot existing = existing();
        existing.assignGroup(SEMIS, GroupSource.AI);
        stubSaveAndGroups(existing);

        service.upsert(request("SEMICONDUCTORS", "x", List.of()), GroupSource.MANUAL, HistoryOrigin.SYNC);

        ArgumentCaptor<StockGroupHistory> captor = ArgumentCaptor.forClass(StockGroupHistory.class);
        verify(groupHistory).save(captor.capture());
        assertThat(captor.getValue().getNewSource()).isEqualTo(GroupSource.MANUAL);
        assertThat(captor.getValue().getOrigin()).isEqualTo(HistoryOrigin.SYNC);
    }

    @Test
    void manualAssignmentWritesRowWithOriginManual() {
        CompanySnapshot existing = existing();
        existing.assignGroup(SEMIS, GroupSource.AI);
        when(repository.findByIsin("US0378331005")).thenReturn(Optional.of(existing));
        when(groups.require("BANKS")).thenReturn(BANKS);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.assignGroupManually("US0378331005", "BANKS");

        ArgumentCaptor<StockGroupHistory> captor = ArgumentCaptor.forClass(StockGroupHistory.class);
        verify(groupHistory).save(captor.capture());
        assertThat(captor.getValue().getOrigin()).isEqualTo(HistoryOrigin.MANUAL);
        assertThat(captor.getValue().getNewSource()).isEqualTo(GroupSource.MANUAL);
        assertThat(captor.getValue().getPreviousGroup()).isSameAs(SEMIS);
    }

    @Test
    void manualReassignOfSameGroupAlreadyManualWritesNoRow() {
        CompanySnapshot existing = existing();
        existing.assignGroup(BANKS, GroupSource.MANUAL);
        when(repository.findByIsin("US0378331005")).thenReturn(Optional.of(existing));
        when(groups.require("BANKS")).thenReturn(BANKS);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.assignGroupManually("US0378331005", "BANKS");

        verify(groupHistory, never()).save(any());
    }

    @Test
    void historyReturnsBothListsNewestFirst() {
        CompanySnapshot existing = existing();
        when(repository.findByIsin("US0378331005")).thenReturn(Optional.of(existing));
        ResearchFindingHistory f = new ResearchFindingHistory(existing, new ReplacedFinding(
                ResearchCriterion.PE_RATIO, new BigDecimal("28.0"), null, "Old.", null, LocalDate.of(2026, 8, 1)),
                Instant.parse("2026-11-01T00:00:00Z"), HistoryOrigin.SYNC);
        StockGroupHistory g = new StockGroupHistory(existing, null, null, SEMIS, GroupSource.AI,
                Instant.parse("2026-11-02T00:00:00Z"), HistoryOrigin.INGEST);
        when(findingHistory.findByCompanySnapshotIdOrderByReplacedAtDescIdDesc(existing.getId()))
                .thenReturn(List.of(f));
        when(groupHistory.findByCompanySnapshotIdOrderByChangedAtDescIdDesc(existing.getId()))
                .thenReturn(List.of(g));

        SnapshotHistoryView view = service.history(" us0378331005 ");

        assertThat(view.findings()).singleElement().satisfies(e -> {
            assertThat(e.claim()).isEqualTo("Old.");
            assertThat(e.origin()).isEqualTo(HistoryOrigin.SYNC);
        });
        assertThat(view.groups()).singleElement().satisfies(e -> {
            assertThat(e.previousGroup()).isNull();
            assertThat(e.newGroup()).isEqualTo(new StockGroupRef("SEMICONDUCTORS", "Semiconductors"));
        });
    }

    @Test
    void historyOfUnknownIsinThrowsNotFound() {
        when(repository.findByIsin("US0378331005")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.history("US0378331005"))
                .isInstanceOf(CompanySnapshotNotFoundException.class);
    }
}
