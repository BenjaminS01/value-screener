package com.valuescreener.portfolio;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PortfolioService {

    private final PortfolioPositionRepository repository;

    public PortfolioService(PortfolioPositionRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void buy(BuyPositionRequest request) {
        PortfolioPosition position = repository.findByIsin(request.isin()).orElse(null);

        if (position == null) {
            repository.save(new PortfolioPosition(
                    request.ticker(), request.isin(), request.companyName(),
                    request.quantity(), request.entryPrice(), request.purchaseDate()));
            return;
        }

        position.recordPurchase(request.quantity(), request.entryPrice());
        repository.save(position);
    }

    @Transactional
    public void sell(SellPositionRequest request) {
        PortfolioPosition position = repository.findByIsin(request.isin())
                .orElseThrow(() -> new PositionNotFoundException(request.isin()));

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

    public List<PortfolioPositionView> listForUser() {
        return repository.findAll().stream()
                .map(p -> new PortfolioPositionView(p.getTicker(), p.getIsin(), p.getCompanyName()))
                .toList();
    }

    public List<AdminPortfolioPositionView> listForAdmin() {
        return repository.findAll().stream()
                .map(p -> new AdminPortfolioPositionView(p.getTicker(), p.getIsin(), p.getCompanyName(),
                        p.getQuantity(), p.getEntryPrice(), p.getPurchaseDate()))
                .toList();
    }

    private PortfolioPosition find(String isin) {
        String normalized = isin == null ? "" : isin.trim().toUpperCase();
        return repository.findByIsin(normalized)
                .orElseThrow(() -> new PositionNotFoundException(normalized));
    }
}
