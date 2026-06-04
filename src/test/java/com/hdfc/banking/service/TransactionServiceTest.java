package com.hdfc.banking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.hdfc.banking.enums.AccountStatus;
import com.hdfc.banking.enums.AccountType;

import com.hdfc.banking.dto.request.TransferRequest;
import com.hdfc.banking.dto.response.TransactionResponse;
import com.hdfc.banking.entity.Account;
import com.hdfc.banking.entity.Transaction;
import com.hdfc.banking.entity.User;
import com.hdfc.banking.exception.InsufficientFundsException;
import com.hdfc.banking.exception.UnauthorizedException;
import com.hdfc.banking.repository.AccountRepository;
import com.hdfc.banking.repository.TransactionRepository;

@ExtendWith(MockitoExtension.class)
public class TransactionServiceTest {

    @Mock
    AccountRepository accountRepository;
    @Mock
    TransactionRepository transactionRepository;
    @Mock
    FraudDetectionService fraudDetectionService;

    @InjectMocks
    TransactionService transactionService;

    private User user;
    private Account fromAccount;
    private Account toAccount;

    @BeforeEach
    void setUp() {
        user = User.builder().id(1L).name("teja").email("naga@gmail.com").build();

        fromAccount = Account.builder().id(1L).accountNumber("HDFC11111111")
                .accountType(AccountType.SAVINGS).accountStatus(AccountStatus.ACTIVE)
                .balance(new BigDecimal("50000")).user(user).build();

        toAccount = Account.builder().id(2L).accountNumber("HDFC22222222")
                .accountType(AccountType.SAVINGS).accountStatus(AccountStatus.ACTIVE)
                .balance(new BigDecimal("10000"))
                .user(User.builder().id(2L).email("other@test.com").build()).build();
    }

    @Test
    void transfer_shouldSucceed_whenBalanceIsSufficient() {

        TransferRequest request = new TransferRequest();

        request.setFromAccountNumber("HDFC11111111");
        request.setToAccountNumber("HDFC22222222");
        request.setAmount(new BigDecimal("10000"));
        request.setDescription("Test transfer");

        when(accountRepository.findByAccountNumber("HDFC11111111")).thenReturn(Optional.of(fromAccount));
        when(accountRepository.findByAccountNumber("HDFC22222222")).thenReturn(Optional.of(toAccount));
        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TransactionResponse response = transactionService.transfer(request, "naga@gmail.com");
        assertNotNull(response);
        assertEquals(new BigDecimal("40000"), fromAccount.getBalance());
        assertEquals(new BigDecimal("20000"), toAccount.getBalance());
        verify(accountRepository, times(2)).save(any(Account.class));
        verify(transactionRepository).save(any(Transaction.class));

    }

    @Test
    void transfer_shouldThrow_whenInsufficientBalance() {
        TransferRequest request = new TransferRequest();
        request.setFromAccountNumber("HDFC11111111");
        request.setToAccountNumber("HDFC22222222");
        request.setAmount(new BigDecimal("99999")); // more than balance (50000)
        request.setDescription("Too much");

        when(accountRepository.findByAccountNumber("HDFC11111111"))
                .thenReturn(Optional.of(fromAccount));
        when(accountRepository.findByAccountNumber("HDFC22222222"))
                .thenReturn(Optional.of(toAccount));

        // ASSERT — expect exception to be thrown
        assertThrows(InsufficientFundsException.class,
                () -> transactionService.transfer(request, "naga@gmail.com"));

        // Verify NO money was saved (balance should NOT change)
        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    void transfer_shouldThrow_whenWrongUser() {
        TransferRequest request = new TransferRequest();
        request.setFromAccountNumber("HDFC11111111");
        request.setToAccountNumber("HDFC22222222");
        request.setAmount(new BigDecimal("1000"));
        request.setDescription("Hacker attempt");

        when(accountRepository.findByAccountNumber("HDFC11111111"))
                .thenReturn(Optional.of(fromAccount));
        when(accountRepository.findByAccountNumber("HDFC22222222"))
                .thenReturn(Optional.of(toAccount));

        // "hacker@evil.com" is NOT the owner (owner is "naga@test.com")
        assertThrows(UnauthorizedException.class,
                () -> transactionService.transfer(request, "hacker@evil.com"));
    }

    @Test
    void transfer_shouldThrow_whenSameAccount() {
        TransferRequest request = new TransferRequest();
        request.setFromAccountNumber("HDFC11111111");
        request.setToAccountNumber("HDFC11111111"); // same!
        request.setAmount(new BigDecimal("1000"));
        request.setDescription("Self transfer");

        when(accountRepository.findByAccountNumber("HDFC11111111"))
                .thenReturn(Optional.of(fromAccount));

        assertThrows(RuntimeException.class,
                () -> transactionService.transfer(request, "naga@test.com"));
    }

    
}


