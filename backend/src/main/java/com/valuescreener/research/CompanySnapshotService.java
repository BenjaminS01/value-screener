package com.valuescreener.research;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
        String kept = snapshot.getGroupKey();
        GroupSource keptSource = snapshot.getGroupSource();
        boolean assigned = snapshot.assignGroup(group, source);
        if (!assigned && (kept == null || !kept.equals(group.getKey()))) {
            log.info("group proposal ignored isin={} proposed={} kept={}",
                    normalizedIsin, group.getKey(), kept);
        }
        List<ReplacedFinding> replaced = new ArrayList<>();
        List<FindingRequest> findings = request.findings() != null ? request.findings() : List.of();
        for (FindingRequest finding : findings) {
            snapshot.upsertFinding(finding.criterionKey(), finding.numericValue(), finding.booleanValue(),
                    finding.claim(), finding.sourceUrl(), finding.asOfDate()).ifPresent(replaced::add);
        }
        Instant now = clock.instant();
        List<ResearchFindingHistory> findingEntries = replaced.stream()
                .map(old -> new ResearchFindingHistory(normalizedIsin, old, now, origin)).toList();
        Optional<StockGroupHistory> groupEntry = groupChange(snapshot, kept, keptSource, origin, now);
        return toView(repository.save(snapshot, findingEntries, groupEntry));
    }

    private static Optional<StockGroupHistory> groupChange(CompanySnapshot snapshot, String previous,
                                                           GroupSource previousSource, HistoryOrigin origin,
                                                           Instant now) {
        String current = snapshot.getGroupKey();
        boolean sameKey = previous != null && previous.equals(current);
        if (sameKey && previousSource == snapshot.getGroupSource()) {
            return Optional.empty();
        }
        return Optional.of(new StockGroupHistory(snapshot.getIsin(), previous, previousSource, current,
                snapshot.getGroupSource(), now, origin));
    }

    /** Single snapshot: the group name comes from one lookup (the view shows key and name). */
    private CompanySnapshotView toView(CompanySnapshot snapshot) {
        Map<String, String> names = new HashMap<>();
        String key = snapshot.getGroupKey();
        if (key != null) {
            String name = groups.nameOf(key);
            if (name != null) {
                names.put(key, name);
            }
        }
        return CompanySnapshotView.from(snapshot, freshness, names);
    }

    public SnapshotHistoryView history(String isin) {
        String normalizedIsin = normalizeIsin(isin);
        CompanySnapshot snapshot = repository.findByIsin(normalizedIsin)
                .orElseThrow(() -> new CompanySnapshotNotFoundException(normalizedIsin));
        List<SnapshotHistoryView.FindingHistoryEntry> findings = findingHistory
                .findBySnapshotIsin(snapshot.getIsin()).stream()
                .map(h -> new SnapshotHistoryView.FindingHistoryEntry(h.getCriterionKey(), h.getNumericValue(),
                        h.getBooleanValue(), h.getClaim(), h.getSourceUrl(), h.getAsOfDate(), h.getReplacedAt(),
                        h.getOrigin()))
                .toList();
        Map<String, String> names = groups.namesByKey();
        List<SnapshotHistoryView.GroupHistoryEntry> groupEntries = groupHistory
                .findBySnapshotIsin(snapshot.getIsin()).stream()
                .map(h -> new SnapshotHistoryView.GroupHistoryEntry(ref(h.getPreviousGroupKey(), names),
                        h.getPreviousSource(), ref(h.getNewGroupKey(), names), h.getNewSource(), h.getChangedAt(),
                        h.getOrigin()))
                .toList();
        return new SnapshotHistoryView(findings, groupEntries);
    }

    private static StockGroupRef ref(String key, Map<String, String> names) {
        return key == null ? null : new StockGroupRef(key, names.getOrDefault(key, key));
    }

    /** Snapshot in import format (stored group and group source); requires a group so the live import is valid. */
    public UpsertCompanySnapshotRequest export(String isin) {
        String normalizedIsin = normalizeIsin(isin);
        CompanySnapshot snapshot = repository.findByIsin(normalizedIsin)
                .orElseThrow(() -> new CompanySnapshotNotFoundException(normalizedIsin));
        String groupKey = snapshot.getGroupKey();
        if (groupKey == null) {
            throw new StockGroupConflictException("Assign a group before exporting");
        }
        List<FindingRequest> findings = snapshot.getFindings().stream()
                .map(f -> new FindingRequest(f.getCriterionKey(), f.getNumericValue(), f.getBooleanValue(),
                        f.getClaim(), f.getSourceUrl(), f.getAsOfDate()))
                .toList();
        return new UpsertCompanySnapshotRequest(snapshot.getTicker(), snapshot.getIsin(),
                snapshot.getCompanyName(), snapshot.getCountry(), snapshot.getBusinessDescription(),
                groupKey, snapshot.getGroupSource(), findings);
    }

    public CompanySnapshotView assignGroupManually(String isin, String groupKey) {
        String normalizedIsin = normalizeIsin(isin);
        CompanySnapshot snapshot = repository.findByIsin(normalizedIsin)
                .orElseThrow(() -> new CompanySnapshotNotFoundException(normalizedIsin));
        String previous = snapshot.getGroupKey();
        GroupSource previousSource = snapshot.getGroupSource();
        snapshot.assignGroup(groups.require(StockGroup.trimUpper(groupKey)), GroupSource.MANUAL);
        Optional<StockGroupHistory> groupEntry = groupChange(snapshot, previous, previousSource,
                HistoryOrigin.MANUAL, clock.instant());
        return toView(repository.save(snapshot, List.of(), groupEntry));
    }

    /** null = all, "none" = ungrouped, otherwise a (normalized) group key; an unknown key yields an empty list. */
    public List<CompanySnapshotView> list(String groupFilter) {
        List<CompanySnapshot> snapshots;
        if (groupFilter == null) {
            snapshots = repository.findAll();
        } else if (NO_GROUP_FILTER.equalsIgnoreCase(groupFilter.trim())) {
            snapshots = repository.findAllWithoutGroup();
        } else {
            snapshots = repository.findAllByGroupKey(StockGroup.trimUpper(groupFilter));
        }
        Map<String, String> names = snapshots.isEmpty() ? Map.of() : groups.namesByKey();
        return snapshots.stream().map(s -> CompanySnapshotView.from(s, freshness, names)).toList();
    }

    public CompanySnapshotView getByIsin(String isin) {
        String normalizedIsin = normalizeIsin(isin);
        return repository.findByIsin(normalizedIsin)
                .map(this::toView)
                .orElseThrow(() -> new CompanySnapshotNotFoundException(isin));
    }

    private static String normalizeIsin(String isin) {
        return isin == null ? "" : isin.trim().toUpperCase();
    }
}
