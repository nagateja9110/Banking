package com.hdfc.banking.exception;

public class DuplicateAccountException extends RuntimeException {

    public DuplicateAccountException(String accountType) {
        super("User already has a " + accountType + " account. Only one account per type allowed.");
    }
}