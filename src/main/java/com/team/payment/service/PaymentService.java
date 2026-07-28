package com.team.payment.service;

import com.team.payment.dto.HistoryResponse;

import java.util.List;

/**
 * 支付业务服务接口
 */
public interface PaymentService {

    /**
     * 查询支付历史
     */
    List<HistoryResponse> getPaymentHistory(Long paymentId);
}

