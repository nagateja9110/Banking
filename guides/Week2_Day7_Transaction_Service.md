# Week 2, Day 7 — Deposit, Withdraw & Fund Transfer

> **Goal:** Build `TransactionService` with 3 operations: Deposit, Withdraw, Transfer.
> Transfer is the hardest — it must be ACID-compliant (atomic + no race conditions).

---

## What You're Building

```
POST /api/transactions/deposit        → Add money to an account
POST /api/transactions/withdraw       → Remove money from an account
POST /api/transactions/transfer       → Move money between 2 accounts (ACID)
GET  /api/transactions/{accountNumber} → View transaction history
```

---

## Step 1 — Check `TransferRequest.jaVva`

Open `dto/request/TransferRequest.java` — it must have:

```java
@NotBlank
private String fromAccountNumber;

@NotBlank
private String toAccountNumber;

@NotNull
@DecimalMin(value = "0.01", message = "Amount must be greater than 0")
private BigDecimal amount;

private String description;

@NotBlank
private String referenceNumber;    // idempotency key — client generates this UUID
```

---

## Step 2 — Create `TransactionResponse.java`

Create file: `dto/response/TransactionResponse.java`

```java
package com.hdfc.banking.dto.response;

import com.hdfc.banking.enums.TransactionStatus;
import com.hdfc.banking.enums.TransactionType;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class TransactionResponse {
    private Long id;
    private String referenceNumber;
    private BigDecimal amount;
    private TransactionType transactionType;
    private TransactionStatus transactionStatus;
    private String description;
    private BigDecimal balanceAfter;
    private LocalDateTime createdAt;
}
```

---

## Step 3 — Create `TransactionService.java`

Create file: `service/TransactionService.java`

### Class setup:
```java
@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
}
```

---

### Method 1 — `deposit()`

```java
@Transactional
public TransactionResponse deposit(String accountNumber, BigDecimal amount, String email) {
    Account account = accountRepository.findByAccountNumber(accountNumber)
            .orElseThrow(() -> new AccountNotFoundException(accountNumber));

    if (!account.getUser().getEmail().equals(email)) {
        throw new UnauthorizedException("This account does not belong to you");
    }

    if (account.getAccountStatus() != AccountStatus.ACTIVE) {
        throw new AccountFrozenException("Account is not active");
    }

    account.setBalance(account.getBalance().add(amount));
    accountRepository.save(account);

    Transaction transaction = Transaction.builder()
            .toAccount(account)
            .amount(amount)
            .transactionType(TransactionType.DEPOSIT)
            .transactionStatus(TransactionStatus.SUCCESS)
            .balanceAfter(account.getBalance())
            .description("Deposit to " + accountNumber)
            .build();

    transactionRepository.save(transaction);
    log.info("Deposit: {} to account {}", amount, accountNumber);
    return mapToResponse(transaction);
}
```

---

### Method 2 — `withdraw()`

```java
@Transactional
public TransactionResponse withdraw(String accountNumber, BigDecimal amount, String email) {
    Account account = accountRepository.findByAccountNumber(accountNumber)
            .orElseThrow(() -> new AccountNotFoundException(accountNumber));

    if (!account.getUser().getEmail().equals(email)) {
        throw new UnauthorizedException("This account does not belong to you");
    }

    if (account.getAccountStatus() != AccountStatus.ACTIVE) {
        throw new AccountFrozenException("Account is not active");
    }

    if (account.getBalance().compareTo(amount) < 0) {
        throw new InsufficientFundsException("Balance " + account.getBalance() + " is less than " + amount);
    }

    account.setBalance(account.getBalance().subtract(amount));
    accountRepository.save(account);

    Transaction transaction = Transaction.builder()
            .fromAccount(account)
            .amount(amount)
            .transactionType(TransactionType.WITHDRAWAL)
            .transactionStatus(TransactionStatus.SUCCESS)
            .balanceAfter(account.getBalance())
            .description("Withdrawal from " + accountNumber)
            .build();

    transactionRepository.save(transaction);
    return mapToResponse(transaction);
}
```

---

### Method 3 — `transfer()` (most important)

```java
@Transactional
public TransactionResponse transfer(TransferRequest request, String email) {

    // STEP 1: Idempotency check — same reference = return original
    Optional<Transaction> existing = transactionRepository.findByReferenceNumber(request.getReferenceNumber());
    if (existing.isPresent()) {
        log.info("Duplicate transfer request: {}", request.getReferenceNumber());
        return mapToResponse(existing.get());
    }

    // STEP 2: Load both accounts
    Account fromAccount = accountRepository.findByAccountNumber(request.getFromAccountNumber())
            .orElseThrow(() -> new AccountNotFoundException(request.getFromAccountNumber()));

    Account toAccount = accountRepository.findByAccountNumber(request.getToAccountNumber())
            .orElseThrow(() -> new AccountNotFoundException(request.getToAccountNumber()));

    // STEP 3: Validate ownership
    if (!fromAccount.getUser().getEmail().equals(email)) {
        throw new UnauthorizedException("Source account does not belong to you");
    }

    // STEP 4: Validate account status
    if (fromAccount.getAccountStatus() != AccountStatus.ACTIVE) {
        throw new AccountFrozenException("Source account is frozen or closed");
    }
    if (toAccount.getAccountStatus() != AccountStatus.ACTIVE) {
        throw new AccountFrozenException("Destination account is frozen or closed");
    }

    // STEP 5: Check balance
    if (fromAccount.getBalance().compareTo(request.getAmount()) < 0) {
        throw new InsufficientFundsException("Insufficient balance");
    }

    // STEP 6: Debit source, credit destination
    fromAccount.setBalance(fromAccount.getBalance().subtract(request.getAmount()));
    toAccount.setBalance(toAccount.getBalance().add(request.getAmount()));

    accountRepository.save(fromAccount);
    accountRepository.save(toAccount);

    // STEP 7: Save transaction record
    Transaction transaction = Transaction.builder()
            .fromAccount(fromAccount)
            .toAccount(toAccount)
            .amount(request.getAmount())
            .referenceNumber(request.getReferenceNumber())
            .transactionType(TransactionType.TRANSFER)
            .transactionStatus(TransactionStatus.SUCCESS)
            .balanceAfter(fromAccount.getBalance())
            .description(request.getDescription())
            .build();

    transactionRepository.save(transaction);
    log.info("Transfer {} from {} to {}", request.getAmount(),
             request.getFromAccountNumber(), request.getToAccountNumber());
    return mapToResponse(transaction);
}
```

