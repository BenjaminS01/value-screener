package com.valuescreener.research;

import java.util.List;

public record CompanySnapshotView(
        Long id,
        String ticker,
        String isin,
        String companyName,
        String country,
        String businessDescription,
        SnapshotGroupView group,
        List<FindingView> findings
) {
    public static CompanySnapshotView from(CompanySnapshot snapshot) {
        StockGroup stockGroup = snapshot.getStockGroup();
        SnapshotGroupView group = stockGroup == null ? null
                : new SnapshotGroupView(stockGroup.getKey(), stockGroup.getName(), snapshot.getGroupSource());
        return new CompanySnapshotView(
                snapshot.getId(), snapshot.getTicker(), snapshot.getIsin(), snapshot.getCompanyName(),
                snapshot.getCountry(), snapshot.getBusinessDescription(), group,
                snapshot.getFindings().stream().map(FindingView::from).toList());
    }
}
