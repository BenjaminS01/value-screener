package com.valuescreener.research;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CompanySnapshotService {

    private final CompanySnapshotRepository repository;

    public CompanySnapshotService(CompanySnapshotRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public CompanySnapshotView upsert(UpsertCompanySnapshotRequest request) {
        String normalizedIsin = request.isin().trim().toUpperCase();
        CompanySnapshot snapshot = repository.findByIsin(normalizedIsin).orElse(null);
        if (snapshot == null) {
            snapshot = new CompanySnapshot(request.ticker(), request.isin(), request.companyName(),
                    request.sector(), request.country(), request.businessDescription());
        } else {
            snapshot.applyUpdate(request.companyName(), request.sector(), request.country(),
                    request.businessDescription());
        }
        List<FindingRequest> findings = request.findings() != null ? request.findings() : List.of();
        for (FindingRequest finding : findings) {
            snapshot.upsertFinding(finding.criterionKey(), finding.numericValue(), finding.booleanValue(),
                    finding.claim(), finding.sourceUrl(), finding.asOfDate());
        }
        return CompanySnapshotView.from(repository.save(snapshot));
    }

    @Transactional(readOnly = true)
    public List<CompanySnapshotView> listAll() {
        return repository.findAll().stream().map(CompanySnapshotView::from).toList();
    }

    @Transactional(readOnly = true)
    public CompanySnapshotView getByIsin(String isin) {
        String normalizedIsin = isin.trim().toUpperCase();
        return repository.findByIsin(normalizedIsin)
                .map(CompanySnapshotView::from)
                .orElseThrow(() -> new CompanySnapshotNotFoundException(isin));
    }
}
