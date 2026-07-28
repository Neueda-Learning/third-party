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

import java.math.BigDecimal;
import java.time.LocalDateTime;
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

    @Override
    public PaymentResponse createPayment(CreatePaymentRequest request, String idempotencyKey) {
        return null;
    }

    @Override
    public PaymentResponse getPaymentDetail(Long paymentId) {
        Payment payment = paymentDao.findById(paymentId);
        if (payment == null) {
            throw new PaymentNotFoundException(paymentId);
        }

        return PaymentResponse.builder()
            .id(payment.getId())
            .idempotencyKey(payment.getIdempotencyKey())
            .sourceAccount(payment.getSourceAccount())
            .destinationAccount(payment.getDestinationAccount())
            .amount(payment.getAmount())
            .currency(payment.getCurrency())
            .status(payment.getStatus())
            .errorCode(payment.getErrorCode())
            .errorMessage(payment.getErrorMessage())
            .reference(payment.getReference())
            .createdAt(payment.getCreatedAt())
            .updatedAt(payment.getUpdatedAt())
            .build();
    }

    @Override
    public PaymentListResponse listPayments(int page, int size, String status) {
        return null;
    }

    @Override
    public List<HistoryResponse> getPaymentHistory(Long paymentId) {
        return List.of();
    }

    @Override
    public PaymentResponse validatePayment(Long paymentId) {
        return null;
    }

    @Override
    public PaymentResponse sendPayment(Long paymentId) {
        return null;
    }

    @Override
    public PaymentResponse completePayment(Long paymentId) {
        return null;
    }

    @Override
    public PaymentResponse failPayment(Long paymentId, String errorCode, String errorMessage) {
        return null;
    }
}

