package com.hdfc.banking.repository;

import com.hdfc.banking.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {


    Page<AuditLog> findByChangedByOrderByTimestampDesc(String changedBy, Pageable pageable);


    Page<AuditLog> findByActionOrderByTimestampDesc(String action, Pageable pageable);
}