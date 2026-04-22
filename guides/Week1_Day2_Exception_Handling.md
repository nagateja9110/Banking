# Week 1 Day 2 — Exception Handling
## Custom Exceptions + Global Error Handler

---

## What You Will Build Today

```
exception/
  ├── AccountNotFoundException.java
  ├── InsufficientFundsException.java
  ├── AccountFrozenException.java
  ├── UnauthorizedException.java
  ├── OtpExpiredException.java
  ├── DuplicateAccountException.java
  └── GlobalExceptionHandler.java

dto/response/
  └── ErrorResponse.java
```

**Time: ~3 hours**

---

## Hour 1 — READ FIRST (No Coding Yet)

### WHY do we need custom exceptions?

Look at this code:

```java
// BAD — throws generic exception
public Account getAccount(Long id) {
    return accountRepository.findById(id)
        .orElseThrow(() -> new RuntimeException("Account not found"));
}
```

What does the client (Postman, mobile app) get back?
```json
{
  "timestamp": "...",
  "status": 500,
  "error": "Internal Server Error",
  "message": "Account not found"
}
```

**Two problems:**
1. Status is `500` (server error) — but this is NOT a server error, it's a CLIENT error (they sent wrong ID)
2. No way to handle this differently from a real server crash

```java
// GOOD — throws specific exception
public Account getAccount(Long id) {
    return accountRepository.findById(id)
        .orElseThrow(() -> new AccountNotFoundException(id));
}
```

What the client gets:
```json
{
  "status": 404,
  "error": "Account Not Found",
  "message": "Account with ID 42 does not exist",
  "timestamp": "2024-01-15T10:30:00"
}
```

**Result:** Client knows it's their fault (404), knows exactly what's wrong, can fix their request.

---

### WHY extend RuntimeException and NOT Exception?

```
Exception (checked)     → Caller MUST handle it with try-catch or throws
RuntimeException (unchecked) → Caller doesn't need to declare it
```

In a banking API, exceptions happen deep inside services.
If we use checked Exception, every layer (controller → service → repository) must declare `throws AccountNotFoundException` — messy.

With `RuntimeException`, the exception bubbles up automatically to `GlobalExceptionHandler`.

```java
// Every method would need this — BAD:
public void transfer(Long from, Long to) throws AccountNotFoundException, InsufficientFundsException { ... }

// With RuntimeException — CLEAN:
public void transfer(Long from, Long to) { ... }  // exception bubbles up automatically
```

---

### WHY @RestControllerAdvice?

Without it: Every controller needs its own try-catch → massive code duplication.

```java
// BAD: Duplicated in every controller
@GetMapping("/{id}")
public Account getAccount(@PathVariable Long id) {
    try {
        return accountService.getAccount(id);
    } catch (AccountNotFoundException e) {
        return ResponseEntity.status(404).body(e.getMessage());
    } catch (AccountFrozenException e) {
        return ResponseEntity.status(403).body(e.getMessage());
    }
}
```

With `@RestControllerAdvice`:
```java
// ONE place handles ALL controllers — CLEAN:
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleAccountNotFound(AccountNotFoundException ex) {
        // called automatically whenever this exception is thrown ANYWHERE
    }
}
```

`@RestControllerAdvice` = `@ControllerAdvice` + `@ResponseBody` (auto-converts response to JSON)

---

### HTTP Status Codes You'll Use

| Exception | Status Code | Why This Code |
|---|---|---|
| `AccountNotFoundException` | 404 Not Found | Resource doesn't exist |
| `InsufficientFundsException` | 400 Bad Request | Client's request can't be fulfilled |
| `AccountFrozenException` | 403 Forbidden | Account exists but action not allowed |
| `UnauthorizedException` | 401 Unauthorized | Not authenticated / wrong token |
| `OtpExpiredException` | 400 Bad Request | Client sent stale OTP |
| `DuplicateAccountException` | 409 Conflict | Resource already exists |
| `MethodArgumentNotValidException` | 400 Bad Request | `@Valid` check failed on DTO |
| `Exception` (catch-all) | 500 Internal Server Error | Unexpected server error |

