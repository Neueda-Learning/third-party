package com.team.payment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team.payment.dto.CreatePaymentRequest;
import com.team.payment.dto.HistoryResponse;
import com.team.payment.dto.PaymentResponse;
import com.team.payment.exception.GlobalExceptionHandler;
import com.team.payment.exception.PaymentNotFoundException;
import com.team.payment.exception.PaymentException;
import com.team.payment.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@WebMvcTest(PaymentController.class)
@Import(GlobalExceptionHandler.class)
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
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
    void createPaymentReturns201WhenServiceReturnsCreated() throws Exception {
        CreatePaymentRequest request = buildValidRequest();
        PaymentResponse response = PaymentResponse.builder()
                .id(1L)
                .status("CREATED")
                .idempotencyKey("idem-1")
                .build();

        when(paymentService.createPayment(any(CreatePaymentRequest.class), eq("idem-1"))).thenReturn(response);

        mockMvc.perform(post("/api/payments")
                        .header("Idempotency-Key", "idem-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.id").value(1));

        verify(paymentService).createPayment(any(CreatePaymentRequest.class), eq("idem-1"));
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
    void createPaymentReturns400WhenValidationFails() throws Exception {
        CreatePaymentRequest invalidRequest = CreatePaymentRequest.builder()
                .destinationAccount("bob")
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .build();

        mockMvc.perform(post("/api/payments")
                        .header("Idempotency-Key", "idem-3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }
    @Test
    void createPaymentReturns409WhenDuplicateIdempotencyKeyExceptionThrown() throws Exception {
        CreatePaymentRequest request = buildValidRequest();

        when(paymentService.createPayment(any(CreatePaymentRequest.class), eq("idem-dup")))
                .thenThrow(new PaymentException("DUPLICATE_IDEMPOTENCY_KEY", "Duplicate key"));

        mockMvc.perform(post("/api/payments")
                        .header("Idempotency-Key", "idem-dup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_IDEMPOTENCY_KEY"));
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

    private CreatePaymentRequest buildValidRequest() {
        return CreatePaymentRequest.builder()
                .sourceAccount("alice")
                .destinationAccount("bob")
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .reference("order-1")
                .build();
    }
}
