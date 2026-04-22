# Week 1 Day 5 — Service Layer + Controllers
## AuthService + AccountService + REST Controllers

---

## What You Will Build Today

```
service/
  ├── AuthService.java
  └── AccountService.java

controller/
  ├── AuthController.java
  └── AccountController.java
```

After today: you can make your **first real end-to-end API call** ✅
Register → Login → OTP → JWT Token → Create Account → View Balance

**Time: ~5 hours**

---

## Hour 1 — READ FIRST

### WHY Services? (Controllers are already there — why add a layer?)

```
WITHOUT service layer (bad):
Controller → does everything: DB queries, business logic, email, validation
→ one class has 500 lines
→ impossible to test
→ duplicate code between endpoints

WITH service layer (good):
Controller → receives HTTP request → calls service → returns response
Service    → business logic: validate, query DB, send email, throw exceptions
Repository → only DB work

Rule:
  Controller = HOW to receive/respond (HTTP concern)
  Service    = WHAT to do (business concern)
  Repository = WHERE to store (data concern)
```

---

### The FLOW you're implementing

```
POST /api/auth/register
  └── AuthController.register()
        └── AuthService.register()
              ├── Check email not taken (UserRepository)
              ├── Hash password (BCrypt)
              ├── Save User to DB (UserRepository)
              └── Return "Registration successful. Check email to verify."

POST /api/auth/login
  └── AuthController.login()
        └── AuthService.login()
              ├── AuthenticationManager.authenticate() → checks email+password
              ├── If correct → generate 6-digit OTP
              ├── Save OTP to DB (OtpTokenRepository)
              └── Return "OTP sent to email"

POST /api/auth/verify-otp
  └── AuthController.verifyOtp()
        └── AuthService.verifyOtp()
              ├── Find OTP in DB (OtpTokenRepository)
              ├── Check not expired (LocalDateTime.now())
              ├── Mark OTP as used
              ├── Generate JWT token (JwtTokenProvider)
              └── Return LoginResponse { token, name, email, role }
```

---

### WHY @Transactional on service methods?

```java
// WITHOUT @Transactional:
public void transfer(String from, String to, BigDecimal amount) {
    debitFromAccount(from, amount);     // ← DB update 1 (committed!)
    // ← Exception happens here
    creditToAccount(to, amount);        // ← never runs
    // Result: ₹5000 debited, but ₹5000 never credited → money disappears!
}

// WITH @Transactional:
@Transactional
public void transfer(...) {
    debitFromAccount(from, amount);     // ← DB update 1 (pending)
    // ← Exception happens here → ROLLBACK everything
    creditToAccount(to, amount);        // ← never runs
    // Result: nothing committed → both accounts unchanged. Money is safe!
}
```

`@Transactional` = all DB operations in the method succeed together or fail together (atomicity).

---

## Hour 2 — CODE: AuthService

Create file: `service/AuthService.java`

