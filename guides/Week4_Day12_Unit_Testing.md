# Week 4, Day 12 — Unit Testing with JUnit 5 + Mockito

> **Goal:** Write unit tests for your services to verify your business logic works correctly.
>
> Real-world: Every banking system has tests. Without tests, you can't confidently
> deploy new features — any change might break existing functionality.

---

## What is Unit Testing?

```
A unit test checks ONE method in isolation.
It does NOT connect to the database.
It does NOT start the Spring application.
It is FAST — runs in milliseconds.
```

**Example in real life:**
```
You wrote transfer() method.
Unit test checks:
  ✅ Transfer works when balance is enough
  ✅ Throws InsufficientFundsException when balance is low
  ✅ Throws UnauthorizedException when wrong user
  ✅ Throws when transferring to same account
```

---

## Key Annotations

| Annotation | Meaning |
|---|---|
| `@ExtendWith(MockitoExtension.class)` | Use Mockito — no Spring context needed |
| `@Mock` | Creates a fake version of a class (no real DB) |
| `@InjectMocks` | Creates the real class and injects all `@Mock` into it |
| `@Test` | Marks this method as a test |
| `when(...).thenReturn(...)` | Tell the fake class what to return |
| `assertThrows(...)` | Verify that an exception is thrown |
| `verify(...)` | Verify a method was called |

---

## Step 1 — Understand the Folder Structure

Tests go inside `src/test/java/com/hdfc/banking/service/` (NOT `src/main`):

```
src/
  test/
    java/
      com/hdfc/banking/
        BankingApplicationTests.java  ← already exists (don't touch)
        service/
          TransactionServiceTest.java   ← you will create this
          AuthServiceTest.java          ← you will create this
```

---

## Step 2 — Create `TransactionServiceTest.java`

Create file: `src/test/java/com/hdfc/banking/service/TransactionServiceTest.java`

### Class setup:
```java
package com.hdfc.banking.service;

import com.hdfc.banking.dto.request.TransferRequest;
import com.hdfc.banking.dto.response.TransactionResponse;
import com.hdfc.banking.entity.Account;
import com.hdfc.banking.entity.Transaction;
import com.hdfc.banking.entity.User;
import com.hdfc.banking.enums.AccountStatus;
import com.hdfc.banking.enums.AccountType;
import com.hdfc.banking.exception.InsufficientFundsException;
import com.hdfc.banking.exception.UnauthorizedException;
import com.hdfc.banking.repository.AccountRepository;
import com.hdfc.banking.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private FraudDetectionService fraudDetectionService;

    @InjectMocks
    private TransactionService transactionService;

    // Test data — reused in all tests
    private User user;
    private Account fromAccount;
    private Account toAccount;

    @BeforeEach
    void setUp() {
        // Create a test user
        user = User.builder()
                .id(1L)
                .name("Naga")
                .email("naga@test.com")
                .build();

        // Create sender account
        fromAccount = Account.builder()
                .id(1L)
                .accountNumber("HDFC11111111")
                .accountType(AccountType.SAVINGS)
                .accountStatus(AccountStatus.ACTIVE)
                .balance(new BigDecimal("50000"))
                .user(user)
                .build();

        // Create receiver account
        toAccount = Account.builder()
                .id(2L)
                .accountNumber("HDFC22222222")
                .accountType(AccountType.SAVINGS)
                .accountStatus(AccountStatus.ACTIVE)
                .balance(new BigDecimal("10000"))
                .user(User.builder().id(2L).email("other@test.com").build())
                .build();
    }
```

---

### Test 1 — Happy path: transfer succeeds

```java
    @Test
    void transfer_shouldSucceed_whenBalanceIsSufficient() {
        // ARRANGE — set up fake responses
        TransferRequest request = new TransferRequest();
        // Use reflection or setter (since @Data gives setters):
        request.setFromAccountNumber("HDFC11111111");
        request.setToAccountNumber("HDFC22222222");
        request.setAmount(new BigDecimal("10000"));
        request.setDescription("Test transfer");

        when(accountRepository.findByAccountNumber("HDFC11111111"))
                .thenReturn(Optional.of(fromAccount));
        when(accountRepository.findByAccountNumber("HDFC22222222"))
                .thenReturn(Optional.of(toAccount));
        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // ACT
        TransactionResponse response = transactionService.transfer(request, "naga@test.com");

        // ASSERT
        assertNotNull(response);
        assertEquals(new BigDecimal("40000"), fromAccount.getBalance());   // 50000 - 10000
        assertEquals(new BigDecimal("20000"), toAccount.getBalance());     // 10000 + 10000
        verify(accountRepository, times(2)).save(any(Account.class));      // both accounts saved
        verify(transactionRepository).save(any(Transaction.class));        // transaction saved
    }
```

---

### Test 2 — Transfer fails: insufficient balance

