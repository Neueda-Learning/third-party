package com.team.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付响应DTO
 * 用于API返回支付信息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentResponse {

    /** 系统生成的ID */
    private Long id;

    /** 幂等键 */
    private String idempotencyKey;

    /** 付款账户 */
    private String sourceAccount;

    /** 收款账户 */
    private String destinationAccount;

    /** 金额 */
    private BigDecimal amount;

    /** 币种 */
    private String currency;

    /** 当前状态 */
    private String status;

    /** 失败错误码 */
    private String errorCode;

    /** 失败错误信息 */
    private String errorMessage;

    /** 参考号 */
    private String reference;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}

