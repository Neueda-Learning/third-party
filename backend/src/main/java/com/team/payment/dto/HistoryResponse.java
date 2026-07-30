package com.team.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 支付历史记录响应DTO
 * 用于API返回历史信息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HistoryResponse {

    /** 变更前状态 */
    private String fromStatus;

    /** 变更后状态 */
    private String toStatus;

    /** 变更原因 */
    private String reason;

    /** 触发方 */
    private String triggeredBy;

    /** 记录时间 */
    private LocalDateTime createdAt;
}

