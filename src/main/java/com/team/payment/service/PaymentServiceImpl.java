package com.team.payment.service;

import com.team.payment.dto.CreatePaymentRequest;
import com.team.payment.dto.HistoryResponse;
import com.team.payment.dto.PaymentListResponse;
import com.team.payment.dto.PaymentResponse;
import com.team.payment.entity.Payment;
import com.team.payment.entity.PaymentHistory;
import com.team.payment.exception.*;
import com.team.payment.dao.PaymentDao;
import com.team.payment.dao.PaymentHistoryDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 支付业务服务实现
 * 主要包含：创建支付、参数校验、幂等性检查、状态管理等
 */
@Service
public class PaymentServiceImpl implements PaymentService {

    @Autowired
    private PaymentDao paymentDao;

    @Autowired
    private PaymentHistoryDao paymentHistoryDao;


    @Override
    @Transactional(readOnly = true)
    public List<HistoryResponse> getPaymentHistory(Long paymentId) {
        Payment payment = paymentDao.findById(paymentId);
        if (payment == null) {
            throw new PaymentNotFoundException(paymentId);
        }

        return paymentHistoryDao.findByPaymentId(paymentId).stream()
            .map(this::toHistoryResponse)
            .collect(Collectors.toList());
    }

    private HistoryResponse toHistoryResponse(PaymentHistory history) {
        return HistoryResponse.builder()
            .fromStatus(history.getFromStatus())
            .toStatus(history.getToStatus())
            .reason(history.getReason())
            .triggeredBy(history.getTriggeredBy())
            .createdAt(history.getCreatedAt())
            .build();
    }


}

