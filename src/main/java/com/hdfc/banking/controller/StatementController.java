package com.hdfc.banking.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hdfc.banking.dto.response.StatementResponse;
import com.hdfc.banking.service.StatementService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/statements")
@RequiredArgsConstructor
public class StatementController {
    
    private final StatementService statementService;

    @GetMapping("/{accountNumber}")
    public ResponseEntity<StatementResponse> getStatement(
            @PathVariable String accountNumber,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal UserDetails userDetails) {

        return ResponseEntity.ok(
            statementService.generateStatement(accountNumber, from, to, userDetails.getUsername())
        );
    }


}
