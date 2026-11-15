package com.valuescreener.research;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class StockGroupService {

    private final StockGroupRepository groups;
    private final CompanySnapshotRepository snapshots;
    private final StockGroupHistoryRepository groupHistory;

    public StockGroupService(StockGroupRepository groups, CompanySnapshotRepository snapshots,
                             StockGroupHistoryRepository groupHistory) {
        this.groups = groups;
        this.snapshots = snapshots;
        this.groupHistory = groupHistory;
    }

    @Transactional(readOnly = true)
    public List<StockGroupView> list() {
        return groups.findAll().stream()
                .sorted(Comparator.comparingInt(StockGroup::getSortOrder).thenComparing(StockGroup::getName))
                .map(this::toView)
                .toList();
    }

    @Transactional
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
        return toView(groups.create(group));
    }

    @Transactional
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

    @Transactional
    public void delete(String key) {
        StockGroup group = find(key);
        long used = snapshots.countByGroupKey(group.getKey());
        if (used > 0) {
            throw new StockGroupConflictException(
                    "Group " + group.getKey() + " still has " + used + " stock(s) assigned");
        }
        if (groupHistory.existsByPreviousGroupKeyOrNewGroupKey(group.getKey(), group.getKey())) {
            throw new StockGroupConflictException(
                    "Group " + group.getKey() + " appears in the group history and cannot be deleted");
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
    @Transactional(readOnly = true)
    public StockGroup require(String key) {
        String normalized = StockGroup.trimUpper(key);
        return groups.findById(normalized).orElseThrow(() -> new UnknownGroupException(normalized));
    }

    private StockGroup find(String key) {
        String normalized = StockGroup.trimUpper(key);
        return groups.findById(normalized).orElseThrow(() -> new StockGroupNotFoundException(normalized));
    }

    private StockGroupView toView(StockGroup group) {
        return new StockGroupView(group.getKey(), group.getName(), group.getSortOrder(),
                snapshots.countByGroupKey(group.getKey()));
    }
}
