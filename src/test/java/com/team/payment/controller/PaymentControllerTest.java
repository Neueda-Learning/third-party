package com.team.payment.controller;

import com.team.payment.dto.HistoryResponse;
import com.team.payment.dto.PaymentResponse;
import com.team.payment.exception.GlobalExceptionHandler;
import com.team.payment.exception.PaymentNotFoundException;
import com.team.payment.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {

    private MockMvc mockMvc;

    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private PaymentController paymentController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(paymentController)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    void getPaymentDetail_shouldReturn200_whenPaymentExists() throws Exception {
        PaymentResponse response = PaymentResponse.builder()
            .id(1L)
            .idempotencyKey("idem-001")
            .sourceAccount("acct-src")
            .destinationAccount("acct-dst")
            .amount(new BigDecimal("100.50"))
            .currency("CNY")
            .status("COMPLETED")
            .build();

        when(paymentService.getPaymentDetail(1L)).thenReturn(response);

        mockMvc.perform(get("/api/payments/{id}", 1L).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.idempotencyKey").value("idem-001"))
            .andExpect(jsonPath("$.amount").value(100.50))
            .andExpect(jsonPath("$.currency").value("CNY"))
            .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void getPaymentHistoryReturnsOkResponseWithBody() {
        Long paymentId = 10L;
        List<HistoryResponse> history = List.of(
            HistoryResponse.builder()
                .fromStatus(null)
                .toStatus("CREATED")
                .reason("Payment created")
                .triggeredBy("API")
                .createdAt(LocalDateTime.of(2026, 7, 27, 10, 0))
                .build()
        );
        when(paymentService.getPaymentHistory(paymentId)).thenReturn(history);
        var response = paymentController.getPaymentHistory(paymentId);
        assertEquals(200, response.getStatusCode().value());
        assertEquals(history, response.getBody());
        verify(paymentService).getPaymentHistory(paymentId);
    }
    @Test
    void getPaymentDetail_shouldReturn404_whenPaymentNotFound() throws Exception {
        when(paymentService.getPaymentDetail(999L)).thenThrow(new PaymentNotFoundException(999L));

        mockMvc.perform(get("/api/payments/{id}", 999L).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("PAYMENT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("支付ID不存在: 999"));
    }

    @Test
    void getPaymentDetail_shouldReturn500_whenUnexpectedExceptionOccurs() throws Exception {
        when(paymentService.getPaymentDetail(2L)).thenThrow(new RuntimeException("mock runtime error"));

        mockMvc.perform(get("/api/payments/{id}", 2L).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorCode").value("PROCESSING_ERROR"))
                .andExpect(jsonPath("$.message").value("内部系统错误: mock runtime error"));
    }

    @Test
    void getPaymentDetail_shouldReturn500_whenIdIsNonNumeric() throws Exception {
        mockMvc.perform(get("/api/payments/{id}", "abc").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorCode").value("PROCESSING_ERROR"));
    }
}

