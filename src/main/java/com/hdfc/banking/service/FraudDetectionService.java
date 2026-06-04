package com.hdfc.banking.service;

import com.hdfc.banking.entity.Account;
import com.hdfc.banking.entity.FraudAlert;
import com.hdfc.banking.entity.Transaction;
import com.hdfc.banking.enums.FraudAlertStatus;
import com.hdfc.banking.repository.FraudAlertRepository;
import com.hdfc.banking.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class FraudDetectionService {

    private final FraudAlertRepository fraudAlertRepository;
    private final TransactionRepository transactionRepository;

    private static final BigDecimal LARGE_AMOUNT_THRESHOLD = new BigDecimal("100000");
    private static final int VELOCITY_LIMIT = 3;
    private static final int VELOCITY_WINDOW_MINUTES = 10;

    public void checkAndFlag(Transaction transaction, Account fromAccount) {

        StringBuilder reason = new StringBuilder();
        BigDecimal riskScore = BigDecimal.ZERO;


        if (transaction.getAmount().compareTo(LARGE_AMOUNT_THRESHOLD) > 0) {
            reason.append("Large amount transfer: ₹").append(transaction.getAmount()).append(". ");
            riskScore = riskScore.max(new BigDecimal("0.9"));
        }


        int hour = LocalDateTime.now().getHour();
        if (hour >= 0 && hour < 4) {
            reason.append("Transfer made at unusual hour (").append(hour).append(":00). ");
            riskScore = riskScore.max(new BigDecimal("0.7"));
        }


        LocalDateTime since = LocalDateTime.now().minusMinutes(VELOCITY_WINDOW_MINUTES);
        long recentCount = transactionRepository.countRecentTransfers(fromAccount, since);

        if (recentCount >= VELOCITY_LIMIT) {
            reason.append("High velocity: ").append(recentCount)
                    .append(" transfers in last ").append(VELOCITY_WINDOW_MINUTES).append(" minutes. ");
            riskScore = riskScore.max(new BigDecimal("0.8"));
        }


        if (riskScore.compareTo(BigDecimal.ZERO) > 0) {
            FraudAlert alert = FraudAlert.builder()
                    .transaction(transaction)
                    .reason(reason.toString().trim())
                    .riskScore(riskScore)
                    .status(FraudAlertStatus.OPEN)
                    .build();

            fraudAlertRepository.save(alert);
            log.warn("FRAUD ALERT created for transaction {} — reason: {}", transaction.getId(), reason);
        }
    }
}
