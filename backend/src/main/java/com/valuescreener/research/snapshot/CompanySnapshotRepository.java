package com.valuescreener.research.snapshot;

import com.valuescreener.research.history.ResearchFindingHistory;
import com.valuescreener.research.history.StockGroupHistory;
import java.util.List;
import java.util.Optional;

public interface CompanySnapshotRepository {

    Optional<CompanySnapshot> findByIsin(String isin);

    List<CompanySnapshot> findAll();

    List<CompanySnapshot> findAllByGroupKey(String key);

    List<CompanySnapshot> findAllWithoutGroup();

    /** Number of snapshots in the group, from the snapshot index. */
    long countByGroupKey(String key);

    /**
     * The snapshot index (ISIN and group of every snapshot), eventually consistent: for group counts and
     * lookups, one small read instead of reading the snapshot items.
     */
    List<SnapshotIndexEntry> findIndex();

    /**
     * Writes the snapshot, its index item, its new history entries and (when a group history entry is present) the
     * "group is referenced" mark in ONE transaction: all or nothing. Create when the snapshot has no version
     * yet, otherwise the stored version must match. On success the snapshot carries its new version.
     *
     * @throws com.valuescreener.persistence.ConcurrentUpdateException when the snapshot was created or changed
     *         concurrently
     * @throws UnknownGroupException when the group was deleted meanwhile
     */
    CompanySnapshot save(CompanySnapshot snapshot, List<ResearchFindingHistory> findingHistory,
                         Optional<StockGroupHistory> groupHistory);
}
