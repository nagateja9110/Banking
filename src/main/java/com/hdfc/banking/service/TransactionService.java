package com.hdfc.banking.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.hdfc.banking.dto.request.TransferRequest;
import com.hdfc.banking.dto.response.TransactionResponse;
import com.hdfc.banking.entity.Account;
import com.hdfc.banking.entity.Transaction;
import com.hdfc.banking.enums.AccountStatus;
import com.hdfc.banking.enums.TransactionStatus;
import com.hdfc.banking.enums.TransactionType;
import com.hdfc.banking.exception.AccountFrozenException;
import com.hdfc.banking.exception.AccountNotFoundException;
import com.hdfc.banking.exception.InsufficientFundsException;
import com.hdfc.banking.exception.UnauthorizedException;
import com.hdfc.banking.repository.AccountRepository;
import com.hdfc.banking.repository.TransactionRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final FraudDetectionService fraudDetectionService;

    @Transactional
    public TransactionResponse deposit(String accountNumber, BigDecimal amount, String email) {

        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException(accountNumber));

        if (account.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new AccountFrozenException("Account is Not Active");
        }

        account.setBalance(account.getBalance().add(amount));
        accountRepository.save(account);

        Transaction transaction = Transaction.builder()
                .toAccount(account)
                .amount(amount)
                .transactionType(TransactionType.DEPOSIT)
                .transactionStatus(TransactionStatus.SUCCESS)
                .balanceAfter(account.getBalance())
                .description("Deposit to " + accountNumber)
                .build();
        transactionRepository.save(transaction);
        log.info("Deposit {} to account {}", amount, accountNumber);
        return mapToResponse(transaction);

    }

    @Transactional
    public TransactionResponse withdraw(String accountNumber, BigDecimal amount, String email) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException(accountNumber));

        if (!account.getUser().getEmail().equals(email)) {
            throw new UnauthorizedException("This account is nor belongs to You");
        }
        if (account.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new AccountFrozenException("Account is Frozen");
        }
        if (account.getBalance().compareTo(amount) < 0) {
            throw new InsufficientFundsException(account.getBalance(), amount);
        }

        account.setBalance(account.getBalance().subtract(amount));
        accountRepository.save(account);

        Transaction transaction = Transaction.builder()
                .fromAccount(account)
                .amount(amount)
                .transactionType(TransactionType.WITHDRAWAL)
                .transactionStatus(TransactionStatus.SUCCESS)
                .balanceAfter(account.getBalance())
                .description("Withdrawal from " + accountNumber)
                .build();

        transactionRepository.save(transaction);
        return mapToResponse(transaction);
    }

    @Transactional
    public TransactionResponse transfer(TransferRequest request, String email) {

        String referenceNumber = "TXN" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();

        Account fromAccount = accountRepository.findByAccountNumber(request.getFromAccountNumber())
                .orElseThrow(() -> new AccountNotFoundException(request.getFromAccountNumber()));
        Account toAccount = accountRepository.findByAccountNumber(request.getToAccountNumber())
                .orElseThrow(() -> new AccountNotFoundException(request.getToAccountNumber()));

        if (!fromAccount.getUser().getEmail().equals(email)) {
            throw new UnauthorizedException("Source account does not belong to you");
        }

        if (request.getFromAccountNumber().equals(request.getToAccountNumber())) {
            throw new RuntimeException("Cannot transfer to the same account");
        }

        if (fromAccount.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new AccountFrozenException("Source account is frozen or closed");
        }

        if (toAccount.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new AccountFrozenException("Destination account is frozen or closed");
        }

        if (fromAccount.getBalance().compareTo(request.getAmount()) < 0) {
            throw new InsufficientFundsException(fromAccount.getBalance(), request.getAmount());
        }

        fromAccount.setBalance(fromAccount.getBalance().subtract(request.getAmount()));
        toAccount.setBalance(toAccount.getBalance().add(request.getAmount()));
        accountRepository.save(fromAccount);
        accountRepository.save(toAccount);

        Transaction transaction = Transaction.builder()
                .fromAccount(fromAccount)
                .toAccount(toAccount)
                .description(request.getDescription())
                .amount(request.getAmount())
                .balanceAfter(fromAccount.getBalance())
                .transactionStatus(TransactionStatus.SUCCESS)
                .transactionType(TransactionType.TRANSFER)
                .referenceNumber(referenceNumber)
                .build();
        transactionRepository.save(transaction);

        log.info("Transfer {} from {} to {}", request.getAmount(),
                request.getFromAccountNumber(), request.getToAccountNumber());
        fraudDetectionService.checkAndFlag(transaction, fromAccount);
        return mapToResponse(transaction);

    }

    public List<TransactionResponse> getHistory(String accountNumber, String email) {

        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException(accountNumber));

        if (!account.getUser().getEmail().equals(email)) {
            throw new UnauthorizedException("This account does not belong to you");
        }

        List<Transaction> transactions = transactionRepository
                .findByFromAccountOrToAccountOrderByCreatedAtDesc(account, account, Pageable.unpaged()).getContent();

        List<TransactionResponse> result = new ArrayList<>();
        for (Transaction t : transactions) {
            result.add(mapToResponse(t));
        }
        return result;
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
