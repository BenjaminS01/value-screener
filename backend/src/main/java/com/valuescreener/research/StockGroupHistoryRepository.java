package com.valuescreener.research;

import org.springframework.data.repository.Repository;

import java.util.List;

/** Insert-only on purpose: extends Repository, not JpaRepository, so no update or delete methods exist. */
public interface StockGroupHistoryRepository extends Repository<StockGroupHistory, Long> {

    StockGroupHistory save(StockGroupHistory entry);

    List<StockGroupHistory> findByCompanySnapshotIdOrderByChangedAtDescIdDesc(Long companySnapshotId);
}
