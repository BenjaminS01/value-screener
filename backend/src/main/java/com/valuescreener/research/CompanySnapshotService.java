package com.valuescreener.research;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class CompanySnapshotService {

    private static final Logger log = LoggerFactory.getLogger(CompanySnapshotService.class);
    private static final String NO_GROUP_FILTER = "none";

    private final CompanySnapshotRepository repository;
    private final StockGroupService groups;
    private final ResearchFreshness freshness;
    private final ResearchFindingHistoryRepository findingHistory;
    private final StockGroupHistoryRepository groupHistory;
    private final Clock clock;

    public CompanySnapshotService(CompanySnapshotRepository repository, StockGroupService groups,
                                  ResearchFreshness freshness, ResearchFindingHistoryRepository findingHistory,
                                  StockGroupHistoryRepository groupHistory, Clock clock) {
        this.repository = repository;
        this.groups = groups;
        this.freshness = freshness;
        this.findingHistory = findingHistory;
        this.groupHistory = groupHistory;
        this.clock = clock;
    }

    @Transactional
    public CompanySnapshotView upsert(UpsertCompanySnapshotRequest request, GroupSource source,
                                      HistoryOrigin origin) {
        String normalizedIsin = request.isin().trim().toUpperCase();
        CompanySnapshot snapshot = repository.findByIsin(normalizedIsin).orElse(null);
        StockGroup group = groups.require(StockGroup.trimUpper(request.groupKey()));
        if (snapshot == null) {
            snapshot = new CompanySnapshot(request.ticker(), request.isin(), request.companyName(),
                    request.country(), request.businessDescription());
        } else {
            snapshot.applyUpdate(request.companyName(), request.country(), request.businessDescription());
        }
        StockGroup kept = snapshot.getStockGroup();
        GroupSource keptSource = snapshot.getGroupSource();
        boolean assigned = snapshot.assignGroup(group, source);
        if (!assigned && (kept == null || !kept.getKey().equals(group.getKey()))) {
            log.info("group proposal ignored isin={} proposed={} kept={}",
                    normalizedIsin, group.getKey(), kept == null ? null : kept.getKey());
        }
        List<ReplacedFinding> replaced = new ArrayList<>();
        List<FindingRequest> findings = request.findings() != null ? request.findings() : List.of();
        for (FindingRequest finding : findings) {
            snapshot.upsertFinding(finding.criterionKey(), finding.numericValue(), finding.booleanValue(),
                    finding.claim(), finding.sourceUrl(), finding.asOfDate()).ifPresent(replaced::add);
        }
        CompanySnapshot saved = repository.save(snapshot);
        Instant now = clock.instant();
        for (ReplacedFinding old : replaced) {
            findingHistory.save(new ResearchFindingHistory(saved, old, now, origin));
        }
        recordGroupChange(saved, kept, keptSource, origin, now);
        return CompanySnapshotView.from(saved, freshness);
    }

    private void recordGroupChange(CompanySnapshot snapshot, StockGroup previous, GroupSource previousSource,
                                   HistoryOrigin origin, Instant now) {
        StockGroup current = snapshot.getStockGroup();
        boolean sameKey = previous != null && current != null && previous.getKey().equals(current.getKey());
        if (sameKey && previousSource == snapshot.getGroupSource()) {
            return;
        }
        groupHistory.save(new StockGroupHistory(snapshot, previous, previousSource, current,
                snapshot.getGroupSource(), now, origin));
    }

    @Transactional(readOnly = true)
    public SnapshotHistoryView history(String isin) {
        String normalizedIsin = normalizeIsin(isin);
        CompanySnapshot snapshot = repository.findByIsin(normalizedIsin)
                .orElseThrow(() -> new CompanySnapshotNotFoundException(normalizedIsin));
        List<SnapshotHistoryView.FindingHistoryEntry> findings = findingHistory
                .findByCompanySnapshotIdOrderByReplacedAtDescIdDesc(snapshot.getId()).stream()
                .map(h -> new SnapshotHistoryView.FindingHistoryEntry(h.getCriterionKey(), h.getNumericValue(),
                        h.getBooleanValue(), h.getClaim(), h.getSourceUrl(), h.getAsOfDate(), h.getReplacedAt(),
                        h.getOrigin()))
                .toList();
        List<SnapshotHistoryView.GroupHistoryEntry> groupEntries = groupHistory
                .findByCompanySnapshotIdOrderByChangedAtDescIdDesc(snapshot.getId()).stream()
                .map(h -> new SnapshotHistoryView.GroupHistoryEntry(ref(h.getPreviousGroup()),
                        h.getPreviousSource(), ref(h.getNewGroup()), h.getNewSource(), h.getChangedAt(),
                        h.getOrigin()))
                .toList();
        return new SnapshotHistoryView(findings, groupEntries);
    }

    private static StockGroupRef ref(StockGroup group) {
        return group == null ? null : new StockGroupRef(group.getKey(), group.getName());
    }

    /** Snapshot in import format (stored group and group source); requires a group so the live import is valid. */
    @Transactional(readOnly = true)
    public UpsertCompanySnapshotRequest export(String isin) {
        String normalizedIsin = normalizeIsin(isin);
        CompanySnapshot snapshot = repository.findByIsin(normalizedIsin)
                .orElseThrow(() -> new CompanySnapshotNotFoundException(normalizedIsin));
        StockGroup group = snapshot.getStockGroup();
        if (group == null) {
            throw new StockGroupConflictException("Assign a group before exporting");
        }
        List<FindingRequest> findings = snapshot.getFindings().stream()
                .map(f -> new FindingRequest(f.getCriterionKey(), f.getNumericValue(), f.getBooleanValue(),
                        f.getClaim(), f.getSourceUrl(), f.getAsOfDate()))
                .toList();
        return new UpsertCompanySnapshotRequest(snapshot.getTicker(), snapshot.getIsin(),
                snapshot.getCompanyName(), snapshot.getCountry(), snapshot.getBusinessDescription(),
                group.getKey(), snapshot.getGroupSource(), findings);
    }

    @Transactional
    public CompanySnapshotView assignGroupManually(String isin, String groupKey) {
        String normalizedIsin = normalizeIsin(isin);
        CompanySnapshot snapshot = repository.findByIsin(normalizedIsin)
                .orElseThrow(() -> new CompanySnapshotNotFoundException(normalizedIsin));
        StockGroup previous = snapshot.getStockGroup();
        GroupSource previousSource = snapshot.getGroupSource();
        snapshot.assignGroup(groups.require(StockGroup.trimUpper(groupKey)), GroupSource.MANUAL);
        CompanySnapshot saved = repository.save(snapshot);
        recordGroupChange(saved, previous, previousSource, HistoryOrigin.MANUAL, clock.instant());
        return CompanySnapshotView.from(saved, freshness);
    }

    /** null = all, "none" = ungrouped, otherwise a (normalized) group key; an unknown key yields an empty list. */
    @Transactional(readOnly = true)
    public List<CompanySnapshotView> list(String groupFilter) {
        List<CompanySnapshot> snapshots;
        if (groupFilter == null) {
            snapshots = repository.findAll();
        } else if (NO_GROUP_FILTER.equalsIgnoreCase(groupFilter.trim())) {
            snapshots = repository.findAllByStockGroupIsNull();
        } else {
            snapshots = repository.findAllByStockGroup_Key(StockGroup.trimUpper(groupFilter));
        }
        return snapshots.stream().map(s -> CompanySnapshotView.from(s, freshness)).toList();
    }

    @Transactional(readOnly = true)
    public CompanySnapshotView getByIsin(String isin) {
        String normalizedIsin = normalizeIsin(isin);
        return repository.findByIsin(normalizedIsin)
                .map(s -> CompanySnapshotView.from(s, freshness))
                .orElseThrow(() -> new CompanySnapshotNotFoundException(isin));
    }

    private static String normalizeIsin(String isin) {
        return isin == null ? "" : isin.trim().toUpperCase();
    }
}