```java
package com.hdfc.banking.service;

import com.hdfc.banking.config.JwtTokenProvider;
import com.hdfc.banking.dto.request.LoginRequest;
import com.hdfc.banking.dto.request.OtpVerifyRequest;
import com.hdfc.banking.dto.request.RegisterRequest;
import com.hdfc.banking.dto.response.LoginResponse;
import com.hdfc.banking.entity.OtpToken;
import com.hdfc.banking.entity.User;
import com.hdfc.banking.exception.OtpExpiredException;
import com.hdfc.banking.exception.UnauthorizedException;
import com.hdfc.banking.repository.OtpTokenRepository;
import com.hdfc.banking.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final OtpTokenRepository otpTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * Register a new user.
     *
     * WHY @Transactional?
     * We save user to DB. If anything fails after save (unlikely here but possible),
     * the user record is rolled back — no orphaned data.
     */
    @Transactional
    public String register(RegisterRequest request) {
        // Check if email already exists
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already registered: " + request.getEmail());
        }

        User user = User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                // BCrypt turns "mypassword123" → "$2a$10$..." (hash)
                // Even if DB is breached, attacker can't reverse the hash
                .phone(request.getPhone())
                .build();

        userRepository.save(user);
        log.info("New user registered: {}", request.getEmail());

        return "Registration successful. You can now log in.";
    }

    /**
     * Step 1 of login: validate credentials → send OTP.
     * Returns a message, not a token (token comes after OTP verification).
     */
    @Transactional
    public String login(LoginRequest request) {
        try {
            // Let Spring Security validate email + password (BCrypt check)
            authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                    request.getEmail(), request.getPassword()
                )
            );
        } catch (BadCredentialsException e) {
            throw new UnauthorizedException("Invalid email or password");
        } catch (AuthenticationException e) {
            throw new UnauthorizedException("Authentication failed: " + e.getMessage());
        }

        // Credentials correct → generate OTP
        String otpCode = generateOtp();

        // Save OTP to DB (expires in 5 minutes)
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        OtpToken otp = OtpToken.builder()
                .user(user)
                .otpCode(otpCode)
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .isUsed(false)
                .build();

        otpTokenRepository.save(otp);

        // TODO Week 2: send actual OTP email via MailService
        // For now: log it so we can test without email server
        log.info("OTP for {} : {}", request.getEmail(), otpCode);

        return "OTP sent to " + request.getEmail() + ". Valid for 5 minutes.";
    }

    /**
     * Step 2 of login: verify OTP → return JWT.
     * @Transactional because we update isUsed=true in OTP record.
     */
    @Transactional
    public LoginResponse verifyOtp(OtpVerifyRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        // Find valid OTP: not used, not expired, most recent
        OtpToken otp = otpTokenRepository
                .findTopByUserAndIsUsedFalseAndExpiresAtAfterOrderByCreatedAtDesc(
                    user, LocalDateTime.now()
                )
                .orElseThrow(() -> new OtpExpiredException());

        // Check OTP code matches
        if (!otp.getOtpCode().equals(request.getOtpCode())) {
            throw new UnauthorizedException("Invalid OTP");
        }

        // Mark as used (one-time use)
        otp.setIsUsed(true);
        otpTokenRepository.save(otp);

        // Generate JWT
        String token = jwtTokenProvider.generateToken(
            user.getEmail(), user.getRole().name()
        );

        log.info("User logged in: {}", user.getEmail());

        return LoginResponse.builder()
                .token(token)
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole())
                .build();
    }

    /**
     * Generates a 6-digit OTP using SecureRandom.
     *
     * WHY SecureRandom and not Random?
     * Random is PREDICTABLE — attacker can guess the sequence.
     * SecureRandom is cryptographically secure — unpredictable.
     * For security codes, always use SecureRandom.
     */
    private String generateOtp() {
        SecureRandom random = new SecureRandom();
        int otp = 100000 + random.nextInt(900000); // always 6 digits
        return String.valueOf(otp);
    }
}
```

---

## Hour 3 — CODE: AccountService

Create file: `service/AccountService.java`

```java
package com.hdfc.banking.service;

import com.hdfc.banking.dto.response.AccountResponse;
import com.hdfc.banking.entity.Account;
import com.hdfc.banking.entity.User;
import com.hdfc.banking.enums.AccountType;
import com.hdfc.banking.exception.AccountNotFoundException;
import com.hdfc.banking.exception.DuplicateAccountException;
import com.hdfc.banking.repository.AccountRepository;
import com.hdfc.banking.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;

    /**
     * Create a bank account for the logged-in user.
     *
     * WHY check existsByUserAndAccountType?
     * Business rule: one account per type per user.
     * Can't have 2 SAVINGS accounts.
     */
    @Transactional
    public AccountResponse createAccount(String userEmail, AccountType accountType) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        // Enforce one-account-per-type rule
        if (accountRepository.existsByUserAndAccountType(user, accountType)) {
            throw new DuplicateAccountException(accountType.name());
        }

        Account account = Account.builder()
                .user(user)
                .accountNumber(generateAccountNumber())
                .accountType(accountType)
                .build();

        Account saved = accountRepository.save(account);
        log.info("Account created: {} for user: {}", saved.getAccountNumber(), userEmail);

        return toResponse(saved);
    }

    /**
     * Get all accounts for the logged-in user.
     * Uses Streams to convert List<Account> → List<AccountResponse>
     * (never expose entity directly to controller)
     */
    public List<AccountResponse> getMyAccounts(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        return accountRepository.findByUser(user)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get a specific account by its account number.
     * Used by logged-in user to check balance.
     */
    public AccountResponse getAccount(String accountNumber) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException(accountNumber));
        return toResponse(account);
    }

    /**
     * Convert Account entity → AccountResponse DTO
     *
     * WHY a separate method?
     * DRY: avoid writing the same mapping in createAccount, getMyAccounts, getAccount.
     * Called with this::toResponse in stream operations.
     */
    private AccountResponse toResponse(Account account) {
        return AccountResponse.builder()
                .id(account.getId())
                .accountNumber(account.getAccountNumber())
                .accountType(account.getAccountType())
                .accountStatus(account.getAccountStatus())
                .balance(account.getBalance())
                .interestRate(account.getInterestRate())
                .createdAt(account.getCreatedAt())
                .userEmail(account.getUser().getEmail())
                .build();
    }

    /**
     * Generates a unique 16-digit account number.
     * Format: HDFC + 12 digits
     * Example: HDFC123456789012
     *
     * TODO Week 2: Check DB to ensure uniqueness before saving.
     */
    private String generateAccountNumber() {
        Random random = new Random();
        long number = (long) (Math.random() * 1_000_000_000_000L);
        return "HDFC" + String.format("%012d", number);
    }
}
```

