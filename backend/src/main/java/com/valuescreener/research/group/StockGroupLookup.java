package com.valuescreener.research.group;

import com.valuescreener.research.snapshot.CompanySnapshotRepository;
import com.valuescreener.research.snapshot.SnapshotIndexEntry;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Read-only lookup of the group assigned to each ISIN, for other modules (portfolio). */
@Component
public class StockGroupLookup {

    private final CompanySnapshotRepository repository;
    private final StockGroupRepository groups;

    public StockGroupLookup(CompanySnapshotRepository repository, StockGroupRepository groups) {
        this.repository = repository;
        this.groups = groups;
    }

    /**
     * ISIN -> group; ISINs without a snapshot or without a group are absent. Reads the snapshot index once and
     * the groups once (both eventually consistent), never the snapshot items.
     */
    public Map<String, StockGroupRef> groupsByIsin(Collection<String> isins) {
        Map<String, StockGroupRef> result = new HashMap<>();
        if (isins == null || isins.isEmpty()) {
            return result;
        }
        Set<String> wanted = new HashSet<>(isins);
        List<SnapshotIndexEntry> grouped = repository.findIndex().stream()
                .filter(e -> e.groupKey() != null && wanted.contains(e.isin())).toList();
        if (grouped.isEmpty()) {
            return result;
        }
        Map<String, String> names = new HashMap<>();
        groups.findAll().forEach(g -> names.put(g.getKey(), g.getName()));
        for (SnapshotIndexEntry entry : grouped) {
            String key = entry.groupKey();
            result.put(entry.isin(), new StockGroupRef(key, names.getOrDefault(key, key)));
        }
        return result;
    }
}
