package com.valuescreener.research;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
public class StockGroupService {

    private final StockGroupRepository groups;
    private final CompanySnapshotRepository snapshots;

    public StockGroupService(StockGroupRepository groups, CompanySnapshotRepository snapshots) {
        this.groups = groups;
        this.snapshots = snapshots;
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
        String name = request.name() == null ? "" : request.name().trim();
        if (groups.existsById(key)) {
            throw new StockGroupConflictException("Group key already exists: " + key);
        }
        if (groups.existsByNameIgnoreCase(name)) {
            throw new StockGroupConflictException("Group name already exists: " + name);
        }
        StockGroup group = new StockGroup(key, name, groups.maxSortOrder() + 10);
        return toView(groups.save(group));
    }

    @Transactional
    public StockGroupView update(String key, UpdateStockGroupRequest request) {
        StockGroup group = find(key);
        if (request.name() != null) {
            String name = request.name().trim();
            if (!name.equalsIgnoreCase(group.getName()) && groups.existsByNameIgnoreCase(name)) {
                throw new StockGroupConflictException("Group name already exists: " + name);
            }
            group.rename(name);
        }
        if (request.sortOrder() != null) {
            group.moveTo(request.sortOrder());
        }
        return toView(group);
    }

    @Transactional
    public void delete(String key) {
        StockGroup group = find(key);
        long used = snapshots.countByStockGroup_Key(group.getKey());
        if (used > 0) {
            throw new StockGroupConflictException(
                    "Group " + group.getKey() + " still has " + used + " stock(s) assigned");
        }
        groups.delete(group);
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
                snapshots.countByStockGroup_Key(group.getKey()));
    }
}
