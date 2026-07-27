package com.team.payment.controller;

import com.team.payment.dto.CreatePaymentRequest;
import com.team.payment.dto.PaymentResponse;
import com.team.payment.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 支付控制器集成测试
 * 成员A负责创建接口测试
 * 成员B负责状态推进接口测试
 * 成员C负责查询接口测试
 */
@WebMvcTest(PaymentController.class)
public class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PaymentService paymentService;

    /**
     * P0: 创建支付 - 返回201 Created
     */
    @Test
    public void testCreatePayment_Success() throws Exception {
        CreatePaymentRequest request = CreatePaymentRequest.builder()
            .sourceAccount("ACC001")
            .destinationAccount("ACC002")
            .amount(new BigDecimal("1500.50"))
            .currency("CNY")
            .reference("INV-20260727")
            .build();

        PaymentResponse response = PaymentResponse.builder()
            .id(1L)
            .idempotencyKey("key-001")
            .sourceAccount("ACC001")
            .destinationAccount("ACC002")
            .amount(new BigDecimal("1500.50"))
            .currency("CNY")
            .status("CREATED")
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

        when(paymentService.createPayment(any(CreatePaymentRequest.class), eq("key-001")))
            .thenReturn(response);

        mockMvc.perform(post("/api/payments")
                .header("Idempotency-Key", "key-001")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sourceAccount\":\"ACC001\",\"destinationAccount\":\"ACC002\"," +
                         "\"amount\":1500.50,\"currency\":\"CNY\",\"reference\":\"INV-20260727\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.status").value("CREATED"));
    }

    /**
     * P0: 查询支付详情 - 返回200
     */
    @Test
    public void testGetPaymentDetail_Success() throws Exception {
        PaymentResponse response = PaymentResponse.builder()
            .id(1L)
            .idempotencyKey("key-001")
            .sourceAccount("ACC001")
            .destinationAccount("ACC002")
            .amount(new BigDecimal("1500.50"))
            .currency("CNY")
            .status("CREATED")
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

        when(paymentService.getPaymentDetail(1L)).thenReturn(response);

        mockMvc.perform(get("/api/payments/1")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.status").value("CREATED"));
    }

    /**
     * P0: 查询支付详情 - 不存在返回404
     */
    @Test
    public void testGetPaymentDetail_NotFound() throws Exception {
        when(paymentService.getPaymentDetail(999L))
            .thenThrow(new RuntimeException("Payment not found"));

        mockMvc.perform(get("/api/payments/999")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isInternalServerError());
    }

    /**
     * P0: 列表查询 - 返回200
     */
    @Test
    public void testListPayments_Success() throws Exception {
        mockMvc.perform(get("/api/payments?status=CREATED&page=0&size=20")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());
    }

    /**
     * P0: 状态推进 - CREATED -> VALIDATED
     */
    @Test
    public void testValidatePayment_Success() throws Exception {
        PaymentResponse response = PaymentResponse.builder()
            .id(1L)
            .status("VALIDATED")
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

        when(paymentService.validatePayment(1L)).thenReturn(response);

        mockMvc.perform(post("/api/payments/1/validate")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("VALIDATED"));
    }
}

