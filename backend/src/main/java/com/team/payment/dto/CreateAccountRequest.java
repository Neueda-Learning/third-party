package com.team.payment.dto;

import lombok.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

/**
 * 创建账户请求DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateAccountRequest {

    @NotBlank(message = "accountName 不能为空")
    @Size(min = 2, max = 64, message = "accountName 长度应为2-64")
    private String accountName;

    @NotBlank(message = "currency 不能为空")
    @Size(min = 3, max = 8, message = "currency 格式不正确")
    private String currency;

    @NotNull(message = "initialBalance 不能为null")
    @DecimalMin(value = "0.00", inclusive = true, message = "initialBalance 不能为负数")
    private BigDecimal initialBalance;
}