---

## Hour 2 — CODE (Type Every Line — No Copy Paste)

### Step 1: Create ErrorResponse DTO

Create file: `dto/response/ErrorResponse.java`

```
Right-click dto folder → New → Package → name it "response"
Right-click response folder → New → Java Class → ErrorResponse
```

```java
package com.hdfc.banking.dto.response;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * Standard error shape returned by ALL our API errors.
 *
 * WHY consistent structure?
 * Mobile apps and frontends parse this JSON.
 * If every error returns different fields, clients break.
 * With this, clients always know: check "status" and "message".
 *
 * WHY LocalDateTime and not String?
 * Easier to parse in any timezone.
 * Jackson auto-serializes it to ISO format: "2024-01-15T10:30:00"
 */
@Data
@Builder
public class ErrorResponse {
    private int status;
    private String error;
    private String message;
    private LocalDateTime timestamp;
}
```

---

### Step 2: Create Custom Exceptions

Create file: `exception/AccountNotFoundException.java`

```java
package com.hdfc.banking.exception;

/**
 * Thrown when: accountRepository.findById(id) returns empty Optional
 * Caught by: GlobalExceptionHandler.handleAccountNotFound()
 * HTTP Status: 404 Not Found
 *
 * WHY two constructors?
 * Sometimes we have the ID: "Account 42 not found"
 * Sometimes we have the account number: "Account HDFC001 not found"
 */
public class AccountNotFoundException extends RuntimeException {

    public AccountNotFoundException(Long id) {
        super("Account with ID " + id + " not found");
    }

    public AccountNotFoundException(String accountNumber) {
        super("Account with number " + accountNumber + " not found");
    }
}
```

---

Create file: `exception/InsufficientFundsException.java`

```java
package com.hdfc.banking.exception;

import java.math.BigDecimal;

/**
 * Thrown when: account balance < transfer amount
 * Caught by: GlobalExceptionHandler.handleInsufficientFunds()
 * HTTP Status: 400 Bad Request
 *
 * WHY BigDecimal in the message?
 * Client needs to know exact balance and requested amount.
 * BigDecimal ensures no rounding in the message — shows exact numbers.
 * Example: "Insufficient funds. Balance: ₹5000.0000, Requested: ₹8000.0000"
 */
public class InsufficientFundsException extends RuntimeException {

    public InsufficientFundsException(BigDecimal balance, BigDecimal requested) {
        super("Insufficient funds. Available: ₹" + balance + ", Requested: ₹" + requested);
    }
}
```

---

Create file: `exception/AccountFrozenException.java`

```java
package com.hdfc.banking.exception;

/**
 * Thrown when: user tries to transfer/withdraw from a FROZEN account
 * Caught by: GlobalExceptionHandler.handleAccountFrozen()
 * HTTP Status: 403 Forbidden
 *
 * WHY 403 and not 400?
 * 400 = bad request format (client sent wrong data)
 * 403 = request is valid but NOT ALLOWED (account exists, format is correct, but action is denied)
 * A frozen account is a permission issue, not a data format issue.
 */
public class AccountFrozenException extends RuntimeException {

    public AccountFrozenException(String accountNumber) {
        super("Account " + accountNumber + " is frozen. Contact support to unfreeze.");
    }
}
```

---

Create file: `exception/UnauthorizedException.java`

```java
package com.hdfc.banking.exception;

/**
 * Thrown when: JWT token is missing, expired, or user tries to access another user's account
 * Caught by: GlobalExceptionHandler.handleUnauthorized()
 * HTTP Status: 401 Unauthorized
 *
 * WHY 401 and not 403?
 * 401 = not authenticated (we don't know who you are)
 * 403 = authenticated but not authorized (we know who you are, but you can't do this)
 * No/invalid token → 401. Valid token but wrong role → 403.
 */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
```

---

