package com.autotrading.repository;

import com.autotrading.entity.SignalRecord;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SignalRecordRepository extends JpaRepository<SignalRecord, Long> {

    List<SignalRecord> findAllByOrderByTimestampDesc(Pageable pageable);

    List<SignalRecord> findAllByOrderByTimestampDesc();
}
