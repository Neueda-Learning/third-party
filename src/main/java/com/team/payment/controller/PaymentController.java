package com.team.payment.controller;

import com.team.payment.dao.PaymentDao;
import com.team.payment.dto.CreatePaymentRequest;
import com.team.payment.dto.HistoryResponse;
import com.team.payment.dto.PaymentListResponse;
import com.team.payment.dto.PaymentResponse;
import com.team.payment.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;

/**
 * 支付REST API控制器
 */
@RestController
@RequestMapping("/api/payments")
@CrossOrigin(origins = "*")
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    @Autowired
    private PaymentService paymentService;

    /**
     * POST /api/payments
     * 创建支付
     * 成员A负责实现
     *
     * 请求头: Idempotency-Key: client-provided-key
     * 响应码: 201 Created (新建) / 200 OK (幂等重复) / 409 Conflict (幂等键冲突) / 400 Bad Request (校验失败)
     */
    @PostMapping
    public ResponseEntity<PaymentResponse> createPayment(
            @Valid @RequestBody CreatePaymentRequest request,@RequestHeader("Idempotency-Key")  String idempotencyKey) {
        PaymentResponse response = paymentService.createPayment(request, idempotencyKey);
        log.info("Create payment finished with status={}", response.getStatus());
        if (response.getStatus().equals("CREATED")) {
            log.info("Payment created successfully");
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } else {
            log.warn("Payment creation failed. errorCode={}, errorMessage={}", response.getErrorCode(), response.getErrorMessage());
            return ResponseEntity.ok(response);

        }

    }



}
