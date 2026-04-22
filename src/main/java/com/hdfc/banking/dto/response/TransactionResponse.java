package com.hdfc.banking.dto.response;

import com.hdfc.banking.enums.TransactionStatus;
import com.hdfc.banking.enums.TransactionType;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * What client receives when they view transaction history.
 * Shows enough to display a bank statement line item.
 */
@Data
@Builder
public class TransactionResponse {
    private Long id;
    private String referenceNumber;
    private TransactionType transactionType;
    private TransactionStatus transactionStatus;
    private BigDecimal amount;
    private BigDecimal balanceAfter;
    private String description;
    private String fromAccountNumber;
    private String toAccountNumber;
    private LocalDateTime createdAt;
}