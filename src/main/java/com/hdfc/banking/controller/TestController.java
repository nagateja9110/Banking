package com.hdfc.banking.controller;

import com.hdfc.banking.exception.*;
import com.hdfc.banking.service.InterestCalculationService;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


import java.math.BigDecimal;

// TEMPORARY controller to test exceptions — delete after verifying
@RestController
@RequestMapping("/test")
@RequiredArgsConstructor
public class TestController {

    private final InterestCalculationService interestCalculationService;

    @PostMapping("/trigger-interest")
    public ResponseEntity<String> triggerInterest() {
        interestCalculationService.calculateMonthlyInterest();
        return ResponseEntity.ok("Interest calculation triggered");
    }

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
