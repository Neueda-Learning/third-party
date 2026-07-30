package com.team.payment.exception;

/**
 * 非法状态转移异常
 * 成员B负责实现
 */
public class InvalidStatusTransitionException extends PaymentException {

    public InvalidStatusTransitionException(String fromStatus, String toStatus) {
        super("INVALID_STATUS_TRANSITION", 
              String.format("非法状态转移: %s -> %s", fromStatus, toStatus));
    }

    public InvalidStatusTransitionException(String message) {
        super("INVALID_STATUS_TRANSITION", message);
    }
}

