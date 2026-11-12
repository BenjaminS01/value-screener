package com.valuescreener.research;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

public record CompanySnapshotView(
        Long id,
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
    public static CompanySnapshotView from(CompanySnapshot snapshot, ResearchFreshness freshness) {
        StockGroup stockGroup = snapshot.getStockGroup();
        SnapshotGroupView group = stockGroup == null ? null
                : new SnapshotGroupView(stockGroup.getKey(), stockGroup.getName(), snapshot.getGroupSource());
        LocalDate lastResearchedAt = snapshot.getFindings().stream().map(ResearchFinding::getAsOfDate)
                .max(Comparator.naturalOrder()).orElse(null);
        return new CompanySnapshotView(
                snapshot.getId(), snapshot.getTicker(), snapshot.getIsin(), snapshot.getCompanyName(),
                snapshot.getCountry(), snapshot.getBusinessDescription(), group,
                snapshot.getFindings().stream().map(f -> FindingView.from(f, freshness)).toList(),
                lastResearchedAt, freshness.isStale(lastResearchedAt));
    }
}
