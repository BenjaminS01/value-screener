package com.valuescreener.portfolio;

import com.valuescreener.thesis.InvestmentThesis;

/** Writes a purchase's position and its thesis paragraph as one all-or-nothing unit. */
public interface PurchaseWriter {

    /**
     * Saves both objects (each with its create/version condition) atomically. If either condition
     * fails nothing is written and ConcurrentUpdateException is thrown. Both objects carry their new
     * version afterwards.
     */
    void save(PortfolioPosition position, InvestmentThesis thesis);
}
