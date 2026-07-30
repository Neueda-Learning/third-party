package com.team.payment.dto;

import com.team.payment.entity.Payment;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付响应DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentResponse {

    private Long id;
    private String idempotencyKey;
    private String sourceAccount;
    private String destinationAccount;
    private Long fromAccountId;
    private Long toAccountId;
    private BigDecimal exchangeRate;
    private BigDecimal amount;
    private String currency;
    private String status;
    private String errorCode;
    private String errorMessage;
    private String reference;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static PaymentResponse fromEntity(Payment payment) {
        return PaymentResponse.builder()
                .id(payment.getId())
                .idempotencyKey(payment.getIdempotencyKey())
                .sourceAccount(payment.getSourceAccount())
                .destinationAccount(payment.getDestinationAccount())
                .fromAccountId(payment.getFromAccountId())
                .toAccountId(payment.getToAccountId())
                .exchangeRate(payment.getExchangeRate())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .status(payment.getStatus())
                .errorCode(payment.getErrorCode())
                .errorMessage(payment.getErrorMessage())
                .reference(payment.getReference())
                .createdAt(payment.getCreatedAt())
                .updatedAt(payment.getUpdatedAt())
                .build();
    }
}
