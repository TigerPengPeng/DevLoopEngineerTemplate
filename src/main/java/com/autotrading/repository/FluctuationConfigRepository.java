package com.autotrading.repository;

import com.autotrading.entity.FluctuationConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for the singleton fluctuation config row (BF-2).
 */
@Repository
public interface FluctuationConfigRepository extends JpaRepository<FluctuationConfigEntity, Long> {
}
