# Week 4, Day 15 — Rate Limiting (Simple Controller-Based Approach)

> **Goal:** Block users who make too many requests in a short time.
> Using a simple service approach — no filters, no complex Spring config.

---

## The Simple Idea

```
Keep track: "How many times did this IP call login in the last 1 minute?"
If count >= 5 → throw exception → controller returns 429
```

No dependency needed. Just a Java `Map` + timestamp tracking.

---

## Where to Apply

| Endpoint | Limit |
|---|---|
| `POST /api/auth/login` | 5 per minute |
| `POST /api/auth/register` | 3 per minute |
| `POST /api/transactions/transfer` | 10 per minute |

---

## Step 1 — Create `RateLimiterService.java`

Create file: `service/RateLimiterService.java`

```java
package com.hdfc.banking.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RateLimiterService {

    // Stores request timestamps per key (IP + endpoint)
    // Deque = a list where we add to end and remove from front
    private final Map<String, Deque<Long>> requestLog = new ConcurrentHashMap<>();

    /**
     * Call this at the START of any controller method you want to protect.
     *
     * @param key       unique key — e.g., IP address or user email + endpoint name
     * @param maxCalls  how many calls are allowed
     * @param windowSec time window in seconds (e.g., 60 = 1 minute)
     */
    public void check(String key, int maxCalls, int windowSec) {
        long now = Instant.now().getEpochSecond();
        long windowStart = now - windowSec;

        // Get or create the request list for this key
        requestLog.putIfAbsent(key, new ArrayDeque<>());
        Deque<Long> timestamps = requestLog.get(key);

        // Remove timestamps that are OLDER than the window
        while (!timestamps.isEmpty() && timestamps.peekFirst() < windowStart) {
            timestamps.pollFirst();
        }

        // Check if limit is exceeded
        if (timestamps.size() >= maxCalls) {
            throw new RuntimeException(
                "Rate limit exceeded. Max " + maxCalls + " requests per " + windowSec + " seconds."
            );
        }

        // Record this request
        timestamps.addLast(now);
    }
}
```

---

## Step 2 — Create `TooManyRequestsException.java`

Create file: `exception/TooManyRequestsException.java`

```java
package com.hdfc.banking.exception;

public class TooManyRequestsException extends RuntimeException {
    public TooManyRequestsException(String message) {
        super(message);
    }
}
```

---

## Step 3 — Handle it in `GlobalExceptionHandler.java`

Open `exception/GlobalExceptionHandler.java` and add:

```java
@ExceptionHandler(TooManyRequestsException.class)
public ResponseEntity<ErrorResponse> handleTooManyRequests(TooManyRequestsException ex) {
    return buildResponse(HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests", ex.getMessage());
}
```

Add import:
```java
import org.springframework.http.HttpStatus;  // already imported
```

Also update `HttpStatus` — add 429 constant (already exists in Spring as `HttpStatus.TOO_MANY_REQUESTS`).

---

## Step 4 — Update `RateLimiterService` to throw the right exception

Go back to `RateLimiterService.java` and change the `RuntimeException` to your new exception:

```java
// Change this line:
throw new RuntimeException("Rate limit exceeded...");

// To this:
throw new com.hdfc.banking.exception.TooManyRequestsException(
    "Rate limit exceeded. Max " + maxCalls + " requests per " + windowSec + " seconds."
);
```

Or add the import at the top:
```java
import com.hdfc.banking.exception.TooManyRequestsException;
// then use:
throw new TooManyRequestsException(
    "Rate limit exceeded. Max " + maxCalls + " requests per " + windowSec + " seconds."
);
```

---

## Step 5 — Use in `AuthController.java`

Open `controller/AuthController.java`:

### 5a — Inject RateLimiterService:
```java
private final RateLimiterService rateLimiterService;
```

