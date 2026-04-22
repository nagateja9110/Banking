package com.hdfc.banking.exception;

public class AccountNotFoundException extends RuntimeException {
    public AccountNotFoundException(Long id) {
        super("Account with ID" + id + "Not Found");
    }

    public AccountNotFoundException(String accountNumber) {
        super("Account with number " + accountNumber + " not found");
    }
}
