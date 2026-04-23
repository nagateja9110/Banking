package com.hdfc.banking.controller;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hdfc.banking.dto.request.CreateAccountRequest;
import com.hdfc.banking.dto.response.AccountResponse;
import com.hdfc.banking.service.AccountSerivice;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountSerivice accountSerivice;

    @PostMapping("/create")
    public ResponseEntity<AccountResponse>create(@Valid @RequestBody CreateAccountRequest request,@AuthenticationPrincipal UserDetails userDetails){
        return ResponseEntity.status(HttpStatus.CREATED).body(accountSerivice.createAccount(request, userDetails.getUsername()));
    }

    @GetMapping("/my")
    public ResponseEntity<List<AccountResponse>>myAcccounts(@AuthenticationPrincipal UserDetails userDetails){
          return ResponseEntity.ok(accountSerivice.getMyAccounts(userDetails.getUsername()));

    }

    @GetMapping("/{accountNumber}/balance")
    public ResponseEntity<BigDecimal>getBalance(@PathVariable String accountNumber,@AuthenticationPrincipal UserDetails userDetails){
        return ResponseEntity.ok(accountSerivice.getBalance(accountNumber,userDetails.getUsername()));
    }

    


    
}
