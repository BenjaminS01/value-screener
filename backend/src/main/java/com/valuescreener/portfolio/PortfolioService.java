package com.valuescreener.portfolio;

import com.valuescreener.research.CompanySnapshot;
import com.valuescreener.research.InvestmentThesis;
import com.valuescreener.research.InvestmentThesisService;
import com.valuescreener.research.StockGroupLookup;
import com.valuescreener.research.StockGroupRef;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
public class PortfolioService {

    private final PortfolioPositionRepository repository;
    private final StockGroupLookup stockGroups;
    private final InvestmentThesisService theses;
    private final PurchaseWriter purchaseWriter;

    public PortfolioService(PortfolioPositionRepository repository, StockGroupLookup stockGroups,
                            InvestmentThesisService theses, PurchaseWriter purchaseWriter) {
        this.repository = repository;
        this.stockGroups = stockGroups;
        this.theses = theses;
        this.purchaseWriter = purchaseWriter;
    }

    public void buy(BuyPositionRequest request) {
        String isin = normalizeIsin(request.isin());
        if (!CompanySnapshot.isValidIsin(isin)) {
            throw new IllegalArgumentException("isin must be a valid 12-character ISIN"); // before any lookup
        }
        // Everything is prepared and validated in memory first (a too-long thesis or invalid input
        // aborts before anything is written); then position and thesis are written in one transaction.
        InvestmentThesis thesis = null;
        if (request.purchaseReason() != null && !request.purchaseReason().isBlank()) {
            thesis = theses.prepareAppendPurchaseReason(isin, request.purchaseReason());
        }
        PortfolioPosition position = repository.findByIsin(isin).orElse(null);

        if (position == null) {
            position = new PortfolioPosition(
                    request.ticker(), isin, request.companyName(),
                    request.quantity(), request.entryPrice(), request.purchaseDate());
        } else {
            position.recordPurchase(request.quantity(), request.entryPrice());
        }

        if (thesis == null) {
            repository.save(position);
        } else {
            purchaseWriter.save(position, thesis);
        }
    }

    public void sell(SellPositionRequest request) {
        PortfolioPosition position = find(request.isin());

        position.recordSale(request.quantity());

        if (position.isClosed()) {
            repository.delete(position);
        } else {
            repository.save(position);
        }
    }

    public void correct(String isin, CorrectPositionRequest request) {
        PortfolioPosition position = find(isin);
        position.correct(request.ticker(), request.companyName(), request.quantity(),
                request.entryPrice(), request.purchaseDate());
        repository.save(position);
    }

    public void delete(String isin) {
        repository.delete(find(isin));
    }

    public List<PortfolioPositionView> listForUser() {
        List<PortfolioPosition> positions = repository.findAll();
        Map<String, StockGroupRef> groups = groupsFor(positions);
        return sorted(positions).stream()
                .map(p -> new PortfolioPositionView(p.getTicker(), p.getIsin(), p.getCompanyName(),
                        groups.get(p.getIsin())))
                .toList();
    }

    public List<AdminPortfolioPositionView> listForAdmin() {
        List<PortfolioPosition> positions = repository.findAll();
        Map<String, StockGroupRef> groups = groupsFor(positions);
        return sorted(positions).stream()
                .map(p -> new AdminPortfolioPositionView(p.getTicker(), p.getIsin(), p.getCompanyName(),
                        groups.get(p.getIsin()), p.getQuantity(), p.getEntryPrice(), p.getPurchaseDate()))
                .toList();
    }

    /** By company name (case-insensitive), then ISIN. */
    private static List<PortfolioPosition> sorted(List<PortfolioPosition> positions) {
        return positions.stream().sorted(Comparator
                .comparing(PortfolioPosition::getCompanyName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(PortfolioPosition::getIsin)).toList();
    }

    private Map<String, StockGroupRef> groupsFor(List<PortfolioPosition> positions) {
        return stockGroups.groupsByIsin(positions.stream().map(PortfolioPosition::getIsin).toList());
    }

    private static String normalizeIsin(String isin) {
        return isin == null ? "" : isin.trim().toUpperCase();
    }

    private PortfolioPosition find(String isin) {
        String normalized = normalizeIsin(isin);
        if (!CompanySnapshot.isValidIsin(normalized)) {
            throw new PositionNotFoundException(normalized); // can never exist; do not send it to DynamoDB
        }
        return repository.findByIsin(normalized)
                .orElseThrow(() -> new PositionNotFoundException(normalized));
    }
}
