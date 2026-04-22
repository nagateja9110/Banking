package com.hdfc.banking.entity;

import com.hdfc.banking.enums.FraudAlertStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * FRAUD_ALERT ENTITY — maps to 'fraud_alerts' table.
 *
 * Created by FraudDetectionService when:
 * 1. Java rule fires: amount > 50,000 OR unusual time (midnight)
 * 2. Python ML model returns risk_score > 0.7
 *
 * WHY risk_score as DECIMAL (0.0 to 1.0)?
 * The ML model (Isolation Forest) returns a float confidence score.
 * 0.0 = definitely normal | 1.0 = definitely fraud
 * Java rules set it to 0.9 (high confidence fraud signal)
 * Admin uses this to prioritize which alerts to review first.
 *
 * WHY linked to Transaction and not Account?
 * Fraud is per-transaction, not per-account.
 * One account can have multiple flagged transactions.
 * One transaction maps to at most one fraud alert.
 *
 * WHY reviewedBy (String not User FK)?
 * Simple admin email stored as string.
 * Even if admin's account is deleted later, we still know WHO reviewed it.
 * Audit trail must be immutable — FK would break if admin user is deleted.
 */

@Entity
@Table(name = "fraud_alerts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @ManyToOne
    @JoinColumn(name = "transaction_id", nullable = false)
    private Transaction transaction;

    @Column(name = "risk_score", precision = 5, scale = 3)
    private BigDecimal riskScore;


    @Column(length = 1000)
    private String reason;


    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private FraudAlertStatus status = FraudAlertStatus.OPEN;


    @Column(name = "reviewed_by", length = 150)
    private String reviewedBy;


    @Column(name = "flagged_at")
    @Builder.Default
    private LocalDateTime flaggedAt = LocalDateTime.now();

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;
}