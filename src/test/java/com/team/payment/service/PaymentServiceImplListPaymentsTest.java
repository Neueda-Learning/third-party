package com.team.payment.service;

import com.team.payment.dao.PaymentDao;
import com.team.payment.dto.PaymentListResponse;
import com.team.payment.dto.PaymentResponse;
import com.team.payment.entity.Payment;
import com.team.payment.exception.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplListPaymentsTest {

    @Mock
    private PaymentDao paymentDao;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    @Test
    void listPayments_shouldReturnPagedData_withNormalizedStatus() {
        Payment payment = Payment.builder()
            .id(10L)
            .idempotencyKey("idem-10")
            .sourceAccount("ACC001")
            .destinationAccount("ACC002")
            .amount(new BigDecimal("88.66"))
            .currency("CNY")
            .status("CREATED")
            .reference("INV-10")
            .createdAt(LocalDateTime.of(2026, 7, 28, 10, 0))
            .updatedAt(LocalDateTime.of(2026, 7, 28, 10, 1))
            .build();

        when(paymentDao.findPaginated(0, 20, "CREATED")).thenReturn(List.of(payment));
        when(paymentDao.countByStatus("CREATED")).thenReturn(21L);

        PaymentListResponse response = paymentService.listPayments(0, 20, "  created ");

        assertEquals(1, response.getContent().size());
        PaymentResponse item = response.getContent().get(0);
        assertEquals(10L, item.getId());
        assertEquals("idem-10", item.getIdempotencyKey());
        assertEquals("CREATED", item.getStatus());

        assertEquals(21L, response.getTotalElements());
        assertEquals(2, response.getTotalPages());
        assertEquals(0, response.getCurrentPage());
        assertEquals(20, response.getPageSize());

        verify(paymentDao).findPaginated(0, 20, "CREATED");
        verify(paymentDao).countByStatus("CREATED");
    }

    @Test
    void listPayments_shouldAllowEmptyStatus() {
        when(paymentDao.findPaginated(1, 2, null)).thenReturn(List.of());
        when(paymentDao.countByStatus(null)).thenReturn(0L);

        PaymentListResponse response = paymentService.listPayments(1, 2, "");

        assertEquals(0, response.getContent().size());
        assertEquals(0L, response.getTotalElements());
        assertEquals(0, response.getTotalPages());
        assertEquals(1, response.getCurrentPage());
        assertEquals(2, response.getPageSize());

        verify(paymentDao).findPaginated(1, 2, null);
        verify(paymentDao).countByStatus(null);
    }

    @Test
    void listPayments_shouldAllowNullStatus() {
        Payment payment = Payment.builder()
            .id(11L)
            .idempotencyKey("idem-11")
            .sourceAccount("ACC011")
            .destinationAccount("ACC012")
            .amount(new BigDecimal("18.88"))
            .currency("CNY")
            .status("SENT")
            .reference("INV-11")
            .createdAt(LocalDateTime.of(2026, 7, 28, 11, 0))
            .updatedAt(LocalDateTime.of(2026, 7, 28, 11, 1))
            .build();

        when(paymentDao.findPaginated(0, 10, null)).thenReturn(List.of(payment));
        when(paymentDao.countByStatus(null)).thenReturn(1L);

        PaymentListResponse response = paymentService.listPayments(0, 10, null);

        assertEquals(1, response.getContent().size());
        assertEquals("SENT", response.getContent().get(0).getStatus());
        assertEquals(1L, response.getTotalElements());
        assertEquals(1, response.getTotalPages());
        verify(paymentDao).findPaginated(0, 10, null);
        verify(paymentDao).countByStatus(null);
    }

    @Test
    void listPayments_shouldCalculateTotalPages_whenExactlyDivisible() {
        when(paymentDao.findPaginated(1, 10, "FAILED")).thenReturn(List.of());
        when(paymentDao.countByStatus("FAILED")).thenReturn(20L);

        PaymentListResponse response = paymentService.listPayments(1, 10, "failed");

        assertEquals(20L, response.getTotalElements());
        assertEquals(2, response.getTotalPages());
        assertEquals(1, response.getCurrentPage());
        assertEquals(10, response.getPageSize());
        verify(paymentDao).findPaginated(1, 10, "FAILED");
        verify(paymentDao).countByStatus("FAILED");
    }

    @Test
    void listPayments_shouldThrowWhenPageNegative() {
        ValidationException ex = assertThrows(ValidationException.class,
            () -> paymentService.listPayments(-1, 20, null));

        assertEquals("VALIDATION_FAILED", ex.getErrorCode());
        assertEquals("page 不能小于 0", ex.getMessage());
        verify(paymentDao, never()).findPaginated(0, 20, null);
    }

    @Test
    void listPayments_shouldThrowWhenSizeNotPositive() {
        ValidationException ex = assertThrows(ValidationException.class,
            () -> paymentService.listPayments(0, 0, null));

        assertEquals("VALIDATION_FAILED", ex.getErrorCode());
        assertEquals("size 必须大于 0", ex.getMessage());
        verify(paymentDao, never()).findPaginated(0, 0, null);
    }

    @Test
    void listPayments_shouldThrowWhenStatusInvalid() {
        ValidationException ex = assertThrows(ValidationException.class,
            () -> paymentService.listPayments(0, 20, "INVALID"));

        assertEquals("VALIDATION_FAILED", ex.getErrorCode());
        assertEquals("status 必须是 CREATED、VALIDATED、SENT、COMPLETED、FAILED 之一", ex.getMessage());
        verify(paymentDao, never()).countByStatus("INVALID");
    }
}

