package com.autotrading.repository;

import com.autotrading.entity.SectorReportRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SectorReportRecordRepository extends JpaRepository<SectorReportRecord, Long> {

    List<SectorReportRecord> findAllByOrderByGeneratedAtDesc();

    Optional<SectorReportRecord> findFirstByReportDateOrderByGeneratedAtDesc(String reportDate);

    Optional<SectorReportRecord> findFirstByOrderByGeneratedAtDesc();
}