```java
    @Test
    void transfer_shouldThrow_whenInsufficientBalance() {
        TransferRequest request = new TransferRequest();
        request.setFromAccountNumber("HDFC11111111");
        request.setToAccountNumber("HDFC22222222");
        request.setAmount(new BigDecimal("99999"));   // more than balance (50000)
        request.setDescription("Too much");

        when(accountRepository.findByAccountNumber("HDFC11111111"))
                .thenReturn(Optional.of(fromAccount));
        when(accountRepository.findByAccountNumber("HDFC22222222"))
                .thenReturn(Optional.of(toAccount));

        // ASSERT — expect exception to be thrown
        assertThrows(InsufficientFundsException.class,
                () -> transactionService.transfer(request, "naga@test.com"));

        // Verify NO money was saved (balance should NOT change)
        verify(accountRepository, never()).save(any(Account.class));
    }
```

---

### Test 3 — Transfer fails: wrong user

```java
    @Test
    void transfer_shouldThrow_whenWrongUser() {
        TransferRequest request = new TransferRequest();
        request.setFromAccountNumber("HDFC11111111");
        request.setToAccountNumber("HDFC22222222");
        request.setAmount(new BigDecimal("1000"));
        request.setDescription("Hacker attempt");

        when(accountRepository.findByAccountNumber("HDFC11111111"))
                .thenReturn(Optional.of(fromAccount));
        when(accountRepository.findByAccountNumber("HDFC22222222"))
                .thenReturn(Optional.of(toAccount));

        // "hacker@evil.com" is NOT the owner (owner is "naga@test.com")
        assertThrows(UnauthorizedException.class,
                () -> transactionService.transfer(request, "hacker@evil.com"));
    }
```

---

### Test 4 — Transfer fails: same account

```java
    @Test
    void transfer_shouldThrow_whenSameAccount() {
        TransferRequest request = new TransferRequest();
        request.setFromAccountNumber("HDFC11111111");
        request.setToAccountNumber("HDFC11111111");   // same!
        request.setAmount(new BigDecimal("1000"));
        request.setDescription("Self transfer");

        when(accountRepository.findByAccountNumber("HDFC11111111"))
                .thenReturn(Optional.of(fromAccount));

        assertThrows(RuntimeException.class,
                () -> transactionService.transfer(request, "naga@test.com"));
    }
}   // end of class
```

---

## Step 3 — Create `AuthServiceTest.java`

Create file: `src/test/java/com/hdfc/banking/service/AuthServiceTest.java`

```java
package com.hdfc.banking.service;

import com.hdfc.banking.dto.request.RegisterRequest;
import com.hdfc.banking.exception.DuplicateAccountException;
import com.hdfc.banking.repository.OtpTokenRepository;
import com.hdfc.banking.repository.UserRepository;
import com.hdfc.banking.config.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private OtpTokenRepository otpTokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    private AuthService authService;

    @Test
    void register_shouldSucceed_whenEmailIsNew() {
        RegisterRequest request = new RegisterRequest();
        request.setName("Naga");
        request.setEmail("naga@test.com");
        request.setPassword("pass1234");
        request.setPhone("9876543210");

        when(userRepository.existsByEmail("naga@test.com")).thenReturn(false);
        when(passwordEncoder.encode("pass1234")).thenReturn("hashed_password");

        String result = authService.register(request);

        assertEquals("Registration successful", result);
        verify(userRepository).save(any());
    }

    @Test
    void register_shouldThrow_whenEmailAlreadyExists() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("existing@test.com");
        request.setPassword("pass1234");
        request.setName("Test");
        request.setPhone("1234567890");

        when(userRepository.existsByEmail("existing@test.com")).thenReturn(true);

        assertThrows(DuplicateAccountException.class,
                () -> authService.register(request));

        // Verify user was NOT saved
        verify(userRepository, never()).save(any());
    }
}
```

---

## Step 4 — Run Tests

```bash
cd /Users/nagateja/Desktop/spring/HDFCBanking/banking
./mvnw test
```

Expected output:
```
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

---

## Step 5 — Understanding what `when().thenReturn()` means

```java
// Real code would do this:
accountRepository.findByAccountNumber("HDFC11111111")
// → goes to database → returns result

// With mock, we say: "when this is called, return THIS instead of going to DB"
when(accountRepository.findByAccountNumber("HDFC11111111"))
        .thenReturn(Optional.of(fromAccount));
// → no database → instantly returns fromAccount
```

This makes tests **fast** and **predictable** — no real data needed.

---

## Step 6 — Git Commit

```bash
git add .
git commit -m "Week4-Day12: Unit tests for TransactionService + AuthService"
git push
```

---

## Key Concepts Learned Today

| Concept | Explanation |
|---|---|
| `@Mock` | Fake version of a class — no real DB or Spring |
| `@InjectMocks` | Real class with all mocks injected |
| `when().thenReturn()` | Define what fake class returns |
| `assertThrows()` | Verify exception is thrown |
| `verify(mock, never()).method()` | Verify method was NEVER called |
| `verify(mock, times(2)).method()` | Verify method called exactly 2 times |
| `@BeforeEach` | Runs before EACH test to reset test data |

---

## After This → Week 4, Day 13 — Integration Tests (real DB, real Spring)
