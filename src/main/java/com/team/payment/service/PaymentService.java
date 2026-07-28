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
     * 根据ID查询支付详情
     */
    PaymentResponse getPaymentDetail(Long paymentId);

    /**
     * 分页查询支付列表
     */
    PaymentListResponse listPayments(int page, int size, String status);

    /**
     * 查询支付历史
     */
    List<HistoryResponse> getPaymentHistory(Long paymentId);



}
