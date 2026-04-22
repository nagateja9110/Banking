# Week 1 Day 3 — Repositories + DTOs
## Spring Data JPA Repositories + Request/Response Objects

---

## What You Will Build Today

```
repository/
  ├── UserRepository.java
  ├── AccountRepository.java
  ├── TransactionRepository.java
  ├── OtpTokenRepository.java
  ├── FraudAlertRepository.java
  └── AuditLogRepository.java

dto/
  ├── request/
  │   ├── RegisterRequest.java
  │   ├── LoginRequest.java
  │   ├── OtpVerifyRequest.java
  │   ├── CreateAccountRequest.java
  │   ├── TransferRequest.java
  │   └── DepositWithdrawRequest.java
  └── response/
      ├── LoginResponse.java
      ├── AccountResponse.java
      └── TransactionResponse.java
```

**Time: ~3 hours**

---

## Hour 1 — READ FIRST (No Coding Yet)

### WHY Repositories? (Don't we already have entities?)

Entities are your **table structure** — they define what the table looks like.
Repositories are your **query engine** — they let you talk to the table.

```
Entity     → Defines: what columns exist, what types, what constraints
Repository → Defines: how to query: find by email, find by account number, etc.
```

Think of it like this:
```
Entity   = blueprint of a bank vault
Repository = the key and lock system to get into it
```

Without a repository, you'd write raw SQL:
```java
// Without JPA Repository — manual JDBC (ugly):
String sql = "SELECT * FROM users WHERE email = ?";
PreparedStatement stmt = connection.prepareStatement(sql);
stmt.setString(1, email);
ResultSet rs = stmt.executeQuery();
User user = new User();
user.setId(rs.getLong("id"));
user.setEmail(rs.getString("email"));
// ... 20 more lines
```

With JPA Repository — just declare the method:
```java
// With Spring Data JPA (clean):
Optional<User> findByEmail(String email);
// Spring generates ALL that SQL automatically!
```

---

### WHY JpaRepository<Entity, ID>?

```java
public interface UserRepository extends JpaRepository<User, Long>
//                                           ↑     ↑
//                                        Entity  Primary Key type
```

`JpaRepository` gives you **18 free methods** without writing any code:

| Method | What it does |
|---|---|
| `save(entity)` | INSERT or UPDATE |
| `findById(id)` | SELECT WHERE id = ? |
| `findAll()` | SELECT * |
| `deleteById(id)` | DELETE WHERE id = ? |
| `existsById(id)` | SELECT EXISTS |
| `count()` | SELECT COUNT(*) |
| `findAll(Pageable)` | Paginated SELECT |

Everything else you add yourself as method signatures.

---

### WHY custom query methods? (findByEmail, findByAccountNumber)

Spring Data JPA reads your method NAME and generates SQL automatically.

```java
// Method name:         finds by:
findByEmail            → WHERE email = ?
findByAccountNumber    → WHERE account_number = ?
findByUserAndAccountType  → WHERE user_id = ? AND account_type = ?
findByStatusAndCreatedAtBefore  → WHERE status = ? AND created_at < ?
```

**Rule:** Method name = `findBy` + field name (capitalized) + optional conditions

```java
// These ALL generate SQL automatically — no @Query needed:
Optional<User> findByEmail(String email);
List<Account> findByUser(User user);
List<Transaction> findByFromAccountOrderByCreatedAtDesc(Account account);
```

---

### WHY DTOs? (We already have Entities — why make more classes?)

**Never expose entities directly to API clients.** Here's why:

```
Entity User has:
  - id, name, email, password (BCrypt hash), phone, role, isVerified, createdAt, updatedAt, accounts

If you return User entity directly from /login:
→ Client sees password hash (security breach!)
→ Client sees all accounts list (performance — lazy loading)
→ Client sees internal fields (isVerified, updatedAt)
```

```
LoginResponse DTO returns only:
  - token (JWT)
  - name
  - email
  - role

Clean. Secure. Minimal.
```

**The Rule:**
```
Request DTO  = what client SENDS  → validate with @Valid, never trust raw input
Response DTO = what client GETS   → only safe, relevant fields
Entity       = internal only      → never goes out of service layer
```

---

### WHY @Valid and validation annotations on DTOs?

```java
public class RegisterRequest {
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;
}
```