Create file: `exception/OtpExpiredException.java`

```java
package com.hdfc.banking.exception;

/**
 * Thrown when: otpToken.getExpiresAt().isBefore(LocalDateTime.now())
 * Caught by: GlobalExceptionHandler.handleOtpExpired()
 * HTTP Status: 400 Bad Request
 *
 * OTP expires after 5 minutes.
 * Client must request a new OTP instead of retrying with the old one.
 */
public class OtpExpiredException extends RuntimeException {

    public OtpExpiredException() {
        super("OTP has expired. Please request a new OTP.");
    }
}
```

---

Create file: `exception/DuplicateAccountException.java`

```java
package com.hdfc.banking.exception;

/**
 * Thrown when: user tries to create a second SAVINGS account
 * (business rule: one account per type per user)
 * HTTP Status: 409 Conflict
 *
 * WHY 409 Conflict?
 * The resource already exists. The request conflicts with current state.
 * Different from 400 (bad format) — the format is fine, but the state conflicts.
 */
public class DuplicateAccountException extends RuntimeException {

    public DuplicateAccountException(String accountType) {
        super("User already has a " + accountType + " account. Only one account per type allowed.");
    }
}
```

---

### Step 3: Create GlobalExceptionHandler

Create file: `exception/GlobalExceptionHandler.java`

```java
package com.hdfc.banking.exception;

import com.hdfc.banking.dto.response.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * @RestControllerAdvice = Central error handler for ALL controllers.
 *
 * HOW it works:
 * 1. Service throws AccountNotFoundException
 * 2. Spring catches it (before returning to client)
 * 3. Finds the matching @ExceptionHandler method here
 * 4. Calls that method → returns structured JSON instead of stack trace
 *
 * WHY put all handlers here instead of each controller?
 * Single Responsibility Principle: controllers handle requests, this class handles errors.
 * DRY: Don't Repeat Yourself — one place to change error format for all endpoints.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ── Helper method to avoid repeating ResponseEntity.status(...).body(...) ──
    private ResponseEntity<ErrorResponse> buildResponse(HttpStatus status, String error, String message) {
        return ResponseEntity.status(status).body(
            ErrorResponse.builder()
                .status(status.value())
                .error(error)
                .message(message)
                .timestamp(LocalDateTime.now())
                .build()
        );
    }

    // ── 404 Not Found ─────────────────────────────────────────────────────────
    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleAccountNotFound(AccountNotFoundException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, "Account Not Found", ex.getMessage());
    }

    // ── 400 Bad Request ───────────────────────────────────────────────────────
    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientFunds(InsufficientFundsException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, "Insufficient Funds", ex.getMessage());
    }

    @ExceptionHandler(OtpExpiredException.class)
    public ResponseEntity<ErrorResponse> handleOtpExpired(OtpExpiredException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, "OTP Expired", ex.getMessage());
    }

    @ExceptionHandler(DuplicateAccountException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateAccount(DuplicateAccountException ex) {
        return buildResponse(HttpStatus.CONFLICT, "Duplicate Account", ex.getMessage());
    }

    // ── 403 Forbidden ─────────────────────────────────────────────────────────
    @ExceptionHandler(AccountFrozenException.class)
    public ResponseEntity<ErrorResponse> handleAccountFrozen(AccountFrozenException ex) {
        return buildResponse(HttpStatus.FORBIDDEN, "Account Frozen", ex.getMessage());
    }

    // ── 401 Unauthorized ──────────────────────────────────────────────────────
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized(UnauthorizedException ex) {
        return buildResponse(HttpStatus.UNAUTHORIZED, "Unauthorized", ex.getMessage());
    }

    // ── 400 Validation Errors (@Valid on DTOs) ────────────────────────────────
    /**
     * WHY separate handler for MethodArgumentNotValidException?
     * When @Valid fails on a request body, Spring throws this exception.
     * It contains a list of ALL field errors, not just one.
     * We return a map: { "email": "must not be blank", "amount": "must be positive" }
     * Client can highlight exactly which fields are wrong.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationErrors(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errors);
    }

    // ── 500 Catch-All (unexpected errors) ─────────────────────────────────────
    /**
     * WHY catch all Exception?
     * Safety net. If something we didn't anticipate throws, we still return
     * a proper JSON instead of an ugly HTML stack trace page.
     * The message is intentionally vague: don't expose internal details to clients.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        return buildResponse(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Internal Server Error",
            "An unexpected error occurred. Please try again later."
            // NOTE: log ex.getMessage() internally, but don't send it to client
        );
    }
}
```

