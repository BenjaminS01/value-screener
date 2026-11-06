package com.valuescreener.research;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/** Read-only lookup of the group assigned to each ISIN, for other modules (portfolio). */
@Component
public class StockGroupLookup {

    private final CompanySnapshotRepository repository;

    public StockGroupLookup(CompanySnapshotRepository repository) {
        this.repository = repository;
    }

    /** ISIN -> group; ISINs without a snapshot or without a group are absent. One query. */
    @Transactional(readOnly = true)
    public Map<String, StockGroupRef> groupsByIsin(Collection<String> isins) {
        Map<String, StockGroupRef> result = new HashMap<>();
        if (isins == null || isins.isEmpty()) {
            return result;
        }
        for (CompanySnapshot snapshot : repository.findAllByIsinIn(isins)) {
            StockGroup group = snapshot.getStockGroup();
            if (group != null) {
                result.put(snapshot.getIsin(), new StockGroupRef(group.getKey(), group.getName()));
            }
        }
        return result;
    }
}
