package com.valuescreener.portfolio;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PortfolioService {

    private final PortfolioPositionRepository repository;

    public PortfolioService(PortfolioPositionRepository repository) {
        this.repository = repository;
    }

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

    public List<PublicPortfolioPositionView> listPublicPositions() {
        return repository.findAll().stream()
                .map(position -> new PublicPortfolioPositionView(position.getTicker(), position.getCompanyName()))
                .toList();
    }
}
