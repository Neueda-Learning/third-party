package com.team.payment.controller;

import com.team.payment.dto.HistoryResponse;
import com.team.payment.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {

    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private PaymentController paymentController;

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
}

