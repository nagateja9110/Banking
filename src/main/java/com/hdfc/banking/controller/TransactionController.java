package com.hdfc.banking.controller;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hdfc.banking.dto.request.TransferRequest;
import com.hdfc.banking.dto.response.TransactionResponse;
import com.hdfc.banking.service.TransactionService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    @PostMapping("/deposit")
    public ResponseEntity<TransactionResponse> deposit(
            @RequestParam String accountNumber,
            @RequestParam BigDecimal amount,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(transactionService.deposit(accountNumber, amount, userDetails.getUsername()));
    }

    @PostMapping("/withdraw")
    public ResponseEntity<TransactionResponse> withdraw(
            @RequestParam String accountNumber,
            @RequestParam BigDecimal amount,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(transactionService.withdraw(accountNumber, amount, userDetails.getUsername()));
    }

    @PostMapping("/transfer")
    public ResponseEntity<TransactionResponse> transfer(
            @Valid @RequestBody TransferRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(transactionService.transfer(request, userDetails.getUsername()));
    }

    @GetMapping("/{accountNumber}")
    public ResponseEntity<List<TransactionResponse>> history(
            @PathVariable String accountNumber,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(transactionService.getHistory(accountNumber, userDetails.getUsername()));
    }
}
