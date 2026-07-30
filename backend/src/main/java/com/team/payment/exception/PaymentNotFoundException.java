package com.team.payment.exception;

/**
 * 支付不存在异常
 */
public class PaymentNotFoundException extends PaymentException {

    public PaymentNotFoundException(Long paymentId) {
        super("PAYMENT_NOT_FOUND", "支付ID不存在: " + paymentId);
    }

    public PaymentNotFoundException(String message) {
        super("PAYMENT_NOT_FOUND", message);
    }
}

