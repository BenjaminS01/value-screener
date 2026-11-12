package com.valuescreener.research;

import com.valuescreener.portfolio.PortfolioPositionRepository;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.regex.Pattern;

@Service
public class InvestmentThesisService {

    private static final Pattern ISIN = Pattern.compile("[A-Z]{2}[A-Z0-9]{9}[0-9]");

    private final InvestmentThesisRepository repository;
    private final PortfolioPositionRepository positions;
    private final Clock clock;

    public InvestmentThesisService(InvestmentThesisRepository repository, PortfolioPositionRepository positions,
                                   Clock clock) {
        this.repository = repository;
        this.positions = positions;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public InvestmentThesisView get(String isin) {
        String normalized = normalizeIsin(isin);
        InvestmentThesis thesis = repository.findById(normalized)
                .orElseThrow(() -> new ThesisNotFoundException(normalized));
        return toView(thesis);
    }

    @Transactional
    public InvestmentThesisView save(String isin, SaveThesisRequest request) {
        String normalized = normalizeIsin(isin);
        Instant now = clock.instant();
        InvestmentThesis existing = repository.findById(normalized).orElse(null);
        InvestmentThesis thesis;
        if (request.version() == null) {
            if (existing != null) {
                throw conflict(normalized);
            }
            thesis = new InvestmentThesis(normalized, now);
        } else {
            if (existing == null || !request.version().equals(existing.getVersion())) {
                throw conflict(normalized);
            }
            thesis = existing;
        }
        thesis.replace(request.whyInteresting(), request.expectedDevelopment(), request.exitCriteria(),
                request.status(), request.statusReason(), now);
        return toView(repository.saveAndFlush(thesis));
    }

    /** Called from the buy transaction: a failure here must abort the purchase too. */
    @Transactional
    public void appendPurchaseReason(String isin, String reason) {
        String normalized = normalizeIsin(isin);
        Instant now = clock.instant();
        InvestmentThesis thesis = repository.findById(normalized)
                .orElseGet(() -> new InvestmentThesis(normalized, now));
        thesis.appendPurchaseReason(reason, LocalDate.now(clock), now);
        repository.save(thesis);
    }

    private InvestmentThesisView toView(InvestmentThesis thesis) {
        return new InvestmentThesisView(thesis.getIsin(), thesis.getWhyInteresting(),
                thesis.getExpectedDevelopment(), thesis.getExitCriteria(), thesis.getStatus(),
                thesis.getStatusReason(), thesis.getUpdatedAt(), thesis.getVersion(),
                positions.findByIsin(thesis.getIsin()).isPresent());
    }

    private static ObjectOptimisticLockingFailureException conflict(String isin) {
        return new ObjectOptimisticLockingFailureException(InvestmentThesis.class, isin);
    }

    private static String normalizeIsin(String isin) {
        String normalized = isin == null ? "" : isin.trim().toUpperCase();
        if (!ISIN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Invalid ISIN");
        }
        return normalized;
    }
}
