# Week 3, Day 11 — Fraud Detection Service

> **Goal:** Automatically flag suspicious transactions after every transfer.
> Admins can view and review flagged alerts.
>
> Real-world: HDFC blocks/flags your card when it detects unusual activity (large amount,
> midnight transfer, etc.)

---

## What You're Building

```
TransactionService.transfer() 
    → calls FraudDetectionService.checkAndFlag()
    → if suspicious → creates FraudAlert in DB

GET  /api/admin/fraud-alerts          → Admin views all open alerts
PUT  /api/admin/fraud-alerts/{id}/review → Admin marks alert as REVIEWED or CONFIRMED
```

---

## Fraud Rules (3 rules)

| Rule | Condition | Risk Score |
|---|---|---|
| Large Amount | Amount > ₹1,00,000 | 0.9 (very suspicious) |
| Midnight Transfer | Transfer between 12AM – 4AM | 0.7 (suspicious) |
| Velocity Check | Same account made 3+ transfers in last 10 minutes | 0.8 (suspicious) |

---

## Step 1 — Create `FraudAlertResponse.java`

Create file: `dto/response/FraudAlertResponse.java`

```java
package com.hdfc.banking.dto.response;

import com.hdfc.banking.enums.FraudAlertStatus;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class FraudAlertResponse {
    private Long id;
    private Long transactionId;
    private String reason;
    private BigDecimal riskScore;
    private FraudAlertStatus status;
    private LocalDateTime flaggedAt;
    private String reviewedBy;
    private LocalDateTime reviewedAt;
}
```

---

## Step 2 — Add Velocity Query to `TransactionRepository.java`

Open `repository/TransactionRepository.java` and add:

```java
import java.time.LocalDateTime;

// Count how many transfers this account made in the last N minutes
@Query("""
    SELECT COUNT(t) FROM Transaction t
    WHERE t.fromAccount = :account
    AND t.transactionType = 'TRANSFER'
    AND t.createdAt >= :since
""")
long countRecentTransfers(
    @Param("account") Account account,
    @Param("since") LocalDateTime since
);
```

---

## Step 3 — Create `FraudDetectionService.java`

Create file: `service/FraudDetectionService.java`

### Class setup:
```java
@Slf4j
@Service
@RequiredArgsConstructor
public class FraudDetectionService {

    private final FraudAlertRepository fraudAlertRepository;
    private final TransactionRepository transactionRepository;

    private static final BigDecimal LARGE_AMOUNT_THRESHOLD = new BigDecimal("100000");
    private static final int VELOCITY_LIMIT = 3;
    private static final int VELOCITY_WINDOW_MINUTES = 10;
}
```

---

### Method — `checkAndFlag()`

```java
public void checkAndFlag(Transaction transaction, Account fromAccount) {

    StringBuilder reason = new StringBuilder();
    BigDecimal riskScore = BigDecimal.ZERO;

    // RULE 1: Large amount check
    if (transaction.getAmount().compareTo(LARGE_AMOUNT_THRESHOLD) > 0) {
        reason.append("Large amount transfer: ₹").append(transaction.getAmount()).append(". ");
        riskScore = riskScore.max(new BigDecimal("0.9"));
    }

    // RULE 2: Midnight transfer (12AM to 4AM)
    int hour = LocalDateTime.now().getHour();
    if (hour >= 0 && hour < 4) {
        reason.append("Transfer made at unusual hour (").append(hour).append(":00). ");
        riskScore = riskScore.max(new BigDecimal("0.7"));
    }

    // RULE 3: Velocity check (too many transfers in short time)
    LocalDateTime since = LocalDateTime.now().minusMinutes(VELOCITY_WINDOW_MINUTES);
    long recentCount = transactionRepository.countRecentTransfers(fromAccount, since);

    if (recentCount >= VELOCITY_LIMIT) {
        reason.append("High velocity: ").append(recentCount)
              .append(" transfers in last ").append(VELOCITY_WINDOW_MINUTES).append(" minutes. ");
        riskScore = riskScore.max(new BigDecimal("0.8"));
    }

    // If any rule fired, create a fraud alert
    if (riskScore.compareTo(BigDecimal.ZERO) > 0) {
        FraudAlert alert = FraudAlert.builder()
                .transaction(transaction)
                .reason(reason.toString().trim())
                .riskScore(riskScore)
                .status(FraudAlertStatus.OPEN)
                .build();

        fraudAlertRepository.save(alert);
        log.warn("FRAUD ALERT created for transaction {} — reason: {}", transaction.getId(), reason);
    }
}
```

