package com.autotrading.repository;

import com.autotrading.entity.AlertRecord;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AlertRecordRepository extends JpaRepository<AlertRecord, Long> {

    List<AlertRecord> findAllByOrderByTimestampDesc(Pageable pageable);

    List<AlertRecord> findAllByOrderByTimestampDesc();
}
