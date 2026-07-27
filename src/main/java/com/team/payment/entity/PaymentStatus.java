package com.team.payment.entity;

/**
 * 支付状态枚举
 */
public enum PaymentStatus {
    CREATED("已创建，待校验"),
    VALIDATED("校验通过，待发送"),
    SENT("已发送，待完成"),
    COMPLETED("完成"),
    FAILED("失败");

    private final String description;

    PaymentStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 将字符串转换为PaymentStatus枚举
     */
    public static PaymentStatus fromValue(String value) {
        try {
            return PaymentStatus.valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}

