package com.valuescreener.research;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CompanySnapshotRepository extends JpaRepository<CompanySnapshot, Long> {
    Optional<CompanySnapshot> findByIsin(String isin);

    long countByStockGroup_Key(String key);

    @Override
    @EntityGraph(attributePaths = "stockGroup")
    List<CompanySnapshot> findAll();

    @EntityGraph(attributePaths = "stockGroup")
    List<CompanySnapshot> findAllByStockGroup_Key(String key);

    @EntityGraph(attributePaths = "stockGroup")
    List<CompanySnapshot> findAllByStockGroupIsNull();

    @EntityGraph(attributePaths = "stockGroup")
    List<CompanySnapshot> findAllByIsinIn(Collection<String> isins);
}
