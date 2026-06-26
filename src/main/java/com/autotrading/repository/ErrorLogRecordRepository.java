package com.autotrading.repository;

import com.autotrading.entity.ErrorLogRecord;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ErrorLogRecordRepository extends JpaRepository<ErrorLogRecord, Long> {

    List<ErrorLogRecord> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<ErrorLogRecord> findAllByOrderByCreatedAtDesc();
}