---

## Hour 3 — VERIFY (Test the exceptions work)

### Step 1: Also make sure dto/request folder exists, and create a temp test controller

Create file: `controller/TestController.java` (temporary — delete after Day 2)

```java
package com.hdfc.banking.controller;

import com.hdfc.banking.exception.*;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;

// TEMPORARY controller to test exceptions — delete after verifying
@RestController
@RequestMapping("/test")
public class TestController {

    @GetMapping("/account-not-found")
    public String testNotFound() {
        throw new AccountNotFoundException(999L);
    }

    @GetMapping("/insufficient-funds")
    public String testInsufficientFunds() {
        throw new InsufficientFundsException(new BigDecimal("5000"), new BigDecimal("8000"));
    }

    @GetMapping("/frozen")
    public String testFrozen() {
        throw new AccountFrozenException("HDFC001");
    }

    @GetMapping("/unauthorized")
    public String testUnauthorized() {
        throw new UnauthorizedException("Invalid or missing JWT token");
    }

    @GetMapping("/otp-expired")
    public String testOtpExpired() {
        throw new OtpExpiredException();
    }
}
```

---

### Step 2: Restart app and test in browser or terminal

```bash
# Restart:
Ctrl+C to stop → ./mvnw spring-boot:run

# Test each endpoint:
curl http://localhost:8080/test/account-not-found
curl http://localhost:8080/test/insufficient-funds
curl http://localhost:8080/test/frozen
curl http://localhost:8080/test/unauthorized
curl http://localhost:8080/test/otp-expired
```

### Expected Response for /test/account-not-found:
```json
{
  "status": 404,
  "error": "Account Not Found",
  "message": "Account with ID 999 not found",
  "timestamp": "2024-01-15T10:30:00"
}
```

### Expected Response for /test/insufficient-funds:
```json
{
  "status": 400,
  "error": "Insufficient Funds",
  "message": "Insufficient funds. Available: ₹5000, Requested: ₹8000",
  "timestamp": "2024-01-15T10:30:00"
}
```

---

## Checkpoint Questions (Answer Before Day 3)

1. What is the difference between `RuntimeException` and `Exception`? Why do we use `RuntimeException` for custom banking exceptions?
2. What annotation makes `GlobalExceptionHandler` work? What two annotations does it combine?
3. If `AccountNotFoundException` is thrown in `AccountService`, how does it get to `GlobalExceptionHandler` without try-catch in the controller?
4. Why does `AccountFrozenException` return 403 and not 400?
5. Why does `MethodArgumentNotValidException` return a Map instead of ErrorResponse?
6. What would happen if you forgot the catch-all `@ExceptionHandler(Exception.class)`? What would the client see?

---

## What You Built Today

```
Before Today:         After Today:
Any error → 500       AccountNotFound → 404
HTML stack trace      InsufficientFunds → 400
No structure          Frozen → 403
                      Unauthorized → 401
                      All return structured JSON ✅
```

---

## Day 3 Preview — Repositories + DTOs

Tomorrow you build:
- `UserRepository`, `AccountRepository`, `TransactionRepository` (Spring Data JPA interfaces)
- Request DTOs: `RegisterRequest`, `LoginRequest`, `TransferRequest`
- Response DTOs: `AccountResponse`, `LoginResponse`

These are the "bridge" between your database entities and your API — entities are internal, DTOs are what clients see.
