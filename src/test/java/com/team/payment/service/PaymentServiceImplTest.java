package com.team.payment.service;

import com.team.payment.dao.PaymentDao;
import com.team.payment.dao.PaymentHistoryDao;
import com.team.payment.dto.HistoryResponse;
import com.team.payment.entity.Payment;
import com.team.payment.entity.PaymentHistory;
import com.team.payment.exception.PaymentNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock
    private PaymentDao paymentDao;

    @Mock
    private PaymentHistoryDao paymentHistoryDao;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    @Test
    void getPaymentHistoryReturnsMappedHistoryInOrder() {
        Long paymentId = 1L;
        Payment payment = Payment.builder().id(paymentId).status("VALIDATED").build();
        LocalDateTime createdAt = LocalDateTime.of(2026, 7, 27, 10, 0, 0);
        LocalDateTime validatedAt = createdAt.plusSeconds(10);
        List<PaymentHistory> histories = List.of(
            PaymentHistory.builder()
                .paymentId(paymentId)
                .fromStatus(null)
                .toStatus("CREATED")
                .reason("Payment created")
                .triggeredBy("API")
                .createdAt(createdAt)
                .build(),
            PaymentHistory.builder()
                .paymentId(paymentId)
                .fromStatus("CREATED")
                .toStatus("VALIDATED")
                .reason("Validation passed")
                .triggeredBy("SCHEDULER")
                .createdAt(validatedAt)
                .build()
        );

        when(paymentDao.findById(paymentId)).thenReturn(payment);
        when(paymentHistoryDao.findByPaymentId(paymentId)).thenReturn(histories);

        List<HistoryResponse> result = paymentService.getPaymentHistory(paymentId);

        assertEquals(2, result.size());
        assertEquals("CREATED", result.get(0).getToStatus());
        assertEquals("VALIDATED", result.get(1).getToStatus());
        assertEquals("SCHEDULER", result.get(1).getTriggeredBy());
        assertEquals(validatedAt, result.get(1).getCreatedAt());
        verify(paymentDao).findById(paymentId);
        verify(paymentHistoryDao).findByPaymentId(paymentId);
    }

    @Test
    void getPaymentHistoryReturnsEmptyListForExistingPaymentWithoutHistory() {
        Long paymentId = 2L;

        when(paymentDao.findById(paymentId)).thenReturn(Payment.builder().id(paymentId).status("CREATED").build());
        when(paymentHistoryDao.findByPaymentId(paymentId)).thenReturn(List.of());

        List<HistoryResponse> result = paymentService.getPaymentHistory(paymentId);

        assertEquals(List.of(), result);
        verify(paymentHistoryDao).findByPaymentId(paymentId);
    }

    @Test
    void getPaymentHistoryThrowsWhenPaymentDoesNotExist() {
        Long paymentId = 999L;
        when(paymentDao.findById(paymentId)).thenReturn(null);

        PaymentNotFoundException exception = assertThrows(
            PaymentNotFoundException.class,
            () -> paymentService.getPaymentHistory(paymentId)
        );

        assertEquals("PAYMENT_NOT_FOUND", exception.getErrorCode());
        verify(paymentDao).findById(paymentId);
    }
}

