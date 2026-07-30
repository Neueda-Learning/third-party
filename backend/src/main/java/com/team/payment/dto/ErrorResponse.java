package com.team.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 错误响应DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ErrorResponse {

    /** 错误码 */
    private String errorCode;

    /** 错误信息 */
    private String message;

    /** 时间戳 */
    private LocalDateTime timestamp;

    /** 链路追踪ID */
    private String traceId;
}

