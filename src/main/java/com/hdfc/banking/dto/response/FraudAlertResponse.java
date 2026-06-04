package com.hdfc.banking.dto.response;

import com.hdfc.banking.enums.FraudAlertStatus;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class FraudAlertResponse {
    private Long id;
    private Long transactionId;
    private String reason;
    private BigDecimal riskScore;
    private FraudAlertStatus status;
    private LocalDateTime flaggedAt;
    private String reviewedBy;
    private LocalDateTime reviewedAt;
}