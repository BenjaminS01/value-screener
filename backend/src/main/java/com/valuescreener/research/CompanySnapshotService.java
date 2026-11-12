package com.valuescreener.research;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CompanySnapshotService {

    private static final Logger log = LoggerFactory.getLogger(CompanySnapshotService.class);
    private static final String NO_GROUP_FILTER = "none";

    private final CompanySnapshotRepository repository;
    private final StockGroupService groups;
    private final ResearchFreshness freshness;

    public CompanySnapshotService(CompanySnapshotRepository repository, StockGroupService groups,
                                  ResearchFreshness freshness) {
        this.repository = repository;
        this.groups = groups;
        this.freshness = freshness;
    }

    @Transactional
    public CompanySnapshotView upsert(UpsertCompanySnapshotRequest request, GroupSource source) {
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
        if (!snapshot.assignGroup(group, source) && (kept == null || !kept.getKey().equals(group.getKey()))) {
            log.info("group proposal ignored isin={} proposed={} kept={}",
                    normalizedIsin, group.getKey(), kept == null ? null : kept.getKey());
        }
        List<FindingRequest> findings = request.findings() != null ? request.findings() : List.of();
        for (FindingRequest finding : findings) {
            snapshot.upsertFinding(finding.criterionKey(), finding.numericValue(), finding.booleanValue(),
                    finding.claim(), finding.sourceUrl(), finding.asOfDate());
        }
        return CompanySnapshotView.from(repository.save(snapshot), freshness);
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
        snapshot.assignGroup(groups.require(StockGroup.trimUpper(groupKey)), GroupSource.MANUAL);
        return CompanySnapshotView.from(repository.save(snapshot), freshness);
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
