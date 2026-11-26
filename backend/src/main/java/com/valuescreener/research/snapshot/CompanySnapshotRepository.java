package com.valuescreener.research.snapshot;

import com.valuescreener.research.history.ResearchFindingHistory;
import java.util.List;
import java.util.Optional;

public interface CompanySnapshotRepository {

    Optional<CompanySnapshot> findByIsin(String isin);

    /** Number of snapshots in the group, from the snapshot index. */
    long countByGroupKey(String key);

    /**
     * The snapshot index (list fields and group of every snapshot), eventually consistent: for the research list,
     * group counts and lookups, one small read instead of reading the snapshot items.
     */
    List<SnapshotIndexEntry> findIndex();

    /**
     * Writes the snapshot, its index item, its new finding history entries and (when {@code groupChanged}) the
     * "group is referenced" mark of the snapshot's group in ONE transaction: all or nothing. Create when the
     * snapshot has no version yet, otherwise the stored version must match. On success the snapshot carries its
     * new version.
     *
     * @param groupChanged the snapshot's group key is new for it, so that group must be marked as referenced
     * @throws com.valuescreener.persistence.ConcurrentUpdateException when the snapshot was created or changed
     *         concurrently
     * @throws UnknownGroupException when the group was deleted meanwhile
     */
    CompanySnapshot save(CompanySnapshot snapshot, List<ResearchFindingHistory> findingHistory,
                         boolean groupChanged);
}
