package com.team.payment.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付实体类
 * 对应payments表
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    /** 系统生成的唯一ID */
    private Long id;

    /** 幂等键（客户端提供），唯一 */
    private String idempotencyKey;

    /** 付款账户 */
    private String sourceAccount;

    /** 收款账户 */
    private String destinationAccount;

    /** 金额（2位小数） */
    private BigDecimal amount;

    /** 币种（仅支持CNY） */
    private String currency;

    /** 当前状态 */
    private String status;

    /** 失败错误码 */
    private String errorCode;

    /** 失败错误信息 */
    private String errorMessage;

    /** 参考号/备注 */
    private String reference;

    /** 版本号（乐观锁用） */
    private Long version;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}