---

## Hour 4 — CODE: Controllers

### AuthController.java

Create file: `controller/AuthController.java`

```java
package com.hdfc.banking.controller;

import com.hdfc.banking.dto.request.LoginRequest;
import com.hdfc.banking.dto.request.OtpVerifyRequespackage com.hdfc.banking.config;

import com.hdfc.banking.security.CustomUserDetailsService;
import com.hdfc.banking.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * REAL Security Config — replaces the temporary permit-all version.
 *
 * Key decisions:
 * 1. STATELESS session — no server-side sessions, JWT only
 * 2. CSRF disabled — not needed for stateless REST APIs (CSRF targets session cookies)
 * 3. Public endpoints: /api/auth/** (login, register, verify-otp)
 * 4. Protected endpoints: everything else requires valid JWT
 * 5. JwtAuthenticationFilter runs BEFORE Spring's default auth filter
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity       // enables @PreAuthorize("hasRole('ADMIN')") on methods
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CustomUserDetailsService userDetailsService;

    /**
     * WHY BCrypt?
     * Never store plain-text passwords. BCrypt:
     * - Adds random salt → same password hashes differently each time
     * - Intentionally slow (configurable rounds) → makes brute-force attacks expensive
     * - Industry standard for password hashing
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * DaoAuthenticationProvider wires together:
     * - WHERE to find users (userDetailsService → our DB)
     * - HOW to verify passwords (BCrypt)
     * Used internally by AuthenticationManager during login.
     */
    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    /**
     * AuthenticationManager is the entry point for authentication.
     * We expose it as a bean so AuthService can call:
     *   authManager.authenticate(new UsernamePasswordAuthenticationToken(email, password))
     */
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())

            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                // STATELESS = Spring never creates HTTP sessions
                // Each request must carry JWT — no "remember me" cookies
            )

            .headers(headers -> headers
                .frameOptions(frame -> frame.disable())  // H2 console iframe
            )

            .authorizeHttpRequests(auth -> auth
                // PUBLIC — no token needed:
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/h2-console/**").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/test/**").permitAll()     // test endpoints (Day 2)

                // ADMIN only:
                .requestMatchers("/api/admin/**").hasRole("ADMIN")

                // Everything else needs a valid JWT:
                .anyRequest().authenticated()
            )

            // Add our JWT filter BEFORE Spring's default auth filter
            .addFilterBefore(jwtAuthenticationFilter,
                             UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}t;
import com.hdfc.banking.dto.request.RegisterRequest;
import com.hdfc.banking.dto.response.LoginResponse;
import com.hdfc.banking.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * @RestController = @Controller + @ResponseBody
 *   → All return values are automatically serialized to JSON
 *
 * @RequestMapping("/api/auth") = base URL for all methods in this class
 *
 * WHY ResponseEntity<>?
 * Lets us control both the response body AND the HTTP status code.
 * return ResponseEntity.ok(body)             → 200 OK
 * return ResponseEntity.status(201).body(x)  → 201 Created
 * Without it: always returns 200 even for creation (incorrect REST).
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * POST /api/auth/register
     * Body: { "name": "Naga", "email": "naga@gmail.com", "password": "secure123" }
     *
     * WHY @Valid?
     * Triggers @NotBlank, @Email, @Size validation on RegisterRequest.
     * Without @Valid, those annotations are IGNORED — garbage input reaches service.
     *
     * WHY 201 Created?
     * REST convention: POST that creates something returns 201, not 200.
     */
    @PostMapping("/register")
    public ResponseEntity<String> register(@Valid @RequestBody RegisterRequest request) {
        String message = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(message);
    }

    /**
     * POST /api/auth/login
     * Body: { "email": "naga@gmail.com", "password": "secure123" }
     * Response: "OTP sent to naga@gmail.com"
     */
    @PostMapping("/login")
    public ResponseEntity<String> login(@Valid @RequestBody LoginRequest request) {
        String message = authService.login(request);
        return ResponseEntity.ok(message);
    }

    /**
     * POST /api/auth/verify-otp
     * Body: { "email": "naga@gmail.com", "otpCode": "123456" }
     * Response: { "token": "eyJ...", "name": "Naga", "email": "...", "role": "CUSTOMER" }
     */
    @PostMapping("/verify-otp")
    public ResponseEntity<LoginResponse> verifyOtp(@Valid @RequestBody OtpVerifyRequest request) {
        LoginResponse response = authService.verifyOtp(request);
        return ResponseEntity.ok(response);
    }
}
```

---

### AccountController.java

Create file: `controller/AccountController.java`