### Imports needed:
```java
import com.hdfc.banking.entity.Account;
import com.hdfc.banking.entity.FraudAlert;
import com.hdfc.banking.entity.Transaction;
import com.hdfc.banking.enums.FraudAlertStatus;
import com.hdfc.banking.repository.FraudAlertRepository;
import com.hdfc.banking.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.LocalDateTime;
```

---

## Step 4 — Hook into `TransactionService.java`

Open `service/TransactionService.java` and:

### 4a — Inject `FraudDetectionService`:
```java
// Add to the fields (after existing fields):
private final FraudDetectionService fraudDetectionService;
```

### 4b — Call fraud check AFTER saving the transfer transaction (end of `transfer()` method):

```java
// After transactionRepository.save(transaction) — add this line:
fraudDetectionService.checkAndFlag(transaction, fromAccount);
```

Your `transfer()` method end should look like:
```java
transactionRepository.save(transaction);
log.info("Transfer {} from {} to {}", ...);
fraudDetectionService.checkAndFlag(transaction, fromAccount);   // ← add this
return mapToResponse(transaction);
```

---

## Step 5 — Add Admin Endpoints to `AdminController.java`

Open `controller/AdminController.java` and:

### 5a — Inject `FraudAlertRepository`:
```java
private final FraudAlertRepository fraudAlertRepository;
```

Add import:
```java
import com.hdfc.banking.entity.FraudAlert;
import com.hdfc.banking.enums.FraudAlertStatus;
import com.hdfc.banking.repository.FraudAlertRepository;
import com.hdfc.banking.dto.response.FraudAlertResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import java.time.LocalDateTime;
```

### 5b — Add 2 new endpoints:

```java
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

// Review a fraud alert (REVIEWED = false alarm, CONFIRMED = real fraud)
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
```

Also add `import java.util.ArrayList;` if not already there.

---

## Step 6 — Test in Thunder Client

### Setup: Register → Login → OTP → token → create account → deposit ₹2,00,000

### Trigger fraud detection (large amount):
```
POST /api/transactions/transfer
Authorization: Bearer <admin-token>

Body:
{
  "fromAccountNumber": "HDFC11111111",
  "toAccountNumber": "HDFC22222222",
  "amount": 150000,
  "description": "Test fraud"
}

→ Transfer succeeds, but fraud alert is created in background
```

### View fraud alerts (admin only):
```
GET /api/admin/fraud-alerts
Authorization: Bearer <admin-token>

Expected:
[{
  "id": 1,
  "transactionId": 5,
  "reason": "Large amount transfer: ₹150000.",
  "riskScore": 0.900,
  "status": "OPEN",
  "flaggedAt": "2026-05-08T..."
}]
```

### Review the alert:
```
PUT /api/admin/fraud-alerts/1/review?status=CONFIRMED
Authorization: Bearer <admin-token>
→ "Alert 1 marked as CONFIRMED"
```

### Verify in H2 Console:
```sql
SELECT * FROM fraud_alerts;
```

---

## Step 7 — Git Commit

```bash
git add .
git commit -m "Week3-Day11: FraudDetectionService with rule-based flagging"
git push
```

---

## Key Concepts Learned Today

| Concept | Explanation |
|---|---|
| Rule-based fraud detection | Check amount, time, velocity — no ML needed |
| Risk score (0.0 → 1.0) | Higher = more suspicious |
| Velocity check | Count how many transactions happened in X minutes |
| FraudAlertStatus | OPEN → REVIEWED (false alarm) or CONFIRMED (real fraud) |
| `BigDecimal.max()` | Takes the highest risk score from multiple rules |
| Post-transaction hook | Call fraud check AFTER transaction saved (not before) |

---

## After This → Week 4 — Spring Security JWT Unit Tests + Integration Tests
