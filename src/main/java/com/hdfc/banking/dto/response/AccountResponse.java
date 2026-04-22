package com.hdfc.banking.dto.response;

import com.hdfc.banking.enums.AccountStatus;
import com.hdfc.banking.enums.AccountType;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;


@Data
@Builder
public class AccountResponse {
    private Long id;
    private String accountNumber;
    private AccountType accountType;
    private AccountStatus accountStatus;
    private BigDecimal balance;
    private BigDecimal interestRate;
    private LocalDateTime createdAt;
    private String userEmail;
}