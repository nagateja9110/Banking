# Week 4, Day 13 — Integration Tests

> **Goal:** Write integration tests that test the FULL flow — HTTP request → Controller → Service → Database.
> Unlike unit tests (fake DB), integration tests use a REAL Spring Boot app + real H2 database.

---

## Unit Test vs Integration Test

| | Unit Test (Day 12) | Integration Test (Day 13) |
|---|---|---|
| Spring starts? | ❌ No | ✅ Yes — full app |
| Database? | ❌ Fake (Mock) | ✅ Real H2 in-memory |
| HTTP requests? | ❌ No | ✅ Yes — via MockMvc |
| Speed | ⚡ Very fast (ms) | 🐢 Slower (seconds) |
| Tests | Business logic only | Entire flow end-to-end |

---

## Key Annotations

| Annotation | Meaning |
|---|---|
| `@SpringBootTest` | Starts the full Spring app for testing |
| `@AutoConfigureTestDatabase` | Uses H2 instead of real MySQL |
| `@AutoConfigureMockMvc` | Sets up MockMvc (fake HTTP client) |
| `@Transactional` | Rolls back DB after EACH test (clean state) |
| `mockMvc.perform(...)` | Send a fake HTTP request |
| `andExpect(status().isOk())` | Assert the HTTP status code |
| `andExpect(jsonPath("$.field")...)` | Assert a field in the JSON response |

---

## Step 1 — Create `AuthControllerIntegrationTest.java`

Create file: `src/test/java/com/hdfc/banking/controller/AuthControllerIntegrationTest.java`

```java
package com.hdfc.banking.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hdfc.banking.dto.request.RegisterRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;   // converts Java objects to JSON
```

---

### Test 1 — Register succeeds

```java
    @Test
    void register_shouldReturn201_whenValidRequest() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setName("Naga");
        request.setEmail("naga@test.com");
        request.setPassword("pass1234");
        request.setPhone("9876543210");

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))   // converts to JSON
                .andExpect(status().isCreated())                       // 201
                .andExpect(content().string("Registration successful"));
    }
```

---

### Test 2 — Register with duplicate email → 409

```java
    @Test
    void register_shouldReturn409_whenEmailAlreadyExists() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setName("Naga");
        request.setEmail("duplicate@test.com");
        request.setPassword("pass1234");
        request.setPhone("9876543210");

        // First registration — should succeed
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Second registration with SAME email — should fail
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())    // 409 Conflict
                .andExpect(jsonPath("$.error").value("Conflict"));
    }
```

---

### Test 3 — Register with missing fields → 400

```java
    @Test
    void register_shouldReturn400_whenFieldsMissing() throws Exception {
        // Empty body — all fields missing
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest());   // 400
    }
}  // end of class
```

---

## Step 2 — Create Helper: `TestHelper.java`

Since multiple tests need a logged-in user (JWT token), create a helper:

Create file: `src/test/java/com/hdfc/banking/TestHelper.java`

```java
package com.hdfc.banking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hdfc.banking.dto.request.LoginRequest;
import com.hdfc.banking.dto.request.RegisterRequest;
import com.hdfc.banking.entity.OtpToken;
import com.hdfc.banking.repository.OtpTokenRepository;
import com.hdfc.banking.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@Component
public class TestHelper {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private OtpTokenRepository otpTokenRepository;
    @Autowired private UserRepository userRepository;

    // Register + Login + OTP verify — returns JWT token
    public String getJwtToken(String email, String password) throws Exception {

        // Step 1: Register
        RegisterRequest reg = new RegisterRequest();
        reg.setName("Test User");
        reg.setEmail(email);
        reg.setPassword(password);
        reg.setPhone("9876543210");

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reg)));

        // Step 2: Login
        LoginRequest login = new LoginRequest();
        login.setEmail(email);
        login.setPassword(password);

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(login)));

        // Step 3: Get OTP from DB (skip email, read directly)
        OtpToken otp = otpTokenRepository.findAll()
                .stream()
                .filter(o -> o.getUser().getEmail().equals(email))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("OTP not found in DB"));

        // Step 4: Verify OTP → get JWT
        String otpBody = "{\"email\":\"" + email + "\",\"otp\":\"" + otp.getOtp() + "\"}";

        MvcResult result = mockMvc.perform(post("/api/auth/verify-otp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(otpBody))
                .andReturn();

        // Extract token from response JSON
        String responseBody = result.getResponse().getContentAsString();
        return objectMapper.readTree(responseBody).get("accessToken").asText();
    }
}
```

