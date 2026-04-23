# Week 2 Fix Guide — Complete the Auth + Account Layer

---

## Step 1 — Fix `User.java` (Add 3 missing fields + @Version)

Open: `entity/User.java`

After the `isVerified` field (around line 47), add:

```java
@Version
private Integer version;        // optimistic locking — prevents concurrent edit conflicts

private String panNumber;       // stored encrypted (AES-256) — Week 6

@Builder.Default
private int failedLogins = 0;   // incremented on each wrong password

@Builder.Default
private boolean locked = false; // true after 3 failed logins → 403 on next attempt
```

---

## Step 2 — Fix `AuthService.java` (Complete the broken line + add methods)

Open: `service/AuthService.java`

**Line 24 — fix the broken incomplete line:**
```java
// WRONG (your current code):
private final JwtTokenProvider

// CORRECT:
private final JwtTokenProvider jwtTokenProvider;
```

**Then add these 3 methods inside the class (after the fields):**

### register()
```java
@Transactional
public String register(RegisterRequest request) {
    if (userRepository.existsByEmail(request.getEmail())) {
        throw new RuntimeException("Email already registered");
    }
    User user = User.builder()
            .name(request.getName())
            .email(request.getEmail())
            .password(passwordEncoder.encode(request.getPassword()))
            .phone(request.getPhone())
            .role(Role.CUSTOMER)
            .build();
    userRepository.save(user);
    log.info("Registered: {}", request.getEmail());
    return "Registration successful";
}
```

### login()
```java
@Transactional
public String login(LoginRequest request) {
    User user = userRepository.findByEmail(request.getEmail())
            .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));

    if (user.isLocked()) {
        throw new UnauthorizedException("Account locked after 3 failed attempts");
    }

    try {
        authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );
    } catch (BadCredentialsException e) {
        user.setFailedLogins(user.getFailedLogins() + 1);
        if (user.getFailedLogins() >= 3) {
            user.setLocked(true);
        }
        userRepository.save(user);
        throw new UnauthorizedException("Invalid credentials");
    }

    // Reset counter on success
    user.setFailedLogins(0);
    userRepository.save(user);

    String otp = String.valueOf(100000 + new SecureRandom().nextInt(900000));
    OtpToken otpToken = OtpToken.builder()
            .user(user)
            .otpCode(otp)
            .expiresAt(LocalDateTime.now().plusMinutes(5))
            .isUsed(false)
            .build();
    otpTokenRepository.save(otpToken);
    log.info("OTP for {} : {}", request.getEmail(), otp); // check terminal for OTP in dev
    return "OTP sent to " + request.getEmail();
}
```

### verifyOtp()
```java
@Transactional
public LoginResponse verifyOtp(OtpVerifyRequest request) {
    User user = userRepository.findByEmail(request.getEmail())
            .orElseThrow(() -> new UnauthorizedException("User not found"));

    OtpToken otp = otpTokenRepository
            .findTopByUserAndIsUsedFalseAndExpiresAtAfterOrderByCreatedAtDesc(user, LocalDateTime.now())
            .orElseThrow(() -> new OtpExpiredException());

    if (!otp.getOtpCode().equals(request.getOtpCode())) {
        throw new UnauthorizedException("Invalid OTP");
    }

    otp.setIsUsed(true);
    otpTokenRepository.save(otp);

    String token = jwtTokenProvider.generateToken(user.getEmail(), user.getRole().name());
    return LoginResponse.builder()
            .token(token)
            .name(user.getName())
            .email(user.getEmail())
            .role(user.getRole())
            .build();
}
```

**Imports to add at the top of AuthService.java:**
```java
import com.hdfc.banking.config.JwtTokenProvider;
import com.hdfc.banking.dto.request.LoginRequest;
import com.hdfc.banking.dto.request.OtpVerifyRequest;
import com.hdfc.banking.dto.request.RegisterRequest;
import com.hdfc.banking.dto.response.LoginResponse;
import com.hdfc.banking.entity.OtpToken;
import com.hdfc.banking.entity.User;
import com.hdfc.banking.enums.Role;
import com.hdfc.banking.exception.OtpExpiredException;
import com.hdfc.banking.exception.UnauthorizedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.transaction.annotation.Transactional;
import java.security.SecureRandom;
import java.time.LocalDateTime;
```

---

## Step 3 — Create `AuthController.java`

Create file: `controller/AuthController.java`

```java
package com.hdfc.banking.controller;

import com.hdfc.banking.dto.request.LoginRequest;
import com.hdfc.banking.dto.request.OtpVerifyRequest;
import com.hdfc.banking.dto.request.RegisterRequest;
import com.hdfc.banking.dto.response.LoginResponse;
import com.hdfc.banking.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<String> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<String> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<LoginResponse> verifyOtp(@Valid @RequestBody OtpVerifyRequest request) {
        return ResponseEntity.ok(authService.verifyOtp(request));
    }
}
```

---

## Step 4 — Verify `JwtTokenProvider.java` exists

Check that `config/JwtTokenProvider.java` has all 3 methods:
- `generateToken(String email, String role)` — returns JWT string
- `getEmailFromToken(String token)` — returns email from token
- `validateToken(String token)` — returns true/false

If the file is missing or empty, recreate it from the Day 4 guide.

---

## Step 5 — Compile & Test

```bash
# In terminal (use Java 21):
JAVA_HOME=/opt/homebrew/Cellar/openjdk@21/21.0.10/libexec/openjdk.jdk/Contents/Home ./mvnw clean compile
```

Should show: `BUILD SUCCESS`

---

## Step 6 — Test in Postman

### Register
```
POST http://localhost:8080/api/auth/register
Content-Type: application/json

{ "name": "Naga", "email": "naga@test.com", "password": "pass1234" }
→ 201 Created: "Registration successful"
```

### Login
```
POST http://localhost:8080/api/auth/login
Content-Type: application/json

{ "email": "naga@test.com", "password": "pass1234" }
→ 200 OK: "OTP sent to naga@test.com"
→ Check terminal logs for: "OTP for naga@test.com : 847291"
```

### Verify OTP → Get JWT
```
POST http://localhost:8080/api/auth/verify-otp
Content-Type: application/json

{ "email": "naga@test.com", "otpCode": "847291" }
→ 200 OK: { "token": "eyJhbG...", "name": "Naga", "email": "...", "role": "CUSTOMER" }
```

**Copy the token — you need it for all account/transaction endpoints.**

---

## After This Guide — Next Steps

Once these compile and Postman works:
- Create `AccountService.java` + `AccountController.java`
- Use `Authorization: Bearer <token>` header in Postman for all account endpoints
