package com.valuescreener.research;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Read-only lookup of the group assigned to each ISIN, for other modules (portfolio). */
@Component
public class StockGroupLookup {

    private final CompanySnapshotRepository repository;
    private final StockGroupRepository groups;

    public StockGroupLookup(CompanySnapshotRepository repository, StockGroupRepository groups) {
        this.repository = repository;
        this.groups = groups;
    }

    /** ISIN -> group; ISINs without a snapshot or without a group are absent. One query plus one group read. */
    public Map<String, StockGroupRef> groupsByIsin(Collection<String> isins) {
        Map<String, StockGroupRef> result = new HashMap<>();
        if (isins == null || isins.isEmpty()) {
            return result;
        }
        List<CompanySnapshot> grouped = repository.findAllByIsinIn(isins).stream()
                .filter(s -> s.getGroupKey() != null).toList();
        if (grouped.isEmpty()) {
            return result;
        }
        Map<String, String> names = new HashMap<>();
        groups.findAll().forEach(g -> names.put(g.getKey(), g.getName()));
        for (CompanySnapshot snapshot : grouped) {
            String key = snapshot.getGroupKey();
            result.put(snapshot.getIsin(), new StockGroupRef(key, names.getOrDefault(key, key)));
        }
        return result;
    }
}