### 5b — How to get the client IP (add this helper method at the bottom):
```java
private String getClientIp(HttpServletRequest request) {
    String forwarded = request.getHeader("X-Forwarded-For");
    if (forwarded != null && !forwarded.isEmpty()) {
        return forwarded.split(",")[0].trim();
    }
    return request.getRemoteAddr();
}
```

Add import: `import jakarta.servlet.http.HttpServletRequest;`

### 5c — Add rate limiting to `login()`:
```java
@PostMapping("/login")
public ResponseEntity<String> login(
        @Valid @RequestBody LoginRequest request,
        HttpServletRequest httpRequest) {          // ← add this parameter

    // Rate limit: 5 login attempts per 60 seconds per IP
    String ip = getClientIp(httpRequest);
    rateLimiterService.check(ip + ":login", 5, 60);

    return ResponseEntity.ok(authService.login(request));
}
```

### 5d — Add rate limiting to `register()`:
```java
@PostMapping("/register")
public ResponseEntity<String> register(
        @Valid @RequestBody RegisterRequest request,
        HttpServletRequest httpRequest) {          // ← add this parameter

    // Rate limit: 3 register attempts per 60 seconds per IP
    String ip = getClientIp(httpRequest);
    rateLimiterService.check(ip + ":register", 3, 60);

    return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
}
```

---

## Step 6 — Use in `TransactionController.java`

Open `controller/TransactionController.java`:

### Inject RateLimiterService:
```java
private final RateLimiterService rateLimiterService;
```

### Add to `transfer()`:
```java
@PostMapping("/transfer")
public ResponseEntity<TransactionResponse> transfer(
        @Valid @RequestBody TransferRequest request,
        @AuthenticationPrincipal UserDetails userDetails,
        HttpServletRequest httpRequest) {          // ← add this parameter

    // Rate limit: 10 transfers per 60 seconds per user email
    rateLimiterService.check(userDetails.getUsername() + ":transfer", 10, 60);

    return ResponseEntity.ok(transactionService.transfer(request, userDetails.getUsername()));
}
```

Add import: `import jakarta.servlet.http.HttpServletRequest;`

---

## Step 7 — Test in Thunder Client

### Test login rate limit:

Send 6 POST requests quickly to `/api/auth/login`:

```
Request 1 → 200 OK ✅
Request 2 → 200 OK ✅
Request 3 → 200 OK ✅
Request 4 → 200 OK ✅
Request 5 → 200 OK ✅
Request 6 → 429 Too Many Requests ❌
```

**Response on 6th request:**
```json
{
  "status": 429,
  "error": "Too Many Requests",
  "message": "Rate limit exceeded. Max 5 requests per 60 seconds."
}
```

After 1 minute, try again → 200 OK (window resets).

---

## How it works (visual):

```
Timeline (1 minute window):
─────────────────────────────────────────────────
t=0s:  request 1 logged  [1]
t=5s:  request 2 logged  [1, 2]
t=10s: request 3 logged  [1, 2, 3]
t=15s: request 4 logged  [1, 2, 3, 4]
t=20s: request 5 logged  [1, 2, 3, 4, 5]
t=25s: request 6 → size=5, maxCalls=5 → BLOCKED ❌

t=61s: request 1 is now older than 60s → removed
       [2, 3, 4, 5] → size=4 → ALLOWED ✅
```

---

## Step 8 — Git Commit

```bash
git add .
git commit -m "Week4-Day15: Controller-based rate limiting"
git push
```

---

## Key Concepts Learned

| Concept | Explanation |
|---|---|
| `ConcurrentHashMap` | Thread-safe map (handles multiple requests simultaneously) |
| `Deque<Long>` | List of timestamps — add to end, remove from front |
| `Instant.now().getEpochSecond()` | Current time in seconds |
| `peekFirst()` | Look at oldest timestamp without removing |
| `pollFirst()` | Remove oldest timestamp |
| Sliding window | The time window "slides" — old requests fall out automatically |
| `HttpServletRequest` | Spring gives access to HTTP request details (IP, headers) |

---

## After This → Day 16 — Docker + MySQL (Production Deployment)
