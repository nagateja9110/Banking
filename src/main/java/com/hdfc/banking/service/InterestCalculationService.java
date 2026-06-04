package com.hdfc.banking.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

@Service
@Slf4j
@RequiredArgsConstructor
public class InterestCalculationService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    @Scheduled(cron = "0 0 0 1 * *")
    @Transactional
    public void calculateMonthlyInterest() {

        List<Account> accounts = accountRepository.findByAccountStatusAndAccountType(AccountStatus.ACTIVE,
                AccountType.SAVINGS);

        int count = 0;

        for (Account a : accounts) {

            if (a.getBalance().compareTo(BigDecimal.ZERO) <= 0)
                continue;

            BigDecimal annualRate = a.getInterestRate();
            BigDecimal monthlyInterest = a.getBalance().multiply(annualRate).divide(BigDecimal.valueOf(1200), 2,
                    RoundingMode.HALF_UP);

            a.setBalance(a.getBalance().add(monthlyInterest));
            accountRepository.save(a);

            Transaction transaction = Transaction.builder()
                    .toAccount(a)
                    .amount(monthlyInterest)
                    .transactionType(TransactionType.INTEREST)
                    .transactionStatus(TransactionStatus.SUCCESS)
                    .balanceAfter(a.getBalance())
                    .description("Monthly interest at " + annualRate + "% p.a.")
                    .build();

            transactionRepository.save(transaction);
            count++;

            log.info("Credited interest {} to account {}", monthlyInterest, a.getAccountNumber());

        }
        log.info("Interest calculation complete. Processed {} accounts.", count);
    }

}
