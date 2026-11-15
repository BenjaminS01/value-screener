package com.valuescreener.portfolio;

import java.util.List;
import java.util.Optional;

public interface PortfolioPositionRepository {
    Optional<PortfolioPosition> findByIsin(String isin);

    List<PortfolioPosition> findAll();

    /** Creates (version null) or updates (version must match); a lost race throws ConcurrentUpdateException. */
    PortfolioPosition save(PortfolioPosition position);

    /** Deletes exactly the loaded version; a lost race throws ConcurrentUpdateException. */
    void delete(PortfolioPosition position);
}
