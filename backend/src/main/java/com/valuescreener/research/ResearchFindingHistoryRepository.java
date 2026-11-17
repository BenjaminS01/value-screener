package com.valuescreener.research;

import java.util.List;

/** Read side only: finding history is append-only and written together with its snapshot. */
public interface ResearchFindingHistoryRepository {

    /** Newest first; entries with the same timestamp come back in reverse order of writing. */
    List<ResearchFindingHistory> findBySnapshotIsin(String isin);
}