Without `@Valid`:
```
Client sends: { "email": "", "password": "123" }
Your code:    BCrypt.encode("123") → saves to DB
Result:       Garbage data in database
```

With `@Valid` on the controller:
```
Client sends: { "email": "", "password": "123" }
Spring:       Runs validation BEFORE your code runs
Result:       400 Bad Request → { "email": "Email is required", "password": "min 8 chars" }
Your code:    Never even runs → database stays clean
```

---

## Hour 2 — CODE Repositories

### Create folder: `repository/`

Right-click `com.hdfc.banking` → New Folder → `repository`

---

### UserRepository.java

```java
package com.hdfc.banking.repository;

import com.hdfc.banking.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

/**
 * @Repository — marks this as a Spring-managed bean.
 * Spring creates the implementation at startup automatically.
 * You never write "class UserRepositoryImpl" — Spring does it.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    // Spring reads "findByEmail" → generates: SELECT * FROM users WHERE email = ?
    Optional<User> findByEmail(String email);

    // Used in registration to check if email is already taken
    boolean existsByEmail(String email);
}
```

---

### AccountRepository.java

```java
package com.hdfc.banking.repository;

import com.hdfc.banking.entity.Account;
import com.hdfc.banking.entity.User;
import com.hdfc.banking.enums.AccountStatus;
import com.hdfc.banking.enums.AccountType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {

    // Find account by its unique account number (e.g. "HDFC001234567890")
    Optional<Account> findByAccountNumber(String accountNumber);

    // Get all accounts belonging to one user
    List<Account> findByUser(User user);

    // Business rule check: does user already have a SAVINGS account?
    // Spring reads: findByUser AND AccountType → WHERE user_id = ? AND account_type = ?
    boolean existsByUserAndAccountType(User user, AccountType accountType);

    // Find accounts by status — used by admin to see all frozen accounts
    List<Account> findByAccountStatus(AccountStatus status);

    /**
     * WHY @Query here?
     * We need to lock the row while doing a transfer to prevent race conditions.
     * "FOR UPDATE" is a SQL lock that blocks other transactions from reading
     * this row until the current transaction commits.
     *
     * Without this: two concurrent transfers from same account → negative balance!
     * With this: second transfer waits until first completes.
     *
     * This is PESSIMISTIC LOCKING — used in banking for correctness over speed.
     */
    @Query("SELECT a FROM Account a WHERE a.accountNumber = :accountNumber")
    Optional<Account> findByAccountNumberWithLock(@Param("accountNumber") String accountNumber);
}
```

---

### TransactionRepository.java

```java
package com.hdfc.banking.repository;

import com.hdfc.banking.entity.Account;
import com.hdfc.banking.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    /**
     * WHY Page<Transaction> instead of List<Transaction>?
     * A user could have 10,000 transactions.
     * Returning all 10,000 at once → crashes mobile app, wastes memory.
     *
     * Page = returns a CHUNK (e.g. 10 at a time) + metadata:
     * { content: [...10 items], totalPages: 1000, currentPage: 0, totalElements: 10000 }
     *
     * Client requests: GET /transactions?page=0&size=10
     * Client requests: GET /transactions?page=1&size=10  (next page)
     */
    Page<Transaction> findByFromAccountOrToAccountOrderByCreatedAtDesc(
        Account fromAccount, Account toAccount, Pageable pageable
    );
}
```

---

### OtpTokenRepository.java

```java
package com.hdfc.banking.repository;

import com.hdfc.banking.entity.OtpToken;
import com.hdfc.banking.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface OtpTokenRepository extends JpaRepository<OtpToken, Long> {

    // Find the latest unused OTP for a user
    Optional<OtpToken> findTopByUserAndIsUsedFalseAndExpiresAtAfterOrderByCreatedAtDesc(
        User user, LocalDateTime now
    );

    /**
     * WHY @Modifying + @Transactional here?
     * @Query with DELETE is a "modifying" operation (not a SELECT).
     * @Modifying tells Spring: "this changes data, handle it as an update"
     * @Transactional ensures the delete is atomic — all or nothing.
     *
     * This is called by BankingScheduler every hour to clean expired OTPs.
     * Without cleanup: otp_tokens table grows forever → database gets slow.
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM OtpToken o WHERE o.expiresAt < :now")
    void deleteExpiredOtps(@Param("now") LocalDateTime now);
}
```

---

### FraudAlertRepository.java

