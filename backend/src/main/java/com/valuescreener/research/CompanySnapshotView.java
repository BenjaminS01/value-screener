package com.valuescreener.research;

import java.util.List;

public record CompanySnapshotView(
        Long id,
        String ticker,
        String isin,
        String companyName,
        String sector,
        String country,
        String businessDescription,
        List<FindingView> findings
) {
    public static CompanySnapshotView from(CompanySnapshot snapshot) {
        return new CompanySnapshotView(
                snapshot.getId(), snapshot.getTicker(), snapshot.getIsin(), snapshot.getCompanyName(),
                snapshot.getSector(), snapshot.getCountry(), snapshot.getBusinessDescription(),
                snapshot.getFindings().stream().map(FindingView::from).toList());
    }
}
