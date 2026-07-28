package com.team.payment.service;

import com.team.payment.dto.CreatePaymentRequest;
import com.team.payment.dto.HistoryResponse;
import com.team.payment.dto.PaymentListResponse;
import com.team.payment.dto.PaymentResponse;
import com.team.payment.entity.Payment;
import com.team.payment.entity.PaymentHistory;

import java.util.List;

/**
 * 支付业务服务接口
 */
public interface PaymentService {

    /**
     * 创建支付
     */
    PaymentResponse createPayment(CreatePaymentRequest request, String idempotencyKey);

    /**
     * 手动推进支付状态为VALIDATED（演示用）
     */
    PaymentResponse validatePayment(Long paymentId);

    /**
     * 手动推进支付状态为SENT（演示用）
     */
    PaymentResponse sendPayment(Long paymentId);

    /**
     * 手动推进支付状态为COMPLETED（演示用）
     */
    PaymentResponse completePayment(Long paymentId);

    /**
     * 手动推进支付状态为FAILED（演示用）
     */
    PaymentResponse failPayment(Long paymentId, String errorCode, String errorMessage);
}
