# Week 3, Day 10 — Interest Calculation Service (Scheduled Job)

> **Goal:** Automatically credit monthly interest to all active savings accounts.
> Runs on a schedule (e.g., every month on the 1st day).
>
> Real-world: HDFC credits savings interest every quarter automatically.

---

## What You're Building

```
Scheduled Job → runs every month
  → finds all ACTIVE SAVINGS accounts
  → calculates interest = balance × (interestRate / 100 / 12)
  → credits the interest to each account
  → saves an INTEREST transaction record
```

No API endpoint — this runs automatically in the background.

---

## Key Concept: `@Scheduled`

```java
@Scheduled(cron = "0 0 0 1 * *")   // runs at midnight on 1st of every month
public void calculateInterest() {
    // your logic
}
```

| Part | Meaning |
|---|---|
| First `0` | Second = 0 |
| Second `0` | Minute = 0 |
| Third `0` | Hour = 0 (midnight) |
| `1` | Day of month = 1st |
| `*` | Every month |
| `*` | Every day of week |

For **testing** (runs every 60 seconds):
```java
@Scheduled(fixedDelay = 60000)
```

---

## Step 1 — Enable Scheduling

Open `BankingApplication.java` and add `@EnableScheduling`:

```java
// ❌ Current
@SpringBootApplication
@EnableJpaAuditing
public class BankingApplication {

// ✅ Add @EnableScheduling
@SpringBootApplication
@EnableJpaAuditing
@EnableScheduling           // ← add this
public class BankingApplication {
```

Import:
```java
import org.springframework.scheduling.annotation.EnableScheduling;
```

---

## Step 2 — Add Query to `AccountRepository.java`

Open `repository/AccountRepository.java` and add:

```java
import com.hdfc.banking.enums.AccountStatus;
import com.hdfc.banking.enums.AccountType;
import java.util.List;

// Find all active savings accounts
List<Account> findByAccountStatusAndAccountType(AccountStatus status, AccountType type);
```

---

## Step 3 — Create `InterestCalculationService.java`

Create file: `service/InterestCalculationService.java`

### Class setup:
```java
@Slf4j
@Service
@RequiredArgsConstructor
public class InterestCalculationService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
}
```

---

### Scheduled Method:

```java
@Scheduled(cron = "0 0 0 1 * *")   // runs on 1st of every month at midnight
@Transactional
public void calculateMonthlyInterest() {
    log.info("Starting monthly interest calculation...");

    // STEP 1: Get all active savings accounts
    List<Account> savingsAccounts = accountRepository
            .findByAccountStatusAndAccountType(AccountStatus.ACTIVE, AccountType.SAVINGS);

    int count = 0;

    for (Account account : savingsAccounts) {

        // STEP 2: Skip accounts with zero balance
        if (account.getBalance().compareTo(BigDecimal.ZERO) <= 0) {
            continue;
        }

        // STEP 3: Calculate monthly interest
        // Formula: interest = balance × (annualRate / 100) / 12
        BigDecimal annualRate = account.getInterestRate();
        BigDecimal monthlyInterest = account.getBalance()
                .multiply(annualRate)
                .divide(BigDecimal.valueOf(1200), 2, RoundingMode.HALF_UP);

        // STEP 4: Credit interest to account
        account.setBalance(account.getBalance().add(monthlyInterest));
        accountRepository.save(account);

        // STEP 5: Record the interest transaction
        Transaction transaction = Transaction.builder()
                .toAccount(account)
                .amount(monthlyInterest)
                .transactionType(TransactionType.INTEREST)
                .transactionStatus(TransactionStatus.SUCCESS)
                .balanceAfter(account.getBalance())
                .description("Monthly interest at " + annualRate + "% p.a.")
                .build();

        transactionRepository.save(transaction);
        count++;

        log.info("Credited interest {} to account {}", monthlyInterest, account.getAccountNumber());
    }

    log.info("Interest calculation complete. Processed {} accounts.", count);
}
```

### Imports needed:
```java
import com.hdfc.banking.entity.Account;
import com.hdfc.banking.entity.Transaction;
import com.hdfc.banking.enums.AccountStatus;
import com.hdfc.banking.enums.AccountType;
import com.hdfc.banking.enums.TransactionStatus;
import com.hdfc.banking.enums.TransactionType;
import com.hdfc.banking.repository.AccountRepository;
import com.hdfc.banking.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
```

---

## Step 4 — Test the Job (trigger manually for testing)

Since `@Scheduled(cron = "0 0 0 1 * *")` runs monthly, create a **test endpoint** to trigger it manually:

Open `controller/TestController.java` and add:

```java
private final InterestCalculationService interestCalculationService;

@PostMapping("/trigger-interest")
public ResponseEntity<String> triggerInterest() {
    interestCalculationService.calculateMonthlyInterest();
    return ResponseEntity.ok("Interest calculation triggered");
}
```

Then call:
```
POST http://localhost:8080/test/trigger-interest
```

Check the terminal logs — you should see:
```
Starting monthly interest calculation...
Credited interest 145.83 to account HDFC12345678
Interest calculation complete. Processed 1 accounts.
```

---

## Step 5 — Verify in H2 Console

Open: http://localhost:8080/h2-console

```sql
-- Check balance increased
SELECT account_number, balance, interest_rate FROM accounts;

-- Check interest transaction was recorded
SELECT * FROM transactions WHERE transaction_type = 'INTEREST';
```

---

## Step 6 — Understand the Formula

```
Annual rate = 3.5%
Monthly rate = 3.5 / 12 = 0.2917%
Balance = ₹50,000

Monthly interest = 50,000 × (3.5 / 1200) = ₹145.83
```

In code:
```java
monthlyInterest = balance × annualRate / 1200
```

> Why 1200? Because `annualRate` is 3.5 (not 0.035), so divide by 100 for percentage,
> then divide by 12 for monthly → 100 × 12 = 1200.

---

## Step 7 — Git Commit

```bash
git add .
git commit -m "Week3-Day10: Interest Calculation scheduled job"
git push
```

---

## Key Concepts Learned Today

| Concept | Explanation |
|---|---|
| `@EnableScheduling` | Must add to main class to enable scheduled jobs |
| `@Scheduled(cron=...)` | Runs method on a time schedule |
| Cron expression | `"0 0 0 1 * *"` = midnight on 1st of every month |
| `RoundingMode.HALF_UP` | Standard bank rounding (₹145.833 → ₹145.83) |
| Interest formula | `balance × rate / 1200` (for monthly) |
| `fixedDelay = 60000` | Run every 60 seconds (for testing only) |

---

## After This → Next: Week 3, Day 11 — Fraud Detection (flag suspicious transactions)