```java
package com.hdfc.banking.repository;

import com.hdfc.banking.entity.FraudAlert;
import com.hdfc.banking.enums.FraudAlertStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FraudAlertRepository extends JpaRepository<FraudAlert, Long> {

    // Admin dashboard: show all open (unreviewed) fraud alerts
    List<FraudAlert> findByStatusOrderByFlaggedAtDesc(FraudAlertStatus status);

    // Count open alerts — shown in admin dashboard summary
    long countByStatus(FraudAlertStatus status);
}
```

---

### AuditLogRepository.java

```java
package com.hdfc.banking.repository;

import com.hdfc.banking.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    // Admin: view all actions by a specific user (paginated)
    Page<AuditLog> findByChangedByOrderByTimestampDesc(String changedBy, Pageable pageable);

    // Admin: view all actions of a specific type (e.g. all "transfer" events)
    Page<AuditLog> findByActionOrderByTimestampDesc(String action, Pageable pageable);
}
```

---

## Hour 3 — CODE DTOs

### Create two sub-folders inside `dto/`:

```
Right-click dto/ → New Folder → request
Right-click dto/ → New Folder → response
```

---

### RegisterRequest.java

```java
package com.hdfc.banking.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * What client sends to POST /api/auth/register
 *
 * WHY @NotBlank and not @NotNull?
 * @NotNull only checks null. @NotBlank also rejects empty string "" and "   " (spaces).
 * Client could send: { "email": "   " } → @NotNull passes! @NotBlank catches it.
 *
 * WHY @Email?
 * Without it: "abc123" is accepted as email → registration succeeds → OTP email fails.
 * With it: format validation before any service code runs.
 */
@Data
public class RegisterRequest {

    @NotBlank(message = "Name is required")
    private String name;

    @NotBlank(message = "Email is required")
    @Email(message = "Please enter a valid email address")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;

    // Phone stored encrypted in DB (AES-256) — plain text comes in, encrypted stored
    private String phone;
}
```

---

### LoginRequest.java

```java
package com.hdfc.banking.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * What client sends to POST /api/auth/login
 * Step 1 of 2: validates credentials → sends OTP if correct
 */
@Data
public class LoginRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Password is required")
    private String password;
}
```

---

### OtpVerifyRequest.java

```java
package com.hdfc.banking.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * What client sends to POST /api/auth/verify-otp
 * Step 2 of 2: submits OTP → gets JWT token back
 */
@Data
public class OtpVerifyRequest {

    @NotBlank(message = "Email is required")
    private String email;

    @NotBlank(message = "OTP is required")
    @Size(min = 6, max = 6, message = "OTP must be exactly 6 digits")
    private String otpCode;
}
```

---

### CreateAccountRequest.java

```java
package com.hdfc.banking.dto.request;

import com.hdfc.banking.enums.AccountType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * What client sends to POST /api/accounts/create
 *
 * WHY @NotNull for enum? (not @NotBlank)
 * @NotBlank is for Strings only.
 * AccountType is an enum (not a String) → use @NotNull.
 *
 * If client sends: { "accountType": "INVALID_TYPE" }
 * Jackson deserialization fails with 400 before validation even runs.
 */
@Data
public class CreateAccountRequest {

    @NotNull(message = "Account type is required (SAVINGS, CURRENT, or FIXED_DEPOSIT)")
    private AccountType accountType;
}
```

---

### TransferRequest.java

```java
package com.hdfc.banking.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;

/**
 * What client sends to POST /api/transactions/transfer
 *
 * WHY BigDecimal for amount and not double?
 * double: 0.1 + 0.2 = 0.30000000000000004 (floating point error!)
 * BigDecimal: 0.1 + 0.2 = 0.3 (exact!)
 *
 * In banking, even ₹0.0001 difference is a compliance issue.
 * Always BigDecimal for money.
 *
 * WHY @DecimalMin("0.01")?
 * Prevents: transfer of ₹0 (no-op attack)
 * Prevents: negative transfer (attempt to reverse-hack)
 */
@Data
public class TransferRequest {

    @NotBlank(message = "Source account number is required")
    private String fromAccountNumber;

    @NotBlank(message = "Destination account number is required")
    private String toAccountNumber;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Transfer amount must be at least ₹0.01")
    private BigDecimal amount;

    private String description;
    // Optional note: "Rent payment", "Birthday gift", etc.
}
```

---

