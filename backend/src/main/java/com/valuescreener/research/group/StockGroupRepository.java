package com.valuescreener.research.group;

import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;

import java.util.List;
import java.util.Optional;

public interface StockGroupRepository {

    Optional<StockGroup> findById(String key);

    List<StockGroup> findAll();

    /** Name uniqueness is case-insensitive and ignores surrounding blanks. */
    boolean existsByNameIgnoreCase(String name);

    /** Highest sort order, 0 when there are no groups. */
    int maxSortOrder();

    /** @throws StockGroupConflictException when the key or the name is taken */
    StockGroup create(StockGroup group);

    /**
     * Saves name and sort order of a group loaded earlier; a changed name moves the name guard.
     * @param previousName the name the group had when it was loaded
     * @throws StockGroupConflictException when the new name is taken
     * @throws com.valuescreener.persistence.ConcurrentUpdateException when the group changed meanwhile
     */
    StockGroup update(StockGroup group, String previousName);

    /** @throws StockGroupConflictException when the group was ever referenced (or changed meanwhile) */
    void delete(StockGroup group);

    /**
     * For Task 4: add this to the transaction that first stores a reference to the group (snapshot group
     * assignment). It fails the transaction when the group no longer exists and makes
     * {@link #delete} fail from then on.
     */
    static TransactWriteItem markReferenced(String tableName, String groupKey) {
        return StockGroupItem.markReferenced(tableName, groupKey);
    }
}
