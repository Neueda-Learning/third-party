package com.team.payment.dto;

import lombok.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

/**
 * 充值请求DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DepositRequest {

    @NotNull(message = "amount 不能为null")
    @DecimalMin(value = "0.01", message = "amount 必须大于0")
    private BigDecimal amount;
}

