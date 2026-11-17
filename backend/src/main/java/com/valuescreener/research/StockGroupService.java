package com.valuescreener.research;

import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class StockGroupService {

    private final StockGroupRepository groups;
    private final CompanySnapshotRepository snapshots;

    public StockGroupService(StockGroupRepository groups, CompanySnapshotRepository snapshots) {
        this.groups = groups;
        this.snapshots = snapshots;
    }

    public List<StockGroupView> list() {
        // one read of the small snapshot index, counted in memory (not the snapshot items themselves)
        Map<String, Long> counts = new HashMap<>();
        snapshots.findIndex().forEach(e -> {
            if (e.groupKey() != null) {
                counts.merge(e.groupKey(), 1L, Long::sum);
            }
        });
        return groups.findAll().stream()
                .sorted(Comparator.comparingInt(StockGroup::getSortOrder).thenComparing(StockGroup::getName))
                .map(g -> toView(g, counts.getOrDefault(g.getKey(), 0L)))
                .toList();
    }

    public StockGroupView create(CreateStockGroupRequest request) {
        String key = StockGroup.trimUpper(request.key());
        // validate first: invalid key or blank name is a 400, never a catalog lookup
        StockGroup group = new StockGroup(key, request.name(), 0);
        String name = group.getName();
        if (groups.findById(key).isPresent()) {
            throw new StockGroupConflictException("Group key already exists: " + key);
        }
        if (groups.existsByNameIgnoreCase(name)) {
            throw new StockGroupConflictException("Group name already exists: " + name);
        }
        group.moveTo(groups.maxSortOrder() + 10);
        return toView(groups.create(group), 0);
    }

    public StockGroupView update(String key, UpdateStockGroupRequest request) {
        StockGroup group = find(key);
        String previousName = group.getName();
        if (request.name() != null) {
            group.rename(request.name()); // validates (blank/too long -> IllegalArgumentException, 400)
            String name = group.getName();
            if (!name.equalsIgnoreCase(previousName) && groups.existsByNameIgnoreCase(name)) {
                throw new StockGroupConflictException("Group name already exists: " + name);
            }
        }
        if (request.sortOrder() != null) {
            group.moveTo(request.sortOrder());
        }
        if (request.name() == null && request.sortOrder() == null) {
            return toView(group);
        }
        groups.update(group, previousName);
        return toView(group);
    }

    public void delete(String key) {
        StockGroup group = find(key);
        long used = snapshots.countByGroupKey(group.getKey());
        if (used > 0) {
            throw new StockGroupConflictException(
                    "Group " + group.getKey() + " still has " + used + " stock(s) assigned");
        }
        groups.delete(group);
    }

    /** Group key to name for all groups: one read, for views that list many snapshots. */
    public Map<String, String> namesByKey() {
        Map<String, String> names = new HashMap<>();
        groups.findAll().forEach(g -> names.put(g.getKey(), g.getName()));
        return names;
    }

    /** Name of one group, or null if it does not exist. */
    public String nameOf(String key) {
        return groups.findById(key).map(StockGroup::getName).orElse(null);
    }

    /** Resolves a group by (normalized) key; used when assigning groups to stocks. */
    public StockGroup require(String key) {
        String normalized = StockGroup.trimUpper(key);
        if (!StockGroup.isValidKey(normalized)) {
            throw new UnknownGroupException(normalized); // can never exist; do not send it to DynamoDB
        }
        return groups.findById(normalized).orElseThrow(() -> new UnknownGroupException(normalized));
    }

    private StockGroup find(String key) {
        String normalized = StockGroup.trimUpper(key);
        if (!StockGroup.isValidKey(normalized)) {
            throw new StockGroupNotFoundException(normalized); // can never exist; do not send it to DynamoDB
        }
        return groups.findById(normalized).orElseThrow(() -> new StockGroupNotFoundException(normalized));
    }

    /** Single group (update): one count is fine here. */
    private StockGroupView toView(StockGroup group) {
        return toView(group, snapshots.countByGroupKey(group.getKey()));
    }

    private StockGroupView toView(StockGroup group, long stockCount) {
        return new StockGroupView(group.getKey(), group.getName(), group.getSortOrder(), stockCount);
    }
}
