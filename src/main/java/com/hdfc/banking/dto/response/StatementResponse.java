package com.hdfc.banking.dto.response;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class StatementResponse {
    private String accountNumber;
    private String accountHolderName;
    private LocalDate fromDate;
    private LocalDate toDate;
    private BigDecimal openingBalance;
    private BigDecimal closingBalance;
    private BigDecimal totalCredits;
    private BigDecimal totalDebits;
    private int totalTransactions;
    private List<TransactionResponse> transactions;
}