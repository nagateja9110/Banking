package com.hdfc.banking.exception;

public class AccountFrozenException extends RuntimeException {
    public AccountFrozenException(String accountNumber) {
        super("Account " + accountNumber + " is frozen. Contact support to unfreeze.");
    }
}
