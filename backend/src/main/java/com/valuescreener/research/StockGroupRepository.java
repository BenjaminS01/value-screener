package com.valuescreener.research;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface StockGroupRepository extends JpaRepository<StockGroup, String> {

    boolean existsByNameIgnoreCase(String name);

    @Query("select coalesce(max(g.sortOrder), 0) from StockGroup g")
    int maxSortOrder();
}
