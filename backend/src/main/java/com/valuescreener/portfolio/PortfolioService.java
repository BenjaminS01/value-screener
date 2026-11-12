package com.valuescreener.portfolio;

import com.valuescreener.research.InvestmentThesisService;
import com.valuescreener.research.StockGroupLookup;
import com.valuescreener.research.StockGroupRef;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class PortfolioService {

    private final PortfolioPositionRepository repository;
    private final StockGroupLookup stockGroups;
    private final InvestmentThesisService theses;

    public PortfolioService(PortfolioPositionRepository repository, StockGroupLookup stockGroups,
                            InvestmentThesisService theses) {
        this.repository = repository;
        this.stockGroups = stockGroups;
        this.theses = theses;
    }

    @Transactional
    public void buy(BuyPositionRequest request) {
        String isin = normalizeIsin(request.isin());
        // Before saving, inside the same transaction: a too-long thesis aborts the whole purchase.
        if (request.purchaseReason() != null && !request.purchaseReason().isBlank()) {
            theses.appendPurchaseReason(isin, request.purchaseReason());
        }
        PortfolioPosition position = repository.findByIsin(isin).orElse(null);

        if (position == null) {
            repository.save(new PortfolioPosition(
                    request.ticker(), isin, request.companyName(),
                    request.quantity(), request.entryPrice(), request.purchaseDate()));
            return;
        }

        position.recordPurchase(request.quantity(), request.entryPrice());
        repository.save(position);
    }

    @Transactional
    public void sell(SellPositionRequest request) {
        PortfolioPosition position = find(request.isin());

        position.recordSale(request.quantity());

        if (position.isClosed()) {
            repository.delete(position);
        } else {
            repository.save(position);
        }
    }

    @Transactional
    public void correct(String isin, CorrectPositionRequest request) {
        PortfolioPosition position = find(isin);
        position.correct(request.ticker(), request.companyName(), request.quantity(),
                request.entryPrice(), request.purchaseDate());
        repository.save(position);
    }

    @Transactional
    public void delete(String isin) {
        repository.delete(find(isin));
    }

    @Transactional(readOnly = true)
    public List<PortfolioPositionView> listForUser() {
        List<PortfolioPosition> positions = repository.findAll();
        Map<String, StockGroupRef> groups = groupsFor(positions);
        return positions.stream()
                .map(p -> new PortfolioPositionView(p.getTicker(), p.getIsin(), p.getCompanyName(),
                        groups.get(p.getIsin())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AdminPortfolioPositionView> listForAdmin() {
        List<PortfolioPosition> positions = repository.findAll();
        Map<String, StockGroupRef> groups = groupsFor(positions);
        return positions.stream()
                .map(p -> new AdminPortfolioPositionView(p.getTicker(), p.getIsin(), p.getCompanyName(),
                        groups.get(p.getIsin()), p.getQuantity(), p.getEntryPrice(), p.getPurchaseDate()))
                .toList();
    }

    private Map<String, StockGroupRef> groupsFor(List<PortfolioPosition> positions) {
        return stockGroups.groupsByIsin(positions.stream().map(PortfolioPosition::getIsin).toList());
    }

    private static String normalizeIsin(String isin) {
        return isin == null ? "" : isin.trim().toUpperCase();
    }

    private PortfolioPosition find(String isin) {
        String normalized = normalizeIsin(isin);
        return repository.findByIsin(normalized)
                .orElseThrow(() -> new PositionNotFoundException(normalized));
    }
}
