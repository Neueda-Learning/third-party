package com.team.payment.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 支付历史记录实体类
 * 对应payment_history表
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentHistory {

    /** 自增ID */
    private Long id;

    /** 关联的支付ID */
    private Long paymentId;

    /** 变更前状态（初始为null） */
    private String fromStatus;

    /** 变更后状态 */
    private String toStatus;

    /** 变更原因 */
    private String reason;

    /** 触发方（API/SCHEDULER/SYSTEM） */
    private String triggeredBy;

    /** 记录时间 */
    private LocalDateTime createdAt;
}

