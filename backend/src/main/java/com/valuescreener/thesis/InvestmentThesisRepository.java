package com.valuescreener.thesis;

import java.util.Optional;

public interface InvestmentThesisRepository {
    Optional<InvestmentThesis> findById(String isin);

    /** Creates (version null) or updates (version must match); a lost race throws ConcurrentUpdateException. */
    InvestmentThesis save(InvestmentThesis thesis);
}
