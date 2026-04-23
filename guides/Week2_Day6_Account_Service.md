# Week 2, Day 6 — Account Service + Controller

> **Before starting:** Run the app and confirm it starts cleanly:
> ```bash
> JAVA_HOME=/opt/homebrew/Cellar/openjdk@21/21.0.10/libexec/openjdk.jdk/Contents/Home ./mvnw spring-boot:run
> ```
> Should show: `Started BankingApplication` in the terminal.

---

## What You're Building Today

```
POST /api/accounts/create        → Create a SAVINGS or CURRENT account
GET  /api/accounts/my            → Get all accounts for logged-in user
GET  /api/accounts/{number}/balance → Get balance of a specific account
```

---

## Step 1 — Create `AccountService.java`

Create file: `service/AccountService.java`

### Fields needed:
```java
private final AccountRepository accountRepository;
private final UserRepository userRepository;
```

### Method 1 — `createAccount()`

Logic:
1. Get the logged-in user's email from SecurityContext
2. Find the user in DB by email
3. Generate a unique 12-digit account number
4. Build and save the Account entity
5. Return AccountResponse DTO

```java
@Transactional
public AccountResponse createAccount(CreateAccountRequest request, String email) {
    User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new RuntimeException("User not found"));

    String accountNumber = generateAccountNumber();

    Account account = Account.builder()
            .user(user)
            .accountNumber(accountNumber)
            .accountType(request.getAccountType())
            .balance(BigDecimal.ZERO)
            .status(AccountStatus.ACTIVE)
            .build();

    accountRepository.save(account);
    return mapToResponse(account);
}
```

### Method 2 — `getMyAccounts()`

```java
public List<AccountResponse> getMyAccounts(String email) {
    User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new RuntimeException("User not found"));
    return accountRepository.findByUser(user)
            .stream()
            .map(this::mapToResponse)
            .toList();
}
```

### Method 3 — `getBalance()`

```java
public BigDecimal getBalance(String accountNumber, String email) {
    Account account = accountRepository.findByAccountNumber(accountNumber)
            .orElseThrow(() -> new AccountNotFoundException(accountNumber));

    if (!account.getUser().getEmail().equals(email)) {
        throw new UnauthorizedException("This account does not belong to you");
    }
    return account.getBalance();
}
```

### Helper methods to add:

```java
private String generateAccountNumber() {
    return "HDFC" + String.format("%08d", new SecureRandom().nextInt(100000000));
}

private AccountResponse mapToResponse(Account account) {
    return AccountResponse.builder()
            .accountNumber(account.getAccountNumber())
            .accountType(account.getAccountType())
            .balance(account.getBalance())
            .status(account.getStatus())
            .build();
}
```

### Imports needed:
```java
import com.hdfc.banking.dto.request.CreateAccountRequest;
import com.hdfc.banking.dto.response.AccountResponse;
import com.hdfc.banking.entity.Account;
import com.hdfc.banking.entity.User;
import com.hdfc.banking.enums.AccountStatus;
import com.hdfc.banking.exception.AccountNotFoundException;
import com.hdfc.banking.exception.UnauthorizedException;
import com.hdfc.banking.repository.AccountRepository;
import com.hdfc.banking.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.List;
```

---

## Step 2 — Create `AccountController.java`

Create file: `controller/AccountController.java`

```java
@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @PostMapping("/create")
    public ResponseEntity<AccountResponse> create(
            @Valid @RequestBody CreateAccountRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(accountService.createAccount(request, userDetails.getUsername()));
    }

    @GetMapping("/my")
    public ResponseEntity<List<AccountResponse>> myAccounts(
            @AuthenticationPrincipal UserDetails userDetails) {

        return ResponseEntity.ok(accountService.getMyAccounts(userDetails.getUsername()));
    }

    @GetMapping("/{accountNumber}/balance")
    public ResponseEntity<BigDecimal> balance(
            @PathVariable String accountNumber,
            @AuthenticationPrincipal UserDetails userDetails) {

        return ResponseEntity.ok(accountService.getBalance(accountNumber, userDetails.getUsername()));
    }
}
```

### Imports needed:
```java
import com.hdfc.banking.dto.request.CreateAccountRequest;
import com.hdfc.banking.dto.response.AccountResponse;
import com.hdfc.banking.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.List;
```

---

## Step 3 — Check `CreateAccountRequest.java`

Open `dto/request/CreateAccountRequest.java` — it must have:

```java
@NotNull
private AccountType accountType;   // SAVINGS or CURRENT
```

---

## Step 4 — Check `AccountResponse.java`

Open `dto/response/AccountResponse.java` — it must have:

```java
private String accountNumber;
private AccountType accountType;
private BigDecimal balance;
private AccountStatus status;
```

And `@Data`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor` Lombok annotations.

---

## Step 5 — Test in Postman

### First: Register + Login + Get JWT
(Do the 4-step auth flow from Week2_Fix_Guide.md)

### Then test account endpoints (use JWT in Authorization header):

**Header for all requests below:**
```
Authorization: Bearer <your-jwt-token>
```

**Create account:**
```
POST http://localhost:8080/api/accounts/create
{ "accountType": "SAVINGS" }
→ 201 Created: { "accountNumber": "HDFC12345678", "balance": 0.00, "status": "ACTIVE" }
```

**Get all my accounts:**
```
GET http://localhost:8080/api/accounts/my
→ 200 OK: [ { "accountNumber": "HDFC12345678", ... } ]
```

**Get balance:**
```
GET http://localhost:8080/api/accounts/HDFC12345678/balance
→ 200 OK: 0.00
```

---

## Step 6 — Verify in H2 Console

Open: http://localhost:8080/h2-console
- JDBC URL: `jdbc:h2:mem:hdfcdb`
- Username: `sa`, Password: (empty)

```sql
SELECT * FROM users;
SELECT * FROM accounts;
```

---

## Step 7 — Git Commit

```bash
git add .
git commit -m "Week2-Day6: AccountService + AccountController"
git push
```

---

## Common Mistakes to Watch

| Mistake | Fix |
|---|---|
| Using `@RequestBody` without `@Valid` | Always add `@Valid` on DTOs |
| `@AuthenticationPrincipal` returns null | Make sure JWT filter is setting SecurityContext correctly |
| `import org.springframework.transaction.annotation.Transactional` vs `jakarta.transaction.Transactional` | Use **Spring's** version for Spring-managed rollback |
| Using `==` to compare BigDecimal | Always use `.compareTo()` |

---

## After This Guide → Next: Deposit + Transfer (Week 2, Day 7)
