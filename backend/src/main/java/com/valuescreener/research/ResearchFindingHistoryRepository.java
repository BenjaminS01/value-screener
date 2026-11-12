package com.valuescreener.research;

import org.springframework.data.repository.Repository;

import java.util.List;

/** Insert-only on purpose: extends Repository, not JpaRepository, so no update or delete methods exist. */
public interface ResearchFindingHistoryRepository extends Repository<ResearchFindingHistory, Long> {

    ResearchFindingHistory save(ResearchFindingHistory entry);

    List<ResearchFindingHistory> findByCompanySnapshotIdOrderByReplacedAtDescIdDesc(Long companySnapshotId);
}
