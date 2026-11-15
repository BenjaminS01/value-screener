package com.valuescreener.research;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CompanySnapshotRepository {

    Optional<CompanySnapshot> findByIsin(String isin);

    List<CompanySnapshot> findAll();

    List<CompanySnapshot> findAllByGroupKey(String key);

    List<CompanySnapshot> findAllWithoutGroup();

    List<CompanySnapshot> findAllByIsinIn(Collection<String> isins);

    long countByGroupKey(String key);

    /**
     * Writes the snapshot, its new history entries and (when a group history entry is present) the
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
