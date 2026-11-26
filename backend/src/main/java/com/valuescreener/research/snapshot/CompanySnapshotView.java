package com.valuescreener.research.snapshot;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record CompanySnapshotView(
        String ticker,
        String isin,
        String companyName,
        String country,
        String businessDescription,
        SnapshotGroupView group,
        List<FindingView> findings,
        LocalDate lastResearchedAt,
        boolean stale
) {
    /** @param groupNames group key to group name; a key missing from the map is shown as its own name */
    public static CompanySnapshotView from(CompanySnapshot snapshot, ResearchFreshness freshness,
                                           Map<String, String> groupNames) {
        String groupKey = snapshot.getGroupKey();
        SnapshotGroupView group = groupKey == null ? null
                : new SnapshotGroupView(groupKey, groupNames.getOrDefault(groupKey, groupKey),
                        snapshot.getGroupSource());
        LocalDate lastResearchedAt = snapshot.lastResearchedAt();
        return new CompanySnapshotView(
                snapshot.getTicker(), snapshot.getIsin(), snapshot.getCompanyName(),
                snapshot.getCountry(), snapshot.getBusinessDescription(), group,
                snapshot.getFindings().stream().map(f -> FindingView.from(f, freshness)).toList(),
                lastResearchedAt, freshness.isStale(lastResearchedAt));
    }
}
