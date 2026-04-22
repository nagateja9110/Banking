package com.hdfc.banking.controller;

import com.hdfc.banking.exception.*;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;

// TEMPORARY controller to test exceptions — delete after verifying
@RestController
@RequestMapping("/test")
public class TestController {

    @GetMapping("/account-not-found")
    public String testNotFound() {
        throw new AccountNotFoundException(999L);
    }

    @GetMapping("/insufficient-funds")
    public String testInsufficientFunds() {
        throw new InsufficientFundsException(new BigDecimal("5000"), new BigDecimal("8000"));
    }

    @GetMapping("/frozen")
    public String testFrozen() {
        throw new AccountFrozenException("HDFC001");
    }

    @GetMapping("/unauthorized")
    public String testUnauthorized() {
        throw new UnauthorizedException("Invalid or missing JWT token");
    }

    @GetMapping("/otp-expired")
    public String testOtpExpired() {
        throw new OtpExpiredException();
    }
}
