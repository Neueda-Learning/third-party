package com.team.payment.controller;

import com.team.payment.dto.CreatePaymentRequest;
import com.team.payment.dto.HistoryResponse;
import com.team.payment.dto.PaymentListResponse;
import com.team.payment.dto.PaymentResponse;
import com.team.payment.service.PaymentService;
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
            @Valid @RequestBody CreatePaymentRequest request,
            @RequestHeader(value = "Idempotency-Key", required = true) String idempotencyKey) {

    }

    /**
     * GET /api/payments/{id}
     * 查询支付详情
     * 成员C负责实现
     *
     * 响应码: 200 OK / 404 Not Found
     */
    @GetMapping("/{id}")
    public ResponseEntity<PaymentResponse> getPaymentDetail(@PathVariable Long id) {

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


    }

    /**
     * GET /api/payments/{id}/history
     * 查询支付历史
     * 成员C负责实现
     *
     * 响应码: 200 OK / 404 Not Found
     */
    @GetMapping("/{id}/history")
    public ResponseEntity<List<HistoryResponse>> getPaymentHistory(@PathVariable Long id) {

    }

    /**
     * POST /api/payments/{id}/validate
     * 手动推进状态为VALIDATED（演示用）
     * 成员B负责实现
     *
     * 响应码: 200 OK / 404 Not Found / 400 Bad Request
     */
    @PostMapping("/{id}/validate")
    public ResponseEntity<PaymentResponse> validatePayment(@PathVariable Long id) {

    }

    /**
     * POST /api/payments/{id}/send
     * 手动推进状态为SENT（演示用）
     * 成员B负责实现
     *
     * 响应码: 200 OK / 404 Not Found / 400 Bad Request
     */
    @PostMapping("/{id}/send")
    public ResponseEntity<PaymentResponse> sendPayment(@PathVariable Long id) {

    }

    /**
     * POST /api/payments/{id}/complete
     * 手动推进状态为COMPLETED（演示用）
     * 成员B负责实现
     *
     * 响应码: 200 OK / 404 Not Found / 400 Bad Request
     */
    @PostMapping("/{id}/complete")
    public ResponseEntity<PaymentResponse> completePayment(@PathVariable Long id) {

    }

    /**
     * POST /api/payments/{id}/fail
     * 手动推进状态为FAILED（演示用）
     * 成员B负责实现
     *
     * 请求体:
     * {
     *   "errorCode": "BUSINESS_ERROR",
     *   "errorMessage": "业务规则检查失败"
     * }
     *
     * 响应码: 200 OK / 404 Not Found / 400 Bad Request
     */
    @PostMapping("/{id}/fail")
    public ResponseEntity<PaymentResponse> failPayment(
            @PathVariable Long id,
            @RequestParam(value = "errorCode", defaultValue = "PROCESSING_ERROR") String errorCode,
            @RequestParam(value = "errorMessage", defaultValue = "Payment failed") String errorMessage) {


    }
}

