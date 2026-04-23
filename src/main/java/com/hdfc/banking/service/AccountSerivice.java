package com.hdfc.banking.service;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.List;

import org.springframework.stereotype.Service;

import com.hdfc.banking.dto.request.CreateAccountRequest;
import com.hdfc.banking.dto.response.AccountResponse;
import com.hdfc.banking.entity.Account;
import com.hdfc.banking.entity.User;
import com.hdfc.banking.enums.AccountStatus;
import com.hdfc.banking.exception.AccountNotFoundException;
import com.hdfc.banking.exception.UnauthorizedException;
import com.hdfc.banking.repository.AccountRepository;
import com.hdfc.banking.repository.UserRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AccountSerivice {

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;

    @Transactional
    public AccountResponse createAccount(CreateAccountRequest request,String email){
        User user = userRepository.findByEmail(email).orElseThrow( ()-> new RuntimeException("User not Found"));

        String accountNumber= generatAccountNumber();

        Account account= Account.builder()
                         .user(user)
                         .accountNumber(accountNumber)
                         .accountType(request.getAccountType())
                         .balance(BigDecimal.ZERO)
                         .accountStatus(AccountStatus.ACTIVE)
                         .build();
        accountRepository.save(account);
        return mapToResponse(account);
    }

    public List<AccountResponse> getMyAccounts(String email){
        User user = userRepository.findByEmail(email).orElseThrow(()->new RuntimeException("User not found") );

        return accountRepository.findByUser(user)
               .stream()
               .map(this::mapToResponse)
               .toList();
    }

    public BigDecimal getBalance(String accountNumber, String email) {
    Account account = accountRepository.findByAccountNumber(accountNumber)
            .orElseThrow(() -> new AccountNotFoundException(accountNumber));

    if (!account.getUser().getEmail().equals(email)) {
        throw new UnauthorizedException("This account does not belong to you");
    }
    return account.getBalance();
}

    private String generatAccountNumber(){
        return "HDFC" + String.format("%08d", new SecureRandom().nextInt(100000000));
    }
    
    private AccountResponse mapToResponse(Account account){
        return AccountResponse.builder()
           .id(account.getId())
           .accountNumber(account.getAccountNumber())
           .accountType(account.getAccountType())
           .accountStatus(account.getAccountStatus())
           .balance(account.getBalance())
           .interestRate(account.getInterestRate())
           .createdAt(account.getCreatedAt())
           .build();
    }
}
