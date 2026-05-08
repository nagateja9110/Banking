# Week 3, Day 9 — Account Statement (Date-Range Filtering)

> **Goal:** Allow users to generate a mini account statement for a chosen date range.
> Shows: opening balance, all transactions, closing balance, total credits & debits.
>
> Real-world banking: "Show last 3 months transactions" in HDFC NetBanking.

---

## What You're Building

```
GET /api/statements/{accountNumber}?from=2026-01-01&to=2026-04-30
→ Returns full statement with summary + transaction list
```

---

## Understanding Opening & Closing Balance

```
Opening Balance = Balance at the START of the period ("from" date)
Closing Balance = Balance at the END of the period ("to" date)
```

**Example:**
```
Statement: Jan 1 → Apr 30

Jan 1:  Balance = ₹10,000   ← Opening Balance
  Jan 15:  Deposit  +₹5,000
  Feb 20:  Withdraw -₹3,000
Apr 30: Balance = ₹12,000   ← Closing Balance

Today (May 8): ₹15,000     ← CURRENT balance (NOT the same as closing!)
```

**Why `account.getBalance()` is WRONG for closing balance:**
The user may have done more transactions after the "to" date.
If they deposited ₹3,000 on May 1 (after the period), `getBalance()` returns ₹15,000,
but the actual closing balance on Apr 30 was ₹12,000.

**Correct approach — work backwards from current balance:**

```
closingBalance  = currentBalance - creditsAfterPeriod + debitsAfterPeriod
openingBalance  = closingBalance - creditsDuringPeriod + debitsDuringPeriod
```

---

## Step 1 — Add Queries to `TransactionRepository.java`

Open `repository/TransactionRepository.java` and add **two** queries:

```java
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;

// Query 1: Transactions WITHIN a date range (for the statement period)
@Query("""
    SELECT t FROM Transaction t
    WHERE (t.fromAccount = :account OR t.toAccount = :account)
    AND t.createdAt BETWEEN :from AND :to
    ORDER BY t.createdAt DESC
""")
List<Transaction> findByAccountAndDateRange(
    @Param("account") Account account,
    @Param("from") LocalDateTime from,
    @Param("to") LocalDateTime to
);

// Query 2: Transactions AFTER a date (to calculate balance at period end)
@Query("""
    SELECT t FROM Transaction t
    WHERE (t.fromAccount = :account OR t.toAccount = :account)
    AND t.createdAt > :from
    ORDER BY t.createdAt ASC
""")
List<Transaction> findByAccountAfterDate(
    @Param("account") Account account,
    @Param("from") LocalDateTime from
);
```

---

## Step 2 — Create `StatementResponse.java`

Create file: `dto/response/StatementResponse.java`

```java
package com.hdfc.banking.dto.response;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class StatementResponse {
    private String accountNumber;
    private String accountHolderName;
    private LocalDate fromDate;
    private LocalDate toDate;
    private BigDecimal openingBalance;
    private BigDecimal closingBalance;
    private BigDecimal totalCredits;
    private BigDecimal totalDebits;
    private int totalTransactions;
    private List<TransactionResponse> transactions;
}
```

---

## Step 3 — Create `StatementService.java`

Create file: `service/StatementService.java`

### Class setup:
```java
@Slf4j
@Service
@RequiredArgsConstructor
public class StatementService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
}
```

---

### Method — `generateStatement()`

