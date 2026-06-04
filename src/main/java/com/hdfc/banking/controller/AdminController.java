package com.hdfc.banking.controller;

import com.hdfc.banking.dto.response.AccountResponse;
import com.hdfc.banking.dto.response.FraudAlertResponse;
import com.hdfc.banking.dto.response.TransactionResponse;
import com.hdfc.banking.dto.response.UserSummaryResponse;
import com.hdfc.banking.entity.AuditLog;
import com.hdfc.banking.entity.FraudAlert;
import com.hdfc.banking.enums.FraudAlertStatus;
import com.hdfc.banking.repository.AuditLogRepository;
import com.hdfc.banking.repository.FraudAlertRepository;
import com.hdfc.banking.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final FraudAlertRepository fraudAlertRepository;
    private final AuditLogRepository auditLogRepository;

    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UserSummaryResponse>> getAllUsers() {
        return ResponseEntity.ok(adminService.getAllUsers());
    }

    @GetMapping("/accounts")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<AccountResponse>> getAllAccounts() {
        return ResponseEntity.ok(adminService.getAllAccounts());
    }

    @PutMapping("/accounts/{accountNumber}/freeze")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> freeze(@PathVariable String accountNumber) {
        return ResponseEntity.ok(adminService.freezeAccount(accountNumber));
    }

    @PutMapping("/accounts/{accountNumber}/unfreeze")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> unfreeze(@PathVariable String accountNumber) {
        return ResponseEntity.ok(adminService.unfreezeAccount(accountNumber));
    }

    @GetMapping("/transactions/{accountNumber}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<TransactionResponse>> transactions(@PathVariable String accountNumber) {
        return ResponseEntity.ok(adminService.getAccountTransactions(accountNumber));
    }

    // View all OPEN fraud alerts
    @GetMapping("/fraud-alerts")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<FraudAlertResponse>> getFraudAlerts() {
        List<FraudAlert> alerts = fraudAlertRepository.findByStatusOrderByFlaggedAtDesc(FraudAlertStatus.OPEN);

        List<FraudAlertResponse> result = new ArrayList<>();
        for (FraudAlert alert : alerts) {
            result.add(FraudAlertResponse.builder()
                    .id(alert.getId())
                    .transactionId(alert.getTransaction().getId())
                    .reason(alert.getReason())
                    .riskScore(alert.getRiskScore())
                    .status(alert.getStatus())
                    .flaggedAt(alert.getFlaggedAt())
                    .build());
        }
        return ResponseEntity.ok(result);
    }

    @PutMapping("/fraud-alerts/{id}/review")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> reviewAlert(
            @PathVariable Long id,
            @RequestParam FraudAlertStatus status,
            @AuthenticationPrincipal UserDetails userDetails) {

        FraudAlert alert = fraudAlertRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Fraud alert not found: " + id));

        alert.setStatus(status);
        alert.setReviewedBy(userDetails.getUsername());
        alert.setReviewedAt(LocalDateTime.now());
        fraudAlertRepository.save(alert);

        return ResponseEntity.ok("Alert " + id + " marked as " + status);
    }

    @GetMapping("/audit-logs")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<AuditLog>> getAuditLogs() {
        return ResponseEntity.ok(auditLogRepository.findAll());
    }
}
