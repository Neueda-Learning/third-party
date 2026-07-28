package com.team.payment.controller;

import com.team.payment.dto.PaymentResponse;
import com.team.payment.service.PaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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


}
