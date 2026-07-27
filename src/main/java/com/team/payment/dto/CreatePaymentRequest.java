package com.team.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

/**
 * 创建支付请求DTO
 * 成员A负责实现
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreatePaymentRequest {

    /** 付款账户 */
    @NotBlank(message = "sourceAccount 不能为空")
    @Size(min = 1, max = 50, message = "sourceAccount 长度应为1-50")
    private String sourceAccount;

    /** 收款账户 */
    @NotBlank(message = "destinationAccount 不能为空")
    @Size(min = 1, max = 50, message = "destinationAccount 长度应为1-50")
    private String destinationAccount;

    /** 金额 */
    @NotNull(message = "amount 不能为null")
    @DecimalMin(value = "0.01", message = "amount 必须大于0")
    @Digits(integer = 16, fraction = 2, message = "amount 最多2位小数")
    private BigDecimal amount;

    /** 币种 */
    private String currency;

    /** 参考号/备注 */
    @Size(max = 255, message = "reference 长度最多255")
    private String reference;
}

