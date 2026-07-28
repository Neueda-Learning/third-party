package com.team.payment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team.payment.dto.CreatePaymentRequest;
import com.team.payment.dto.PaymentResponse;
import com.team.payment.exception.GlobalExceptionHandler;
import com.team.payment.exception.PaymentException;
import com.team.payment.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentController.class)
@Import(GlobalExceptionHandler.class)
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PaymentService paymentService;

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
    void createPaymentReturns200WhenServiceReturnsNonCreatedStatus() throws Exception {
        CreatePaymentRequest request = buildValidRequest();
        PaymentResponse response = PaymentResponse.builder()
                .status("FAILED")
                .errorCode("PROCESSING_ERROR")
                .errorMessage("Payment failed")
                .build();

        when(paymentService.createPayment(any(CreatePaymentRequest.class), eq("idem-2"))).thenReturn(response);

        mockMvc.perform(post("/api/payments")
                        .header("Idempotency-Key", "idem-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.errorCode").value("PROCESSING_ERROR"));
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
