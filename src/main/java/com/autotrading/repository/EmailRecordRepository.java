package com.autotrading.repository;

import com.autotrading.entity.EmailRecordEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EmailRecordRepository extends JpaRepository<EmailRecordEntity, Long> {

    List<EmailRecordEntity> findAllByOrderByTimestampDesc(Pageable pageable);

    List<EmailRecordEntity> findAllByOrderByTimestampDesc();

    long countByType(String type);
}
