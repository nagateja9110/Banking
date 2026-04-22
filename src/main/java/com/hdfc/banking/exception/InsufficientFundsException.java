package com.hdfc.banking.exception;

import java.math.BigDecimal;

public class InsufficientFundsException extends RuntimeException {
    public InsufficientFundsException(BigDecimal balance, BigDecimal requested) {
        super("Insufficient funds. Available: ₹" + balance + ", Requested: ₹" + requested);
    }
}
