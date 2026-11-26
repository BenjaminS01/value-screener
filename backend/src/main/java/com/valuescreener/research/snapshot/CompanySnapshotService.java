package com.valuescreener.research.snapshot;

import com.valuescreener.research.group.StockGroup;
import com.valuescreener.research.group.StockGroupConflictException;
import com.valuescreener.research.group.StockGroupService;
import com.valuescreener.research.history.HistoryOrigin;
import com.valuescreener.research.history.ReplacedFinding;
import com.valuescreener.research.history.ResearchFindingHistory;
import com.valuescreener.research.history.ResearchFindingHistoryRepository;
import com.valuescreener.research.history.SnapshotHistoryView;
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

    private final CompanySnapshotRepository repository;
    private final StockGroupService groups;
    private final ResearchFreshness freshness;
    private final ResearchFindingHistoryRepository findingHistory;
    private final Clock clock;

    public CompanySnapshotService(CompanySnapshotRepository repository, StockGroupService groups,
                                  ResearchFreshness freshness, ResearchFindingHistoryRepository findingHistory,
                                  Clock clock) {
        this.repository = repository;
        this.groups = groups;
        this.freshness = freshness;
        this.findingHistory = findingHistory;
        this.clock = clock;
    }

    public CompanySnapshotView upsert(UpsertCompanySnapshotRequest request, GroupSource source,
                                      HistoryOrigin origin) {
        String normalizedIsin = request.isin().trim().toUpperCase();
        // an invalid ISIN is never looked up (key limit); the constructor below rejects it (400)
        CompanySnapshot snapshot = CompanySnapshot.isValidIsin(normalizedIsin)
                ? repository.findByIsin(normalizedIsin).orElse(null) : null;
        StockGroup group = groups.require(StockGroup.trimUpper(request.groupKey()));
        if (snapshot == null) {
            snapshot = new CompanySnapshot(request.ticker(), request.isin(), request.companyName(),
                    request.country(), request.businessDescription());
        } else {
            snapshot.applyUpdate(request.companyName(), request.country(), request.businessDescription());
        }
        String kept = snapshot.getGroupKey();
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
        return toView(repository.save(snapshot, findingEntries, groupChanged(snapshot, kept)));
    }

    /** True when the snapshot now has a group key it did not have before (the new group must be marked). */
    private static boolean groupChanged(CompanySnapshot snapshot, String previous) {
        String current = snapshot.getGroupKey();
        return current != null && !current.equals(previous);
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
        CompanySnapshot snapshot = find(normalizedIsin)
                .orElseThrow(() -> new CompanySnapshotNotFoundException(normalizedIsin));
        List<SnapshotHistoryView.FindingHistoryEntry> findings = findingHistory
                .findBySnapshotIsin(snapshot.getIsin()).stream()
                .map(h -> new SnapshotHistoryView.FindingHistoryEntry(h.getCriterionKey(), h.getNumericValue(),
                        h.getBooleanValue(), h.getClaim(), h.getSourceUrl(), h.getAsOfDate(), h.getReplacedAt(),
                        h.getOrigin()))
                .toList();
        return new SnapshotHistoryView(findings);
    }

    /** Snapshot in import format (stored group and group source); requires a group so the live import is valid. */
    public UpsertCompanySnapshotRequest export(String isin) {
        String normalizedIsin = normalizeIsin(isin);
        CompanySnapshot snapshot = find(normalizedIsin)
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
        CompanySnapshot snapshot = find(normalizedIsin)
                .orElseThrow(() -> new CompanySnapshotNotFoundException(normalizedIsin));
        String previous = snapshot.getGroupKey();
        snapshot.assignGroup(groups.require(StockGroup.trimUpper(groupKey)), GroupSource.MANUAL);
        return toView(repository.save(snapshot, List.of(), groupChanged(snapshot, previous)));
    }

    /** The research list from the snapshot index (eventually consistent): no snapshot item is read. */
    public List<SnapshotSummaryView> list() {
        List<SnapshotIndexEntry> index = repository.findIndex();
        Map<String, String> names = index.isEmpty() ? Map.of() : groups.namesByKey();
        return index.stream().map(e -> SnapshotSummaryView.from(e, freshness, names)).toList();
    }

    public CompanySnapshotView getByIsin(String isin) {
        String normalizedIsin = normalizeIsin(isin);
        return find(normalizedIsin)
                .map(this::toView)
                .orElseThrow(() -> new CompanySnapshotNotFoundException(isin));
    }

    /** An invalid ISIN can never exist: not found without a lookup (DynamoDB rejects keys over 1024 bytes). */
    private Optional<CompanySnapshot> find(String normalizedIsin) {
        return CompanySnapshot.isValidIsin(normalizedIsin) ? repository.findByIsin(normalizedIsin) : Optional.empty();
    }

    private static String normalizeIsin(String isin) {
        return isin == null ? "" : isin.trim().toUpperCase();
    }
}
