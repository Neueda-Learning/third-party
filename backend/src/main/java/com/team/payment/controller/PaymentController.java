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
        if ("CREATED".equals(response.getStatus())) {
            log.info("Payment created successfully");
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        }

        if ("DUPLICATE_IDEMPOTENCY_KEY".equals(response.getErrorCode())) {
            log.warn("Duplicate idempotency key. errorMessage={}", response.getErrorMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }

        if ("FAILED".equals(response.getStatus())) {
            log.warn("Payment validation failed. errorCode={}, errorMessage={}", response.getErrorCode(), response.getErrorMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        // Backward-compatible fallback for idempotent replay of an existing payment snapshot.
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/payments/{id}
     * 查询支付详情
     * 成员C负责实现
     *
     * 响应码: 200 OK / 404 Not Found
     */
    @GetMapping("/{id}")
    public ResponseEntity<PaymentResponse> getPaymentDetail(@PathVariable("id") Long id) {
        PaymentResponse response = paymentService.getPaymentDetail(id);
        return ResponseEntity.ok(response);
    }




    /**
     * GET /api/payments?status=CREATED&page=0&size=20
     * 列表查询（分页+筛选）
     * 成员C负责实现
     *
     * 查询参数:
     *   - status: 筛选状态（可选）
     *   - page: 页码（从0开始，默认0）
     *   - size: 页大小（默认20）
     *
     * 响应码: 200 OK
     */
    @GetMapping
    public ResponseEntity<PaymentListResponse> listPayments(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        PaymentListResponse response = paymentService.listPayments(page, size, status);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/payments/{id}/history
     * 查询支付历史
     * 成员C负责实现
     *
     * 响应码: 200 OK / 404 Not Found
     */
    @GetMapping("/{id}/history")
    public ResponseEntity<List<HistoryResponse>> getPaymentHistory(@PathVariable("id") Long id) {
        return ResponseEntity.ok(paymentService.getPaymentHistory(id));
    }



}




