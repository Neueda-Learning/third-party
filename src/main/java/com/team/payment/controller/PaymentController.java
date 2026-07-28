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


