package com.valuescreener.research;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
public class CompanySnapshotService {

    private final CompanySnapshotRepository repository;

    public CompanySnapshotService(CompanySnapshotRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public CompanySnapshotView upsert(UpsertCompanySnapshotRequest request) {
        FinancialStats stats = new FinancialStats(
                request.peRatio(), request.pbRatio(), request.roePercent(), request.debtToEquity(),
                request.currentYearNetMarginPercent(), request.currentYearFcfPositive(),
                request.currentYearNetIncomeIncreasedYoy(), request.insiderOwnershipPercent());
        Set<String> sources = request.sources() != null ? request.sources() : Set.of();
        String moatNote = request.moatNote() != null && request.moatNote().isBlank() ? null : request.moatNote();
        String opportunitiesAndRisksNote = request.opportunitiesAndRisksNote() != null
                && request.opportunitiesAndRisksNote().isBlank() ? null : request.opportunitiesAndRisksNote();

        String normalizedIsin = request.isin().trim().toUpperCase();
        CompanySnapshot snapshot = repository.findByIsin(normalizedIsin).orElse(null);
        if (snapshot == null) {
            snapshot = new CompanySnapshot(request.ticker(), request.isin(), request.companyName(),
                    request.sector(), request.country(), request.businessDescription(), moatNote,
                    opportunitiesAndRisksNote, stats, request.asOfDate(), sources);
        } else {
            snapshot.applyUpdate(request.companyName(), request.sector(), request.country(),
                    request.businessDescription(), moatNote, opportunitiesAndRisksNote,
                    stats, request.asOfDate(), sources);
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
