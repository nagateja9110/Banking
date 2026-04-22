# Week 1 Day 4 — JWT Security
## JSON Web Tokens + Spring Security Filter Chain

---

## What You Will Build Today

```
config/
  ├── SecurityConfig.java          ← REPLACE the temp one (allow-all → JWT-protected)
  └── JwtTokenProvider.java        ← generates + validates JWT tokens

security/
  ├── JwtAuthenticationFilter.java ← intercepts every HTTP request
  └── CustomUserDetailsService.java ← loads User from DB for Spring Security
```

**Time: ~4 hours (most complex day of Week 1)**

---

## Hour 1 — READ EVERYTHING FIRST

### WHY JWT? (Why not just sessions?)

**Session-based (old way):**
```
Client → Login → Server creates session (stores in memory) → gives client session ID
Client → Next request → sends session ID → Server looks it up in memory → responds

Problem: 1 million users = 1 million sessions in server RAM
Problem: 2 servers (horizontal scaling) — session on server 1, request goes to server 2 → LOGGED OUT!
```

**JWT (modern way):**
```
Client → Login → Server creates JWT (stores NOTHING) → gives client the token
Client → Next request → sends JWT → Server validates signature → responds

Benefit: Server stores NOTHING — stateless!
Benefit: Any server can validate the same JWT — works with 100 servers!
Benefit: Token contains user info (id, email, role) — no DB lookup needed per request
```

---

### WHAT is a JWT?

A JWT has 3 parts separated by dots:
```
eyJhbGciOiJIUzI1NiJ9   .   eyJzdWIiOiJ1c2VyQGdtYWlsLmNvbSIsInJvbGUiOiJDVVNUT01FUiJ9   .   signature
      HEADER                              PAYLOAD                                              SIGNATURE
   (algorithm)                      (user claims)                                         (tamper-proof)
```

**Decoded Payload:**
```json
{
  "sub": "user@gmail.com",    ← who this token is for (subject)
  "role": "CUSTOMER",         ← what permissions they have
  "iat": 1713456000,          ← issued at (Unix timestamp)
  "exp": 1713542400           ← expires at (24 hours later)
}
```

**The signature** = HMAC-SHA256(header + payload, secretKey)

- If someone modifies the payload (changes role to ADMIN), the signature won't match → REJECTED
- Only your server knows the secret key → only your server can create valid tokens

---

### HOW the JWT flow works in our banking app

```
STEP 1: User submits email + password → POST /api/auth/login
STEP 2: Server checks DB → correct? → sends OTP to email
STEP 3: User submits OTP → POST /api/auth/verify-otp
STEP 4: Server validates OTP → creates JWT → sends back to client

EVERY REQUEST AFTER:
Client sends: Authorization: Bearer eyJhbGci...
              ↑
              This header is the JWT

JwtAuthenticationFilter intercepts:
  1. Extract token from "Authorization" header
  2. Validate signature + expiry
  3. Extract email from token
  4. Load user from DB (just once, to set SecurityContext)
  5. Set authentication → Spring Security allows the request
  6. Controller runs
```

---

### WHY a Filter? What is OncePerRequestFilter?

Spring Security uses a **FilterChain** — a list of filters that every HTTP request passes through before reaching the controller:

```
HTTP Request
    ↓
[CorsFilter]
    ↓
[JwtAuthenticationFilter]  ← our filter goes here
    ↓
[UsernamePasswordAuthenticationFilter]
    ↓
[SecurityContextHolder]
    ↓
[Controller]
```

`OncePerRequestFilter` = guaranteed to run exactly once per request (not 2x for forwards/redirects).

---

### WHAT is SecurityContextHolder?

It's a thread-local storage that holds the currently logged-in user for the duration of ONE request:

```java
// Our filter sets this:
SecurityContextHolder.getContext().setAuthentication(auth);

// Any service or controller can read it:
String email = SecurityContextHolder.getContext().getAuthentication().getName();
```

Without setting this: Spring Security thinks no one is logged in → rejects every request.

