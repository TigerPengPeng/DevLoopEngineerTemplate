package com.autotrading.repository;

import com.autotrading.entity.MAAlertConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for the singleton MA alert config row.
 */
@Repository
public interface MAAlertConfigRepository extends JpaRepository<MAAlertConfigEntity, Long> {
}
