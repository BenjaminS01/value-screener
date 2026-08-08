package com.valuescreener.research;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CompanySnapshotRepository extends JpaRepository<CompanySnapshot, Long> {
    Optional<CompanySnapshot> findByIsin(String isin);
}
