package com.hdfc.banking.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.Builder;
import java.math.BigDecimal;


@Data
public class TransferRequest {

    @NotBlank(message = "Source account number is required")
    private String fromAccountNumber;

    @NotBlank(message = "Destination account number is required")
    private String toAccountNumber;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Transfer amount must be at least ₹0.01")
    private BigDecimal amount;

    private String description;
}