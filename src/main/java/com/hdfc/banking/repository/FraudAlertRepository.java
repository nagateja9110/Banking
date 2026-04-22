package com.hdfc.banking.repository;

import com.hdfc.banking.entity.FraudAlert;
import com.hdfc.banking.enums.FraudAlertStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FraudAlertRepository extends JpaRepository<FraudAlert, Long> {


    List<FraudAlert> findByStatusOrderByFlaggedAtDesc(FraudAlertStatus status);


    long countByStatus(FraudAlertStatus status);
}