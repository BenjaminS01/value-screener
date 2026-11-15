package com.valuescreener.research;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CompanySnapshotRepository extends JpaRepository<CompanySnapshot, Long> {
    Optional<CompanySnapshot> findByIsin(String isin);

    long countByGroupKey(String key);

    List<CompanySnapshot> findAllByGroupKey(String key);

    List<CompanySnapshot> findAllByGroupKeyIsNull();

    List<CompanySnapshot> findAllByIsinIn(Collection<String> isins);
}
