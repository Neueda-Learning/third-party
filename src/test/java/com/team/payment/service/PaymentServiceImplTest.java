package com.team.payment.service;

import com.team.payment.dao.PaymentDao;
import com.team.payment.dao.PaymentHistoryDao;
import com.team.payment.dto.CreatePaymentRequest;
import com.team.payment.dto.PaymentResponse;
import com.team.payment.entity.Payment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock
    private PaymentDao paymentDao;

    @Mock
    private PaymentHistoryDao paymentHistoryDao;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    @Test
    void shouldFailWhenRequestIsNull() {
        PaymentResponse response = paymentService.createPayment(null, "idem-1");

        assertEquals("FAILED", response.getStatus());
        assertEquals("INVALID_REQUEST", response.getErrorCode());
        verify(paymentDao, never()).create(any());
        verify(paymentHistoryDao, never()).insert(any());
    }

    @Test
    void shouldFailWhenAccountsAreSame() {
        CreatePaymentRequest request = validRequestBuilder()
                .sourceAccount("ACCOUNT001")
                .destinationAccount("ACCOUNT001")
                .build();

        PaymentResponse response = paymentService.createPayment(request, "idem-2");

        assertEquals("FAILED", response.getStatus());
        assertEquals("INVALID_ACCOUNT", response.getErrorCode());
        verify(paymentDao, never()).create(any());
        verify(paymentHistoryDao, never()).insert(any());
    }

    @Test
    void shouldFailWhenSourceAccountFormatInvalid() {
        CreatePaymentRequest request = validRequestBuilder()
                .sourceAccount("abc-12")
                .build();

        PaymentResponse response = paymentService.createPayment(request, "idem-3");

        assertEquals("FAILED", response.getStatus());
        assertEquals("INVALID_SOURCE_ACCOUNT_FORMAT", response.getErrorCode());
        verify(paymentDao, never()).create(any());
        verify(paymentHistoryDao, never()).insert(any());
    }

    @Test
    void shouldFailWhenAmountExceedsLimit() {
        CreatePaymentRequest request = validRequestBuilder()
                .amount(new BigDecimal("1000000.01"))
                .build();

        PaymentResponse response = paymentService.createPayment(request, "idem-4");

        assertEquals("FAILED", response.getStatus());
        assertEquals("AMOUNT_EXCEEDS_LIMIT", response.getErrorCode());
        verify(paymentDao, never()).create(any());
        verify(paymentHistoryDao, never()).insert(any());
    }

    @Test
    void shouldFailWhenDefaultCurrencyHasMoreThanTwoDecimals() {
        CreatePaymentRequest request = validRequestBuilder()
                .currency("CNY")
                .amount(new BigDecimal("1.999"))
                .build();

        PaymentResponse response = paymentService.createPayment(request, "idem-5");

        assertEquals("FAILED", response.getStatus());
        assertEquals("INVALID_AMOUNT_SCALE", response.getErrorCode());
        verify(paymentDao, never()).create(any());
        verify(paymentHistoryDao, never()).insert(any());
    }

    @Test
    void shouldFailWhenZeroDecimalCurrencyHasFraction() {
        CreatePaymentRequest request = validRequestBuilder()
                .currency("JPY")
                .amount(new BigDecimal("100.10"))
                .build();

        PaymentResponse response = paymentService.createPayment(request, "idem-6");

        assertEquals("FAILED", response.getStatus());
        assertEquals("INVALID_AMOUNT_SCALE", response.getErrorCode());
        verify(paymentDao, never()).create(any());
        verify(paymentHistoryDao, never()).insert(any());
    }

    @Test
    void shouldReturnCreatedSnapshotWhenRequestValid() {
        mockAutoLifecycle("CNY");
        CreatePaymentRequest request = validRequestBuilder().build();

        PaymentResponse response = paymentService.createPayment(request, "idem-7");

        assertEquals("CREATED", response.getStatus());
        assertNull(response.getErrorCode());
        assertNull(response.getErrorMessage());
        assertEquals("CNY", response.getCurrency());
        verify(paymentDao, times(1)).create(any());
        verify(paymentHistoryDao, times(4)).insert(any());
    }

    @Test
    void shouldFailWhenCurrencyIsNotIso4217() {
        CreatePaymentRequest request = validRequestBuilder()
                .currency("CNYY")
                .build();

        PaymentResponse response = paymentService.createPayment(request, "idem-8");

        assertEquals("FAILED", response.getStatus());
        assertEquals("INVALID_CURRENCY", response.getErrorCode());
        verify(paymentDao, never()).create(any());
        verify(paymentHistoryDao, never()).insert(any());
    }

    @Test
    void shouldAcceptLowercaseIso4217CurrencyAndReturnCreatedSnapshot() {
        mockAutoLifecycle("USD");
        CreatePaymentRequest request = validRequestBuilder()
                .currency("usd")
                .build();

        PaymentResponse response = paymentService.createPayment(request, "idem-9");

        assertEquals("CREATED", response.getStatus());
        assertEquals("USD", response.getCurrency());
        verify(paymentDao, times(1)).create(any());
        verify(paymentHistoryDao, times(4)).insert(any());
    }

    private void mockAutoLifecycle(String currency) {
        Payment created = Payment.builder().id(1L).status("CREATED").currency(currency).build();
        Payment validated = Payment.builder().id(1L).status("VALIDATED").currency(currency).build();
        Payment sent = Payment.builder().id(1L).status("SENT").currency(currency).build();
        Payment completed = Payment.builder().id(1L).status("COMPLETED").currency(currency).build();

        org.mockito.Mockito.when(paymentDao.findByIdempotencyKey(any())).thenReturn(null);
        org.mockito.Mockito.when(paymentDao.create(any())).thenReturn(created);
        org.mockito.Mockito.when(paymentDao.findById(1L)).thenReturn(
                created,
                validated,
                validated,
                sent,
                sent,
                completed
        );
    }

    private CreatePaymentRequest.CreatePaymentRequestBuilder validRequestBuilder() {
        return CreatePaymentRequest.builder()
                .sourceAccount("SRCACC001")
                .destinationAccount("DSTACC001")
                .amount(new BigDecimal("99.99"))
                .currency("CNY")
                .reference("test");
    }
}
