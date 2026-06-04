# Week 4, Day 14 — Spring AOP: Audit Logging

> **Goal:** Log every sensitive banking action automatically — without adding logging
> code inside every service method.
>
> Real-world: HDFC must keep an audit trail of WHO did WHAT and WHEN.
> RBI regulations require banks to log all sensitive operations.

---

## The Problem Without AOP

Without AOP, you'd add log code inside EVERY method:
```java
public void deposit(...) {
    log.info("User {} deposited {}", email, amount);   // repeated everywhere
    // ... business logic
    log.info("Deposit complete");                       // repeated everywhere
}

public void withdraw(...) {
    log.info("User {} withdrew {}", email, amount);    // copy-paste everywhere
    // ...
}
```

This violates **DRY principle** — same code repeated in dozens of methods.

---

## The AOP Solution

```
@Around("any method in service package")
→ Runs YOUR code BEFORE and AFTER every service method automatically
→ No changes to service classes at all
```

---

## Key Concepts

| Concept | Meaning |
|---|---|
| **Aspect** | The class containing your cross-cutting logic (logging) |
| **Advice** | The code that runs (before, after, around) |
| **Pointcut** | The expression that selects WHICH methods to intercept |
| `@Before` | Runs BEFORE the method |
| `@AfterReturning` | Runs AFTER method succeeds |
| `@AfterThrowing` | Runs AFTER method throws an exception |
| `@Around` | Runs BEFORE and AFTER — most powerful |

---

## Step 1 — Check if AOP Dependency Exists

Open `pom.xml` and verify this dependency exists (it's included by default in Spring Boot):

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aop</artifactId>
</dependency>
```

If missing, add it. Most Spring Boot projects already have it.

---

## Step 2 — Check Your Existing `AuditLog` Entity

Your project already has an `audit_logs` table (visible in the test output).
Let's check it — open `entity/AuditLog.java` if it exists, or create it:

```java
package com.hdfc.banking.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

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

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Column(length = 50)
    private String entityType;          // e.g. "TRANSACTION", "ACCOUNT"

    @Column(length = 100)
    private String action;              // e.g. "DEPOSIT", "TRANSFER", "FREEZE"

    @Column(length = 150)
    private String changedBy;           // email of the user who did it

    @Column(columnDefinition = "TEXT")
    private String oldValue;            // optional: state before

    @Column(columnDefinition = "TEXT")
    private String newValue;            // optional: state after
}
```

---

## Step 3 — Create `AuditLogRepository.java`

Create file: `repository/AuditLogRepository.java`

```java
package com.hdfc.banking.repository;

import com.hdfc.banking.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByChangedByOrderByTimestampDesc(String changedBy);

    List<AuditLog> findByEntityTypeAndActionOrderByTimestampDesc(String entityType, String action);
}
```

---

## Step 4 — Create `AuditAspect.java`

Create file: `aspect/AuditAspect.java`

```java
package com.hdfc.banking.aspect;

