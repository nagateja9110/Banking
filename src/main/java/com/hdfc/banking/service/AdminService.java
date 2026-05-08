package com.hdfc.banking.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.autoconfigure.data.web.SpringDataWebProperties.Pageable;
import org.springframework.stereotype.Service;

import com.hdfc.banking.dto.response.AccountResponse;
import com.hdfc.banking.dto.response.TransactionResponse;
import com.hdfc.banking.dto.response.UserSummaryResponse;
import com.hdfc.banking.entity.Account;
import com.hdfc.banking.entity.Transaction;
import com.hdfc.banking.entity.User;
import com.hdfc.banking.enums.AccountStatus;
import com.hdfc.banking.exception.AccountNotFoundException;
import com.hdfc.banking.repository.AccountRepository;
import com.hdfc.banking.repository.TransactionRepository;
import com.hdfc.banking.repository.UserRepository;
 
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class AdminService {

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;

    public List<UserSummaryResponse> getAllUsers() {

        List<User> users = userRepository.findAll();

        List<UserSummaryResponse> result = new ArrayList<>();
        for (User user : users) {
            result.add(UserSummaryResponse.builder()
                    .id(user.getId())
                    .name(user.getName())
                    .email(user.getEmail())
                    .role(user.getRole())
                    .locked(user.isLocked())
                    .build());
        }
        return result;
    }

    public List<AccountResponse> getAllAccounts() {
        List<Account> accounts = accountRepository.findAll();

        List<AccountResponse> result = new ArrayList<>();
        for (Account account : accounts) {
            result.add(AccountResponse.builder()
                    .id(account.getId())
                    .accountNumber(account.getAccountNumber())
                    .accountType(account.getAccountType())
                    .accountStatus(account.getAccountStatus())
                    .balance(account.getBalance())
                    .build());
        }
        return result;
    }

    @Transactional
    public String freezeAccount(String accountNumber) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException(accountNumber));

        if (account.getAccountStatus() == AccountStatus.FROZEN) {
            return "Account is already frozen";
        }

        account.setAccountStatus(AccountStatus.FROZEN);
        accountRepository.save(account);
        log.info("Admin froze account: {}", accountNumber);
        return "Account frozen successfully";
    }

    @Transactional
    public String unfreezeAccount(String accountNumber) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException(accountNumber));

        if (account.getAccountStatus() == AccountStatus.ACTIVE) {
            return "Account is already active";
        }

        account.setAccountStatus(AccountStatus.ACTIVE);
        accountRepository.save(account);
        log.info("Admin unfroze account: {}", accountNumber);
        return "Account unfrozen successfully";
    }

    public List<TransactionResponse> getAccountTransactions(String accountNumber) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException(accountNumber));

        List<Transaction> transactions = transactionRepository
                .findByFromAccountOrToAccountOrderByCreatedAtDesc(account, account, Pageable.unpaged())
                .getContent();

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