---

## Hour 2 — CODE: JwtTokenProvider

Create file: `config/JwtTokenProvider.java`

```java
package com.hdfc.banking.config;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;

/**
 * @Component — Spring creates one instance of this and reuses it everywhere.
 * It holds the JWT secret key and knows how to create/validate tokens.
 *
 * Think of it as a JWT factory: give it a user email, it gives you a token.
 * Give it a token, it tells you if it's valid and who it belongs to.
 */
@Component
public class JwtTokenProvider {

    @Value("${jwt.secret}")
    private String jwtSecret;
    // Read from application.properties: jwt.secret=your_secret_key_here

    @Value("${jwt.expiration}")
    private long jwtExpirationMs;
    // e.g. 86400000 = 24 hours in milliseconds

    private Key signingKey;

    /**
     * WHY @PostConstruct?
     * @Value fields are injected AFTER the constructor runs.
     * If we set signingKey in constructor, jwtSecret is still null!
     * @PostConstruct runs AFTER all @Value fields are set → safe to use jwtSecret here.
     */
    @PostConstruct
    public void init() {
        this.signingKey = Keys.hmacShaKeyFor(jwtSecret.getBytes());
        // Keys.hmacShaKeyFor converts your secret string into a cryptographic Key object
        // HMAC-SHA256 requires at least 256-bit (32 character) secret key
    }

    /**
     * Creates a JWT token for the given email and role.
     * Called after OTP verification is successful.
     */
    public String generateToken(String email, String role) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + jwtExpirationMs);

        return Jwts.builder()
            .setSubject(email)           // "sub" claim — who the token is for
            .claim("role", role)         // custom claim — user's role (CUSTOMER/ADMIN)
            .setIssuedAt(now)            // "iat" — when token was created
            .setExpiration(expiry)       // "exp" — when token expires (24h from now)
            .signWith(signingKey)        // signs with HMAC-SHA256
            .compact();                  // builds the final JWT string
    }

    /**
     * Extracts the email (subject) from a valid token.
     * Used in JwtAuthenticationFilter to know WHO is making the request.
     */
    public String getEmailFromToken(String token) {
        return Jwts.parserBuilder()
            .setSigningKey(signingKey)
            .build()
            .parseClaimsJws(token)
            .getBody()
            .getSubject();       // returns the "sub" field = email
    }

    /**
     * Returns true if token signature is valid AND not expired.
     * Returns false (or throws) if token is tampered or expired.
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                .setSigningKey(signingKey)
                .build()
                .parseClaimsJws(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            // JwtException covers: expired, invalid signature, malformed
            return false;
        }
    }
}
```

---

## Hour 2 Continued — CODE: CustomUserDetailsService

Create file: `security/CustomUserDetailsService.java`

```java
package com.hdfc.banking.security;

import com.hdfc.banking.entity.User;
import com.hdfc.banking.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * WHY this class?
 * Spring Security doesn't know about OUR User entity.
 * It works with its own UserDetails interface.
 *
 * This class is the BRIDGE:
 *   Our User entity → Spring Security's UserDetails
 *
 * Spring calls loadUserByUsername() during authentication.
 * We load the user from DB and return a Spring-compatible UserDetails object.
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    /**
     * Called by Spring Security when it needs to authenticate a user.
     * "username" in Spring Security terminology = email in our app.
     *
     * WHY UsernameNotFoundException?
     * Spring Security expects this specific exception when user isn't found.
     * It's different from our AccountNotFoundException — it's Spring's contract.
     */
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));

        return new org.springframework.security.core.userdetails.User(
            user.getEmail(),
            user.getPassword(),
            List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
            // ROLE_CUSTOMER or ROLE_ADMIN
            // Spring Security requires "ROLE_" prefix for hasRole() checks
        );
    }
}
```

---

## Hour 3 — CODE: JwtAuthenticationFilter

Create file: `security/JwtAuthenticationFilter.java`

