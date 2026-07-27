package com.team.payment.exception;

/**
 * 参数校验异常
 * 成员A负责实现
 */
public class ValidationException extends PaymentException {

    public ValidationException(String message) {
        super("VALIDATION_FAILED", message);
    }

    public ValidationException(String errorCode, String message) {
        super(errorCode, message);
    }
}