### DepositWithdrawRequest.java

```java
package com.hdfc.banking.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class DepositWithdrawRequest {

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be at least ₹0.01")
    private BigDecimal amount;

    private String description;
}
```

---

### LoginResponse.java

```java
package com.hdfc.banking.dto.response;

import com.hdfc.banking.enums.Role;
import lombok.Builder;
import lombok.Data;

/**
 * What client receives after successful OTP verification.
 *
 * WHY only these 4 fields?
 * Client needs:
 *   token → to include in all future requests (Authorization: Bearer <token>)
 *   name  → to show "Welcome, Naga!" in UI
 *   email → to show logged-in user's identity
 *   role  → to show/hide admin sections in UI
 *
 * Client does NOT need: password, createdAt, isVerified, phone (private data)
 */
@Data
@Builder
public class LoginResponse {
    private String token;
    private String name;
    private String email;
    private Role role;
}
```

---

### AccountResponse.java

```java
package com.hdfc.banking.dto.response;

import com.hdfc.banking.enums.AccountStatus;
import com.hdfc.banking.enums.AccountType;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * What client receives when they view their account.
 *
 * WHY not return the Account entity directly?
 * Account entity has: user (full User object with password hash!)
 * Returning entity would expose: BCrypt password hash → security breach
 *
 * AccountResponse has: only what the client needs to display
 */
@Data
@Builder
public class AccountResponse {
    private Long id;
    private String accountNumber;
    private AccountType accountType;
    private AccountStatus accountStatus;
    private BigDecimal balance;
    private BigDecimal interestRate;
    private LocalDateTime createdAt;
    // userEmail — so client knows which user this belongs to, without full User object
    private String userEmail;
}
```

---

### TransactionResponse.java

```java
package com.hdfc.banking.dto.response;

import com.hdfc.banking.enums.TransactionStatus;
import com.hdfc.banking.enums.TransactionType;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * What client receives when they view transaction history.
 * Shows enough to display a bank statement line item.
 */
@Data
@Builder
public class TransactionResponse {
    private Long id;
    private String referenceNumber;
    private TransactionType transactionType;
    private TransactionStatus transactionStatus;
    private BigDecimal amount;
    private BigDecimal balanceAfter;
    private String description;
    private String fromAccountNumber;
    private String toAccountNumber;
    private LocalDateTime createdAt;
}
```

---

## Also Create: @EnableJpaAuditing in main class

Open `BankingApplication.java` and add `@EnableJpaAuditing`:

```java
package com.hdfc.banking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * WHY @EnableJpaAuditing?
 * Your entities use @CreatedDate and @LastModifiedDate.
 * Without this annotation, those fields are NEVER populated — always null.
 * With this: Spring automatically sets createdAt when saved, updatedAt when updated.
 */
@SpringBootApplication
@EnableJpaAuditing
public class BankingApplication {
    public static void main(String[] args) {
        SpringApplication.run(BankingApplication.class, args);
    }
}
```

---

## Verify — Restart and Check

After writing all files:

```bash
Ctrl+C  → ./mvnw spring-boot:run
```

If app starts with `BUILD SUCCESS` and tables appear → Day 3 complete ✅

**No new endpoints to test today** — repositories and DTOs are internal.
They become visible in Week 2 when Services + Controllers use them.

---

## Checkpoint Questions (Answer Before Day 4)

1. What is `JpaRepository<User, Long>`? What do `User` and `Long` represent?
2. Write a method signature to find all accounts where status = FROZEN. Don't use @Query.
3. Why does `TransactionRepository` use `Page<Transaction>` instead of `List<Transaction>`?
4. What is the difference between `@NotNull` and `@NotBlank`? Give an example where @NotNull passes but @NotBlank fails.
5. Why is `TransferRequest.amount` a `BigDecimal` and not `double`?
6. What happens to `@CreatedDate` fields if you forget `@EnableJpaAuditing`?
7. Why do we have `AccountResponse` DTO when we already have `Account` entity?

---

## Day 4 Preview — JWT Security

Tomorrow you build the real security:
- `JwtTokenProvider` — generates + validates JWT tokens
- `JwtAuthenticationFilter` — intercepts every request and validates token
- Full `SecurityConfig` — replaces today's permit-all config with real role-based rules
- `CustomUserDetailsService` — loads user from DB during authentication

This is the most important and complex day of Week 1.