```java
package com.hdfc.banking.controller;

import com.hdfc.banking.dto.request.CreateAccountRequest;
import com.hdfc.banking.dto.response.AccountResponse;
import com.hdfc.banking.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    /**
     * WHY SecurityContextHolder.getContext().getAuthentication().getName()?
     *
     * When JwtAuthenticationFilter runs, it sets the logged-in user.
     * SecurityContextHolder stores that user for this request's thread.
     * .getName() returns the email (which we set as the "subject" in JWT).
     *
     * This is how the controller knows WHO is making the request WITHOUT
     * requiring the user to send their email in the request body.
     * The JWT token already proves their identity.
     */
    private String getCurrentUserEmail() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    /**
     * POST /api/accounts/create
     * Header: Authorization: Bearer <jwt_token>
     * Body: { "accountType": "SAVINGS" }
     */
    @PostMapping("/create")
    public ResponseEntity<AccountResponse> createAccount(
            @Valid @RequestBody CreateAccountRequest request) {
        String email = getCurrentUserEmail();
        AccountResponse response = accountService.createAccount(email, request.getAccountType());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * GET /api/accounts/my
     * Header: Authorization: Bearer <jwt_token>
     * Returns all accounts belonging to the logged-in user.
     */
    @GetMapping("/my")
    public ResponseEntity<List<AccountResponse>> getMyAccounts() {
        String email = getCurrentUserEmail();
        List<AccountResponse> accounts = accountService.getMyAccounts(email);
        return ResponseEntity.ok(accounts);
    }

    /**
     * GET /api/accounts/{accountNumber}
     * Header: Authorization: Bearer <jwt_token>
     * Returns details + balance for one account.
     */
    @GetMapping("/{accountNumber}")
    public ResponseEntity<AccountResponse> getAccount(
            @PathVariable String accountNumber) {
        AccountResponse response = accountService.getAccount(accountNumber);
        return ResponseEntity.ok(response);
    }
}
```

---

## Hour 5 — VERIFY (Complete Flow Test in Postman)

### Step 1: Register

```
POST http://localhost:8080/api/auth/register
Content-Type: application/json

{
  "name": "Naga Teja",
  "email": "naga@gmail.com",
  "password": "secure123"
}
```
Expected: `201 Created` → `"Registration successful. You can now log in."`

---

### Step 2: Login

```
POST http://localhost:8080/api/auth/login
Content-Type: application/json

{
  "email": "naga@gmail.com",
  "password": "secure123"
}
```
Expected: `200 OK` → `"OTP sent to naga@gmail.com. Valid for 5 minutes."`

---

### Step 3: Check OTP in Terminal Logs

Look in your running app terminal for:
```
INFO --- OTP for naga@gmail.com : 847291   ← your OTP is here
```

---

### Step 4: Verify OTP → Get Token

```
POST http://localhost:8080/api/auth/verify-otp
Content-Type: application/json

{
  "email": "naga@gmail.com",
  "otpCode": "847291"
}
```
Expected: `200 OK` →
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "name": "Naga Teja",
  "email": "naga@gmail.com",
  "role": "CUSTOMER"
}
```

**COPY THE TOKEN.**

---

### Step 5: Create Bank Account (using JWT)

```
POST http://localhost:8080/api/accounts/create
Content-Type: application/json
Authorization: Bearer <paste token here>

{
  "accountType": "SAVINGS"
}
```
Expected: `201 Created` →
```json
{
  "id": 1,
  "accountNumber": "HDFC123456789012",
  "accountType": "SAVINGS",
  "accountStatus": "ACTIVE",
  "balance": 0.0000,
  "interestRate": 3.50,
  "createdAt": "2026-04-19T..."
}
```

---

### Step 6: View Your Accounts

```
GET http://localhost:8080/api/accounts/my
Authorization: Bearer <paste token here>
```
Expected: `200 OK` → array with your SAVINGS account.

---

## Checkpoint Questions

1. Why is business logic in `AuthService` and not in `AuthController`?
2. What happens if you call `login()` without `@Transactional`? (Hint: OTP is saved to DB)
3. In `AccountController.getCurrentUserEmail()`, where does this email come from? Trace it from the HTTP request.
4. WHY `SecureRandom` and not `Random` for `generateOtp()`?
5. What does `@Valid` do in `@PostMapping("/register")`? What happens without it?
6. WHY return `201 Created` for register/create, but `200 OK` for login/getAccounts?

---

## Day 6 Preview — Transaction Service

Tomorrow you implement the most critical banking logic:
- `TransactionService.transfer()` — debits source, credits destination, saves transaction record
- `@Transactional` with rollback on any failure
- Pessimistic locking to prevent race conditions
- `TransactionController` — POST /api/transactions/transfer, GET /api/transactions/history
