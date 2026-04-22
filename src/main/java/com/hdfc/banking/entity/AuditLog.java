package com.hdfc.banking.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * AUDIT_LOG ENTITY — maps to 'audit_logs' table.
 *
 * Created AUTOMATICALLY by AuditAspect (Spring AOP) — Week 3.
 * You never call auditLogRepository.save() manually in service classes.
 * AOP intercepts service method calls and saves logs transparently.
 *
 * WHY separate from Hibernate Envers (accounts_aud)?
 *
 * Envers tracks FIELD-LEVEL changes:
 *   "Account HDFC001: balance changed from 10000 → 8000 at rev 5"
 *
 * AuditLog tracks BUSINESS EVENTS:
 *   "User test@hdfc.com performed TRANSFER at 2024-01-15 10:30:00"
 *   "Action failed for admin@hdfc.com: AccountNotFoundException"
 *
 * Together they answer ALL compliance questions:
 *   "Who transferred money from this account?" → AuditLog (changedBy)
 *   "What was the balance before the transfer?" → Envers (accounts_aud)
 *
 * WHY oldValue/newValue as TEXT (not foreign keys)?
 * Flexibility: can store JSON snapshot of any object.
 * Example: newValue = "{\"balance\":8000,\"status\":\"ACTIVE\"}"
 * No FK means the record survives even if the referenced entity is deleted.
 */
@Entity
@Table(name = "audit_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entity_type", length = 50)
    private String entityType;
    // "Transaction", "Account", "User", "ERROR"

    @Column(length = 100)
    private String action;
    // "transfer", "deposit", "freezeAccount", "verifyLoginOtp"

    @Column(name = "changed_by", length = 150)
    private String changedBy;
    // email of the user who triggered this action (from SecurityContextHolder)
    // "SYSTEM" if triggered by a scheduler job

    @Column(name = "old_value", columnDefinition = "TEXT")
    // TEXT = unlimited length (no 255 char limit) — needed for JSON snapshots
    private String oldValue;
    // JSON representation of state BEFORE the action
    // e.g., "{\"balance\":10000}" or "EXCEPTION: InsufficientFundsException"

    @Column(name = "new_value", columnDefinition = "TEXT")
    private String newValue;
    // JSON representation of state AFTER the action
    // e.g., "{\"balance\":8000,\"status\":\"SUCCESS\"}"

    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
    // When did this audit event happen?
}