```java
public StatementResponse generateStatement(
        String accountNumber,
        LocalDate from,
        LocalDate to,
        String email) {

    // STEP 1: Load account + validate ownership
    Account account = accountRepository.findByAccountNumber(accountNumber)
            .orElseThrow(() -> new AccountNotFoundException(accountNumber));

    if (!account.getUser().getEmail().equals(email)) {
        throw new UnauthorizedException("This account does not belong to you");
    }

    // STEP 2: Validate date range
    if (from.isAfter(to)) {
        throw new RuntimeException("'from' date cannot be after 'to' date");
    }
    if (from.isBefore(LocalDate.now().minusYears(1))) {
        throw new RuntimeException("Statement cannot be older than 1 year");
    }

    // STEP 3: Define time boundaries
    LocalDateTime fromDateTime = from.atStartOfDay();           // Jan 1 00:00:00
    LocalDateTime toDateTime   = to.atTime(23, 59, 59);         // Apr 30 23:59:59

    // STEP 4: Get transactions WITHIN the period
    List<Transaction> periodTransactions = transactionRepository
            .findByAccountAndDateRange(account, fromDateTime, toDateTime);

    // STEP 5: Calculate credits and debits DURING the period
    BigDecimal totalCredits = BigDecimal.ZERO;
    BigDecimal totalDebits  = BigDecimal.ZERO;

    for (Transaction t : periodTransactions) {
        boolean isCredit = t.getToAccount() != null &&
                t.getToAccount().getAccountNumber().equals(accountNumber);
        if (isCredit) {
            totalCredits = totalCredits.add(t.getAmount());
        } else {
            totalDebits = totalDebits.add(t.getAmount());
        }
    }

    // STEP 6: Calculate closing balance by working backwards
    // Get all transactions AFTER the period
    List<Transaction> afterTransactions = transactionRepository
            .findByAccountAfterDate(account, toDateTime);

    BigDecimal creditsAfter = BigDecimal.ZERO;
    BigDecimal debitsAfter  = BigDecimal.ZERO;

    for (Transaction t : afterTransactions) {
        boolean isCredit = t.getToAccount() != null &&
                t.getToAccount().getAccountNumber().equals(accountNumber);
        if (isCredit) {
            creditsAfter = creditsAfter.add(t.getAmount());
        } else {
            debitsAfter = debitsAfter.add(t.getAmount());
        }
    }

    // closingBalance = currentBalance - creditsAfter + debitsAfter
    BigDecimal currentBalance  = account.getBalance();
    BigDecimal closingBalance  = currentBalance.subtract(creditsAfter).add(debitsAfter);

    // openingBalance = closingBalance - creditsDuring + debitsDuring
    BigDecimal openingBalance  = closingBalance.subtract(totalCredits).add(totalDebits);

    // STEP 7: Map transactions to response DTOs
    List<TransactionResponse> txnResponses = new ArrayList<>();
    for (Transaction t : periodTransactions) {
        txnResponses.add(mapToResponse(t));
    }

    log.info("Statement generated for account {} from {} to {}", accountNumber, from, to);

    return StatementResponse.builder()
            .accountNumber(accountNumber)
            .accountHolderName(account.getUser().getName())
            .fromDate(from)
            .toDate(to)
            .openingBalance(openingBalance)
            .closingBalance(closingBalance)
            .totalCredits(totalCredits)
            .totalDebits(totalDebits)
            .totalTransactions(periodTransactions.size())
            .transactions(txnResponses)
            .build();
}

private TransactionResponse mapToResponse(Transaction transaction) {
    return TransactionResponse.builder()
            .id(transaction.getId())
            .referenceNumber(transaction.getReferenceNumber())
            .amount(transaction.getAmount())
            .transactionType(transaction.getTransactionType())
            .transactionStatus(transaction.getTransactionStatus())
            .description(transaction.getDescription())
            .balanceAfter(transaction.getBalanceAfter())
            .createdAt(transaction.getCreatedAt())
            .build();
}
```

### Imports needed:
```java
import com.hdfc.banking.dto.response.StatementResponse;
import com.hdfc.banking.dto.response.TransactionResponse;
import com.hdfc.banking.entity.Account;
import com.hdfc.banking.entity.Transaction;
import com.hdfc.banking.exception.AccountNotFoundException;
import com.hdfc.banking.exception.UnauthorizedException;
import com.hdfc.banking.repository.AccountRepository;
import com.hdfc.banking.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
```

---

## Step 4 — Create `StatementController.java`

Create file: `controller/StatementController.java`

```java
@RestController
@RequestMapping("/api/statements")
@RequiredArgsConstructor
public class StatementController {

    private final StatementService statementService;

    @GetMapping("/{accountNumber}")
    public ResponseEntity<StatementResponse> getStatement(
            @PathVariable String accountNumber,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal UserDetails userDetails) {

        return ResponseEntity.ok(
            statementService.generateStatement(accountNumber, from, to, userDetails.getUsername())
        );
    }
}
```

### Imports needed:
```java
import com.hdfc.banking.dto.response.StatementResponse;
import com.hdfc.banking.service.StatementService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
```

---

## Step 5 — Test in Thunder Client

### Setup: Register → Login → OTP → Token → Create account → Do transactions

```
POST /api/transactions/deposit?accountNumber=HDFC12345678&amount=50000
POST /api/transactions/withdraw?accountNumber=HDFC12345678&amount=10000
```

### Generate statement:
```
Method: GET
URL:    http://localhost:8080/api/statements/HDFC12345678?from=2026-01-01&to=2026-12-31
Header: Authorization: Bearer <your-token>
```

### Expected response:
```json
{
  "accountNumber": "HDFC12345678",
  "accountHolderName": "Naga",
  "fromDate": "2026-01-01",
  "toDate": "2026-12-31",
  "openingBalance": 0.00,
  "closingBalance": 40000.00,
  "totalCredits": 50000.00,
  "totalDebits": 10000.00,
  "totalTransactions": 2,
  "transactions": [ ... ]
}
```

### Verify the math:
```
openingBalance + totalCredits - totalDebits = closingBalance
0 + 50000 - 10000 = 40000 ✅
```

---

## Step 6 — Git Commit

```bash
git add .
git commit -m "Week3-Day9: Account Statement with correct balance calculation"
git push
```

---

## Key Concepts Learned Today

| Concept | Explanation |
|---|---|
| Opening Balance | Balance at START of statement period |
| Closing Balance | Balance at END of statement period (NOT current balance) |
| Working backwards | `closingBalance = currentBalance - creditsAfter + debitsAfter` |
| `@Query` JPQL | Custom DB queries in Spring Data JPA |
| `@Param` | Maps method args to JPQL `:variable` names |
| `atStartOfDay()` | Converts `LocalDate` → `LocalDateTime` at midnight |
| `@DateTimeFormat` | Parses `?from=2026-01-01` from URL query params |

---

## After This → Next: Week 3, Day 10 — Interest Calculation Service