import com.hdfc.banking.entity.AuditLog;
import com.hdfc.banking.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Arrays;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditAspect {

    private final AuditLogRepository auditLogRepository;
```

---

### Pointcut — which methods to intercept

```java
    // Intercept all public methods in TransactionService
    @Pointcut("execution(* com.hdfc.banking.service.TransactionService.*(..))")
    public void transactionMethods() {}

    // Intercept all public methods in AdminService
    @Pointcut("execution(* com.hdfc.banking.service.AdminService.*(..))")
    public void adminMethods() {}

    // Combine both
    @Pointcut("transactionMethods() || adminMethods()")
    public void sensitiveMethods() {}
```

---

### Before advice — log when method STARTS

```java
    @Before("sensitiveMethods()")
    public void logBefore(JoinPoint joinPoint) {
        String methodName = joinPoint.getSignature().getName();
        String args = Arrays.toString(joinPoint.getArgs());
        String user = getCurrentUser();

        log.info("[AUDIT] {} called {} with args: {}", user, methodName, args);
    }
```

---

### After returning — log SUCCESS

```java
    @AfterReturning(pointcut = "sensitiveMethods()", returning = "result")
    public void logAfterSuccess(JoinPoint joinPoint, Object result) {
        String methodName = joinPoint.getSignature().getName();
        String user = getCurrentUser();

        // Determine entity type and action from method name
        String entityType = methodName.contains("Account") ? "ACCOUNT" : "TRANSACTION";
        String action = methodName.toUpperCase();

        // Save to DB
        AuditLog auditLog = AuditLog.builder()
                .timestamp(LocalDateTime.now())
                .entityType(entityType)
                .action(action)
                .changedBy(user)
                .newValue(result != null ? result.toString() : "void")
                .build();

        auditLogRepository.save(auditLog);
        log.info("[AUDIT] {} completed {} successfully", user, methodName);
    }
```

---

### After throwing — log FAILURES

```java
    @AfterThrowing(pointcut = "sensitiveMethods()", throwing = "ex")
    public void logAfterException(JoinPoint joinPoint, Throwable ex) {
        String methodName = joinPoint.getSignature().getName();
        String user = getCurrentUser();

        log.warn("[AUDIT] {} failed {} — reason: {}", user, methodName, ex.getMessage());

        AuditLog auditLog = AuditLog.builder()
                .timestamp(LocalDateTime.now())
                .entityType("ERROR")
                .action(methodName.toUpperCase() + "_FAILED")
                .changedBy(user)
                .newValue("ERROR: " + ex.getMessage())
                .build();

        auditLogRepository.save(auditLog);
    }
```

---

### Helper — get current logged-in user's email

```java
    private String getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !auth.getName().equals("anonymousUser")) {
            return auth.getName();   // returns email (JWT subject)
        }
        return "SYSTEM";   // for scheduled jobs like interest calculation
    }
}   // end of class
```

---

### Imports needed:
```java
import com.hdfc.banking.entity.AuditLog;
import com.hdfc.banking.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.Arrays;
```

---

## Step 5 — Add Admin Endpoint to View Audit Logs

Open `controller/AdminController.java` and add:

```java
// Add to imports:
import com.hdfc.banking.entity.AuditLog;
import com.hdfc.banking.repository.AuditLogRepository;

// Add field:
private final AuditLogRepository auditLogRepository;

// Add endpoint:
@GetMapping("/audit-logs")
@PreAuthorize("hasRole('ADMIN')")
public ResponseEntity<List<AuditLog>> getAuditLogs() {
    return ResponseEntity.ok(auditLogRepository.findAll());
}
```

---

## Step 6 — Test It

### 1. Start the app (already running) and do a deposit:
```
POST /api/transactions/deposit?accountNumber=HDFC12345678&amount=5000
Authorization: Bearer <token>
```

### 2. Check logs in terminal — you should see:
```
[AUDIT] naga@gmail.com called deposit with args: [HDFC12345678, 5000]
[AUDIT] naga@gmail.com completed deposit successfully
```

### 3. Check the database:
```
GET /api/admin/audit-logs
Authorization: Bearer <admin-token>

Response:
[{
  "id": 1,
  "timestamp": "2026-05-12T...",
  "entityType": "TRANSACTION",
  "action": "DEPOSIT",
  "changedBy": "naga@gmail.com",
  "newValue": "..."
}]
```

### 4. Try a failed operation (e.g., withdraw more than balance):
```
POST /api/transactions/withdraw?accountNumber=HDFC12345678&amount=999999

Check audit_logs table — should have a WITHDRAW_FAILED entry
```

---

## Step 7 — Git Commit

```bash
git add .
git commit -m "Week4-Day14: AOP Audit Logging aspect"
git push
```

---

## Key Concepts Learned Today

| Concept | Explanation |
|---|---|
| `@Aspect` | Marks the class as an AOP aspect |
| `@Component` | Makes it a Spring bean |
| `@Pointcut` | Defines WHICH methods to intercept (regex-like expression) |
| `@Before` | Code runs BEFORE the method |
| `@AfterReturning` | Code runs AFTER method returns successfully |
| `@AfterThrowing` | Code runs AFTER method throws an exception |
| `SecurityContextHolder` | Gets the current logged-in user without passing it as param |
| `JoinPoint` | Gives you method name, args, class — at runtime |

---

## Why This is Powerful

```java
// WITHOUT AOP — you'd add this to EVERY method manually:
public TransactionResponse deposit(...) {
    log.info("deposit called");          // ← manual
    // business logic
    auditLogRepo.save(...);              // ← manual
    log.info("deposit done");            // ← manual
}

// WITH AOP — zero changes to service:
public TransactionResponse deposit(...) {
    // ONLY business logic — clean!
}
// AOP handles logging automatically for ALL methods ↑
```

---

## After This → Day 15 — Rate Limiting with Bucket4j
