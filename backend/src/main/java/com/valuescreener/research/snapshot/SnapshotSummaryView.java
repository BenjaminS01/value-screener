package com.valuescreener.research.snapshot;

import java.time.LocalDate;
import java.util.Map;

/** One row of the research list: read from the snapshot index, without findings or description. */
public record SnapshotSummaryView(
        String ticker,
        String isin,
        String companyName,
        String country,
        SnapshotGroupView group,
        LocalDate lastResearchedAt,
        boolean stale
) {
    /**
     * Name and ticker are never null: a v1 index item (only the group, e.g. written by an older release during a
     * deploy or after a rollback) shows the ISIN as name and an empty ticker until the next save rewrites it.
     *
     * @param groupNames group key to group name; a key missing from the map is shown as its own name
     */
    public static SnapshotSummaryView from(SnapshotIndexEntry entry, ResearchFreshness freshness,
                                           Map<String, String> groupNames) {
        String groupKey = entry.groupKey();
        SnapshotGroupView group = groupKey == null ? null
                : new SnapshotGroupView(groupKey, groupNames.getOrDefault(groupKey, groupKey), entry.groupSource());
        String ticker = entry.ticker() == null ? "" : entry.ticker();
        String companyName = entry.companyName() == null ? entry.isin() : entry.companyName();
        return new SnapshotSummaryView(ticker, entry.isin(), companyName, entry.country(), group,
                entry.lastResearchedAt(), freshness.isStale(entry.lastResearchedAt()));
    }
}