```java
package com.hdfc.banking.security;

import com.hdfc.banking.config.JwtTokenProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Runs on EVERY HTTP request (before any controller).
 *
 * Job:
 * 1. Look for "Authorization: Bearer <token>" header
 * 2. If found: validate the token
 * 3. If valid: load user from DB, set SecurityContext
 * 4. If missing/invalid: do nothing (request continues, Spring rejects if protected endpoint)
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final CustomUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        // STEP 1: Extract token from header
        String token = extractToken(request);

        // STEP 2: If token exists and is valid
        if (token != null && jwtTokenProvider.validateToken(token)) {

            // STEP 3: Get email from token (no DB call yet)
            String email = jwtTokenProvider.getEmailFromToken(token);

            // STEP 4: Load full UserDetails from DB (to get roles/authorities)
            UserDetails userDetails = userDetailsService.loadUserByUsername(email);

            // STEP 5: Create authentication object
            UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                    userDetails,
                    null,                           // credentials = null (already validated)
                    userDetails.getAuthorities()    // roles: [ROLE_CUSTOMER] or [ROLE_ADMIN]
                );
            authentication.setDetails(
                new WebAuthenticationDetailsSource().buildDetails(request)
            );

            // STEP 6: Set in SecurityContext — THIS is what marks the user as "logged in"
            SecurityContextHolder.getContext().setAuthentication(authentication);

            // WHY clear after request? Thread pooling — same thread handles next request
            // Spring actually clears this automatically after each request, but being explicit
        }

        // STEP 7: Always continue the chain (let Spring Security decide if request is allowed)
        filterChain.doFilter(request, response);
    }

    /**
     * Extracts JWT from "Authorization: Bearer <token>" header.
     * Returns null if header is missing or doesn't start with "Bearer ".
     */
    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7); // Remove "Bearer " prefix (7 characters)
        }
        return null;
    }
}
```

---

## Hour 4 — CODE: Replace SecurityConfig

**Delete the old SecurityConfig content and replace completely:**

```java
package com.hdfc.banking.config;

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
}
```

---

## Hour 4 — Update application.properties

Add these 2 lines to `application.properties`:

```properties
# JWT Configuration
jwt.secret=hdfc-banking-super-secret-key-must-be-at-least-32-chars-long!!
jwt.expiration=86400000
# 86400000 ms = 24 hours
```

> ⚠️ Secret key must be at least 32 characters (256 bits) for HMAC-SHA256.

---

## Verify

```bash
Ctrl+C  →  ./mvnw spring-boot:run
```

**Test in Postman:**

```
GET http://localhost:8080/api/accounts/test
→ Expected: 401 Unauthorized (no token)

GET http://localhost:8080/actuator/health
→ Expected: 200 OK (public endpoint)
```

You should see 401 for protected endpoints and 200 for public ones.

---

## Checkpoint Questions (Answer Before Day 5)

1. What is the difference between JWT and session-based authentication? Why does JWT work better for horizontal scaling?
2. A JWT has 3 parts. What is in each part?
3. WHY does `@PostConstruct` exist? Why can't we initialize `signingKey` in the constructor?
4. What does `SessionCreationPolicy.STATELESS` mean? What problem does it solve?
5. WHY do we store "ROLE_CUSTOMER" (with prefix) instead of just "CUSTOMER"?
6. In `JwtAuthenticationFilter`, what happens if the JWT is expired? What does the client receive?
7. WHY is CSRF disabled? When WOULD you need CSRF protection?
8. What is `@EnableMethodSecurity` for? Give an example of where you'd use it.

---

## Day 5 Preview — Service Layer (AuthService + AccountService)

Tomorrow:
- `AuthService` — register user, login (password check), verify OTP, generate JWT
- `AccountService` — create account, get balance, deposit, withdraw
- `AuthController` — POST /api/auth/register, /login, /verify-otp
- `AccountController` — GET /api/accounts, POST /api/accounts/create

After Day 5, you can make your **first real end-to-end API call** — register → login → OTP → get JWT → view account!
