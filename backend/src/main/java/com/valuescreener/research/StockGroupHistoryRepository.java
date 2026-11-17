package com.valuescreener.research;

import java.util.List;

/** Read side only: group history is append-only and written together with its snapshot. */
public interface StockGroupHistoryRepository {

    /** Newest first. */
    List<StockGroupHistory> findBySnapshotIsin(String isin);
}
