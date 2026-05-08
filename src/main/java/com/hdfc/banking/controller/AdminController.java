package com.hdfc.banking.controller;

import com.hdfc.banking.dto.response.AccountResponse;
import com.hdfc.banking.dto.response.TransactionResponse;
import com.hdfc.banking.dto.response.UserSummaryResponse;
import com.hdfc.banking.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UserSummaryResponse>> getAllUsers() {
        return ResponseEntity.ok(adminService.getAllUsers());
    }

    @GetMapping("/accounts")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<AccountResponse>> getAllAccounts() {
        return ResponseEntity.ok(adminService.getAllAccounts());
    }

    @PutMapping("/accounts/{accountNumber}/freeze")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> freeze(@PathVariable String accountNumber) {
        return ResponseEntity.ok(adminService.freezeAccount(accountNumber));
    }

    @PutMapping("/accounts/{accountNumber}/unfreeze")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> unfreeze(@PathVariable String accountNumber) {
        return ResponseEntity.ok(adminService.unfreezeAccount(accountNumber));
    }

    @GetMapping("/transactions/{accountNumber}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<TransactionResponse>> transactions(@PathVariable String accountNumber) {
        return ResponseEntity.ok(adminService.getAccountTransactions(accountNumber));
    }
}
