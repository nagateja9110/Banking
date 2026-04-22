package com.hdfc.banking.dto.request;

import com.hdfc.banking.enums.AccountType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;



@Data
public class CreateAccountRequest {

    @NotNull(message = "Account type is required (SAVINGS, CURRENT, or FIXED_DEPOSIT)")
    private AccountType accountType;
}