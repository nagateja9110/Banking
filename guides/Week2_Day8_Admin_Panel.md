# Week 2, Day 8 — Admin Panel

> **Goal:** Build an admin-only panel where admins can:
> - View all users & accounts
> - Freeze / unfreeze accounts
> - View any user's transaction history
>
> Security rule: Only users with `ROLE_ADMIN` can access these endpoints.

---

## What You're Building

```
GET  /api/admin/users                     → Get all registered users
GET  /api/admin/accounts                  → Get all accounts in the system
PUT  /api/admin/accounts/{number}/freeze  → Freeze an account
PUT  /api/admin/accounts/{number}/unfreeze → Unfreeze an account
GET  /api/admin/transactions/{number}     → View any account's transactions
```

---

## Step 1 — Enable Method-Level Security

Open `SecurityConfig.java` and add `@EnableMethodSecurity` annotation:

```java
// ❌ Current
@Configuration
@EnableWebSecurity
public class SecurityConfig {

// ✅ Add @EnableMethodSecurity
@Configuration
@EnableWebSecurity
@EnableMethodSecurity           // ← add this
public class SecurityConfig {
```

Also add this import:
```java
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
```

**Why?** — `@EnableMethodSecurity` allows you to use `@PreAuthorize("hasRole('ADMIN')")` on individual methods to restrict access by role.

---

## Step 2 — Create `AdminService.java`

Create file: `service/AdminService.java`

### Class setup:
```java
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
}
```

---

### Method 1 — `getAllUsers()`

```java
public List<UserSummaryResponse> getAllUsers() {
    List<User> users = userRepository.findAll();

    List<UserSummaryResponse> result = new ArrayList<>();
    for (User user : users) {
        result.add(UserSummaryResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole())
                .locked(user.isLocked())
                .build());
    }
    return result;
}
```

---

### Method 2 — `getAllAccounts()`

```java
public List<AccountResponse> getAllAccounts() {
    List<Account> accounts = accountRepository.findAll();

    List<AccountResponse> result = new ArrayList<>();
    for (Account account : accounts) {
        result.add(AccountResponse.builder()
                .id(account.getId())
                .accountNumber(account.getAccountNumber())
                .accountType(account.getAccountType())
                .accountStatus(account.getAccountStatus())
                .balance(account.getBalance())
                .build());
    }
    return result;
}
```

---

### Method 3 — `freezeAccount()`

```java
@Transactional
public String freezeAccount(String accountNumber) {
    Account account = accountRepository.findByAccountNumber(accountNumber)
            .orElseThrow(() -> new AccountNotFoundException(accountNumber));

    if (account.getAccountStatus() == AccountStatus.FROZEN) {
        return "Account is already frozen";
    }

    account.setAccountStatus(AccountStatus.FROZEN);
    accountRepository.save(account);
    log.info("Admin froze account: {}", accountNumber);
    return "Account frozen successfully";
}
```

---

### Method 4 — `unfreezeAccount()`

```java
@Transactional
public String unfreezeAccount(String accountNumber) {
    Account account = accountRepository.findByAccountNumber(accountNumber)
            .orElseThrow(() -> new AccountNotFoundException(accountNumber));

    if (account.getAccountStatus() == AccountStatus.ACTIVE) {
        return "Account is already active";
    }

    account.setAccountStatus(AccountStatus.ACTIVE);
    accountRepository.save(account);
    log.info("Admin unfroze account: {}", accountNumber);
    return "Account unfrozen successfully";
}
```

---

### Method 5 — `getAccountTransactions()`

```java
public List<TransactionResponse> getAccountTransactions(String accountNumber) {
    Account account = accountRepository.findByAccountNumber(accountNumber)
            .orElseThrow(() -> new AccountNotFoundException(accountNumber));

    List<Transaction> transactions = transactionRepository
            .findByFromAccountOrToAccountOrderByCreatedAtDesc(account, account, Pageable.unpaged())
            .getContent();

    List<TransactionResponse> result = new ArrayList<>();
    for (Transaction t : transactions) {
        result.add(mapToResponse(t));
    }
    return result;
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
import com.hdfc.banking.dto.response.AccountResponse;
import com.hdfc.banking.dto.response.TransactionResponse;
import com.hdfc.banking.dto.response.UserSummaryResponse;
import com.hdfc.banking.entity.Account;
import com.hdfc.banking.entity.Transaction;
import com.hdfc.banking.entity.User;
import com.hdfc.banking.enums.AccountStatus;
import com.hdfc.banking.exception.AccountNotFoundException;
import com.hdfc.banking.repository.AccountRepository;
import com.hdfc.banking.repository.TransactionRepository;
import com.hdfc.banking.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.List;
```

---

## Step 3 — Create `UserSummaryResponse.java`

Create file: `dto/response/UserSummaryResponse.java`

```java
package com.hdfc.banking.dto.response;

import com.hdfc.banking.enums.Role;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserSummaryResponse {
    private Long id;
    private String name;
    private String email;
    private Role role;
    private boolean locked;
}
```

---

## Step 4 — Create `AdminController.java`

Create file: `controller/AdminController.java`

```java
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

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
}
```

### Imports needed:
```java
import com.hdfc.banking.dto.response.AccountResponse;
import com.hdfc.banking.dto.response.TransactionResponse;
import com.hdfc.banking.dto.response.UserSummaryResponse;
import com.hdfc.banking.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
```

---

## Step 5 — Register an Admin User (in H2 Console)

Since registration always creates a `CUSTOMER`, you need to manually promote a user to `ADMIN` in H2 console:

1. Open: http://localhost:8080/h2-console
2. Run:
```sql
-- First register a user via /api/auth/register, then promote them:
UPDATE users SET role = 'ADMIN' WHERE email = 'admin@hdfc.com';
```

---

## Step 6 — Test in Thunder Client

### Login as Admin and get JWT

```
POST /api/auth/register  → { "email": "admin@hdfc.com", ... }
→ Manually update role in H2 console

POST /api/auth/login     → get OTP
POST /api/auth/verify-otp → get JWT (this is your ADMIN token)
```

### Test admin endpoints (use admin JWT):

```
GET  /api/admin/users
GET  /api/admin/accounts
PUT  /api/admin/accounts/HDFC12345678/freeze
PUT  /api/admin/accounts/HDFC12345678/unfreeze
GET  /api/admin/transactions/HDFC12345678
```

### Try accessing admin endpoint with CUSTOMER token:
```
GET /api/admin/users  (with customer JWT)
→ 403 Forbidden  ← this proves role-based security works!
```

---

## Step 7 — Git Commit

```bash
git add .
git commit -m "Week2-Day8: AdminService + AdminController with role-based security"
git push
```

---

## Key Concepts Learned Today

| Concept | Explanation |
|---|---|
| `@PreAuthorize("hasRole('ADMIN')")` | Blocks non-admin access at method level |
| `@EnableMethodSecurity` | Must be added to SecurityConfig to enable `@PreAuthorize` |
| Role-based access control (RBAC) | Different users get different permissions |
| H2 console SQL update | How to manually set admin role for testing |

---

## After This Guide → Next: Week 3 — Statement PDF Generation + Email Notifications