---

### Method 4 — `getHistory()`

```java
public List<TransactionResponse> getHistory(String accountNumber, String email) {
    Account account = accountRepository.findByAccountNumber(accountNumber)
            .orElseThrow(() -> new AccountNotFoundException(accountNumber));

    if (!account.getUser().getEmail().equals(email)) {
        throw new UnauthorizedException("This account does not belong to you");
    }

    List<Transaction> transactions = transactionRepository
            .findByFromAccountOrToAccountOrderByCreatedAtDesc(account, account, Pageable.unpaged())
            .getContent();

    List<TransactionResponse> result = new ArrayList<>();
    for (Transaction t : transactions) {
        result.add(mapToResponse(t));
    }
    return result;
}
```

---

### Helper method:

```java
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

---

### Imports needed:

```java
import com.hdfc.banking.dto.request.TransferRequest;
import com.hdfc.banking.dto.response.TransactionResponse;
import com.hdfc.banking.entity.Account;
import com.hdfc.banking.entity.Transaction;
import com.hdfc.banking.enums.AccountStatus;
import com.hdfc.banking.enums.TransactionStatus;
import com.hdfc.banking.enums.TransactionType;
import com.hdfc.banking.exception.AccountFrozenException;
import com.hdfc.banking.exception.AccountNotFoundException;
import com.hdfc.banking.exception.InsufficientFundsException;
import com.hdfc.banking.exception.UnauthorizedException;
import com.hdfc.banking.repository.AccountRepository;
import com.hdfc.banking.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
```

---

## Step 4 — Add to `TransactionRepository.java`

Open `repository/TransactionRepository.java` and add:

```java
Optional<Transaction> findByReferenceNumber(String referenceNumber);
```

---

## Step 5 — Create `TransactionController.java`

Create file: `controller/TransactionController.java`

```java
@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    @PostMapping("/deposit")
    public ResponseEntity<TransactionResponse> deposit(
            @RequestParam String accountNumber,
            @RequestParam BigDecimal amount,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(transactionService.deposit(accountNumber, amount, userDetails.getUsername()));
    }

    @PostMapping("/withdraw")
    public ResponseEntity<TransactionResponse> withdraw(
            @RequestParam String accountNumber,
            @RequestParam BigDecimal amount,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(transactionService.withdraw(accountNumber, amount, userDetails.getUsername()));
    }

    @PostMapping("/transfer")
    public ResponseEntity<TransactionResponse> transfer(
            @Valid @RequestBody TransferRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(transactionService.transfer(request, userDetails.getUsername()));
    }

    @GetMapping("/{accountNumber}")
    public ResponseEntity<List<TransactionResponse>> history(
            @PathVariable String accountNumber,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(transactionService.getHistory(accountNumber, userDetails.getUsername()));
    }
}
```

---

## Step 6 — Test in Thunder Client

First: **Register → Login → OTP → copy JWT token**

Then (add `Authorization: Bearer <token>` to all):

### Deposit
```
POST http://localhost:8080/api/transactions/deposit?accountNumber=HDFC12345678&amount=50000
```

### Withdraw
```
POST http://localhost:8080/api/transactions/withdraw?accountNumber=HDFC12345678&amount=10000
```

### Transfer
```
POST http://localhost:8080/api/transactions/transfer
Body:
{
  "fromAccountNumber": "HDFC12345678",
  "toAccountNumber":   "HDFC87654321",
  "amount": 5000,
  "description": "Test transfer",
  "referenceNumber": "REF-001"
}
```

### Check balance after transfer
```
GET http://localhost:8080/api/accounts/HDFC12345678/balance
```

---

## Step 7 — Git Commit

```bash
git add .
git commit -m "Week2-Day7: TransactionService - deposit, withdraw, transfer"
git push
```

---

## Key Concepts to Understand

| Concept | Where Used |
|---|---|
| `@Transactional` | Ensures deposit + save are atomic — if save fails, balance reverts |
| Idempotency key (`referenceNumber`) | Same request sent twice = same result, no duplicate transfer |
| `compareTo()` for BigDecimal | Never use `<` or `>` with BigDecimal — always `.compareTo()` |
| `subtract()` / `add()` | BigDecimal is immutable — always reassign the result |