---

## Step 3 — Create `AccountControllerIntegrationTest.java`

Create file: `src/test/java/com/hdfc/banking/controller/AccountControllerIntegrationTest.java`

```java
package com.hdfc.banking.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hdfc.banking.TestHelper;
import com.hdfc.banking.dto.request.CreateAccountRequest;
import com.hdfc.banking.enums.AccountType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class AccountControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TestHelper testHelper;

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        // Get a fresh JWT token before each test
        token = testHelper.getJwtToken("naga@test.com", "pass1234");
    }

    @Test
    void createAccount_shouldReturn201_withValidToken() throws Exception {
        CreateAccountRequest request = new CreateAccountRequest();
        request.setAccountType(AccountType.SAVINGS);

        mockMvc.perform(post("/api/accounts/create")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountNumber").isNotEmpty())
                .andExpect(jsonPath("$.accountStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.balance").value(0.0));
    }

    @Test
    void createAccount_shouldReturn401_withoutToken() throws Exception {
        CreateAccountRequest request = new CreateAccountRequest();
        request.setAccountType(AccountType.SAVINGS);

        // No Authorization header
        mockMvc.perform(post("/api/accounts/create")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());   // 401
    }

    @Test
    void getMyAccounts_shouldReturnEmptyList_whenNoAccounts() throws Exception {
        mockMvc.perform(get("/api/accounts/my")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());   // no accounts yet
    }
}
```

---

## Step 4 — Run All Tests

```bash
./mvnw test
```

Expected:
```
Tests run: 9, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

To run only integration tests:
```bash
./mvnw test -pl . -Dtest="*IntegrationTest"
```

To run only unit tests:
```bash
./mvnw test -pl . -Dtest="*ServiceTest"
```

---

## Understanding `@DirtiesContext`

```java
// This resets the entire Spring context + H2 database before each test
// So test data from Test 1 doesn't affect Test 2
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
```

Without this, if Test 1 registers `naga@test.com`, Test 2 would fail with "Email already exists".

---

## Understanding `jsonPath`

```java
// JSON response: {"accountNumber": "HDFC12345678", "balance": 0.0, "accountStatus": "ACTIVE"}

.andExpect(jsonPath("$.accountNumber").isNotEmpty())     // field exists and not empty
.andExpect(jsonPath("$.balance").value(0.0))             // balance equals 0.0
.andExpect(jsonPath("$.accountStatus").value("ACTIVE"))  // status equals ACTIVE
.andExpect(jsonPath("$").isArray())                      // root is a list
.andExpect(jsonPath("$[0].accountNumber").isNotEmpty())  // first item's accountNumber
```

---

## Step 5 — Git Commit

```bash
git add .
git commit -m "Week4-Day13: Integration tests for Auth + Account controllers"
git push
```

---

## Key Concepts Learned Today

| Concept | Explanation |
|---|---|
| `@SpringBootTest` | Starts real Spring context for testing |
| `MockMvc` | Simulates HTTP requests — no real server needed |
| `objectMapper.writeValueAsString()` | Converts Java object → JSON string |
| `jsonPath("$.field")` | Checks a field in the JSON response |
| `@DirtiesContext` | Resets app state between tests |
| `@BeforeEach` | Runs before each test — sets up fresh token |
| `TestHelper` | Shared utility class — avoids repeating register/login/OTP code |

---

## After This → Day 14 — Spring AOP: Audit Logging
