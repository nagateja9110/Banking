package com.hdfc.banking.service;

import com.hdfc.banking.dto.response.StatementResponse;
import com.hdfc.banking.dto.response.TransactionResponse;
import com.hdfc.banking.entity.Account;
import com.hdfc.banking.entity.Transaction;
import com.hdfc.banking.exception.AccountNotFoundException;
import com.hdfc.banking.exception.UnauthorizedException;
import com.hdfc.banking.repository.AccountRepository;
import com.hdfc.banking.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatementService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    public StatementResponse generateStatement(
            String accountNumber,
            LocalDate from,
            LocalDate to,
            String email) {

        // STEP 1: Validate account + ownership
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException(accountNumber));

        if (!account.getUser().getEmail().equals(email)) {
            throw new UnauthorizedException("This account does not belong to you");
        }

        // STEP 2: Convert LocalDate to LocalDateTime (start of day / end of day)
        LocalDateTime fromDateTime = from.atStartOfDay();
        LocalDateTime toDateTime = to.atTime(23, 59, 59);

        // STEP 3: Fetch transactions in date range
        List<Transaction> transactions = transactionRepository
                .findByAccountAndDateRange(account, fromDateTime, toDateTime);

        // STEP 4: Calculate totals
        BigDecimal totalCredits = BigDecimal.ZERO;
        BigDecimal totalDebits = BigDecimal.ZERO;

        for (Transaction t : transactions) {
            if (t.getToAccount() != null &&
                    t.getToAccount().getAccountNumber().equals(accountNumber)) {
                // This account RECEIVED money → credit
                totalCredits = totalCredits.add(t.getAmount());
            } else {
                // This account SENT money → debit
                totalDebits = totalDebits.add(t.getAmount());
            }
        }

        // STEP 5: Calculate opening balance
        // openingBalance = currentBalance + totalDebits - totalCredits
        BigDecimal closingBalance = account.getBalance();
        BigDecimal openingBalance = closingBalance.add(totalDebits).subtract(totalCredits);

        // STEP 6: Map transactions to response
        List<TransactionResponse> txnResponses = new ArrayList<>();
        for (Transaction t : transactions) {
            txnResponses.add(mapToResponse(t));
        }

        log.info("Statement generated for account {} from {} to {}", accountNumber, from, to);

        return StatementResponse.builder()
                .accountNumber(accountNumber)
                .accountHolderName(account.getUser().getName())
                .fromDate(from)
                .toDate(to)
                .openingBalance(openingBalance)
                .closingBalance(closingBalance)
                .totalCredits(totalCredits)
                .totalDebits(totalDebits)
                .totalTransactions(transactions.size())
                .transactions(txnResponses)
                .build();
    }

    private TransactionResponse mapToResponse(Transaction transaction) {
        return TransactionResponse.builder()
                .id(transaction.getId())
                .referenceNumber(transaction.getReferenceNumber())
                .amount(transaction.getAmount())
                .transactionType(transaction.getTransactionType())
                .transactionStatus(transaction.getTransactionStatus())
                .description(transaction.getDescription())
                .balanceAfter(transaction.getBalanceAfter())
                .createdAt(transaction.getCreatedAt())
                .build();
    }
}
