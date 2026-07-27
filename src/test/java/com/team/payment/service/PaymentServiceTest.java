package com.team.payment.service;

import com.team.payment.dao.PaymentDao;
import com.team.payment.dao.PaymentHistoryDao;
import com.team.payment.dto.CreatePaymentRequest;
import com.team.payment.dto.PaymentResponse;
import com.team.payment.entity.Payment;
import com.team.payment.exception.PaymentException;
import com.team.payment.exception.PaymentNotFoundException;
import com.team.payment.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 支付服务单元测试
 * 成员A负责幂等性和创建相关测试
 * 成员B负责状态机相关测试
 * 成员C负责查询相关测试
 */
@ExtendWith(MockitoExtension.class)
public class PaymentServiceTest {

    @Mock
    private PaymentDao paymentDao;

    @Mock
    private PaymentHistoryDao paymentHistoryDao;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    private CreatePaymentRequest validRequest;

    @BeforeEach
    public void setUp() {
        validRequest = CreatePaymentRequest.builder()
            .sourceAccount("ACC001")
            .destinationAccount("ACC002")
            .amount(new BigDecimal("1500.50"))
            .currency("CNY")
            .reference("INV-20260727")
            .build();
    }

    // ===== 创建支付相关测试 =====

    /**
     * P0: 创建支付 - 正常路径
     */
    @Test
    public void testCreatePayment_Success() {
        when(paymentDao.findByIdempotencyKey("key-001")).thenReturn(null);
        when(paymentDao.create(any(Payment.class))).thenAnswer(invocation -> {
            Payment p = invocation.getArgument(0);
            p.setId(1L);
            p.setCreatedAt(LocalDateTime.now());
            p.setUpdatedAt(LocalDateTime.now());
            return p;
        });

        PaymentResponse response = paymentService.createPayment(validRequest, "key-001");

        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals("CREATED", response.getStatus());
        assertEquals("CNY", response.getCurrency());
        verify(paymentDao, times(1)).create(any(Payment.class));
        verify(paymentHistoryDao, times(1)).insert(any());
    }

    /**
     * P0: 幂等键重复 - 内容一致，返回200
     */
    @Test
    public void testCreatePayment_IdempotentKeyDuplicate_ContentMatch() {
        Payment existingPayment = Payment.builder()
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

        when(paymentDao.findByIdempotencyKey("key-001")).thenReturn(existingPayment);

        PaymentResponse response = paymentService.createPayment(validRequest, "key-001");

        assertNotNull(response);
        assertEquals(1L, response.getId());
        verify(paymentDao, never()).create(any(Payment.class));
    }

    /**
     * P0: 幂等键重复 - 内容不一致，返回409
     */
    @Test
    public void testCreatePayment_IdempotentKeyConflict() {
        Payment existingPayment = Payment.builder()
            .id(1L)
            .idempotencyKey("key-001")
            .sourceAccount("ACC001")
            .destinationAccount("ACC002")
            .amount(new BigDecimal("999.99"))  // 不同的金额
            .currency("CNY")
            .status("CREATED")
            .build();

        when(paymentDao.findByIdempotencyKey("key-001")).thenReturn(existingPayment);

        assertThrows(PaymentException.class, () -> {
            paymentService.createPayment(validRequest, "key-001");
        });
    }

    /**
     * P0: 金额校验 - 金额≤0拒绝
     */
    @Test
    public void testCreatePayment_InvalidAmount_ZeroOrNegative() {
        CreatePaymentRequest invalidRequest = CreatePaymentRequest.builder()
            .sourceAccount("ACC001")
            .destinationAccount("ACC002")
            .amount(new BigDecimal("0"))
            .currency("CNY")
            .build();

        assertThrows(ValidationException.class, () -> {
            paymentService.createPayment(invalidRequest, "key-001");
        });
    }

    /**
     * P0: 币种校验 - 非CNY拒绝
     */
    @Test
    public void testCreatePayment_InvalidCurrency() {
        CreatePaymentRequest invalidRequest = CreatePaymentRequest.builder()
            .sourceAccount("ACC001")
            .destinationAccount("ACC002")
            .amount(new BigDecimal("1500.50"))
            .currency("USD")  // 非CNY
            .build();

        assertThrows(ValidationException.class, () -> {
            paymentService.createPayment(invalidRequest, "key-001");
        });
    }

    /**
     * P0: 账户校验 - 源目账户相同拒绝
     */
    @Test
    public void testCreatePayment_SameSourceAndDestinationAccount() {
        CreatePaymentRequest invalidRequest = CreatePaymentRequest.builder()
            .sourceAccount("ACC001")
            .destinationAccount("ACC001")  // 相同
            .amount(new BigDecimal("1500.50"))
            .currency("CNY")
            .build();

        assertThrows(ValidationException.class, () -> {
            paymentService.createPayment(invalidRequest, "key-001");
        });
    }

    // ===== 查询相关测试 =====

    /**
     * P0: 查询支付 - 存在返回200
     */
    @Test
    public void testGetPaymentDetail_Found() {
        Payment payment = Payment.builder()
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

        when(paymentDao.findById(1L)).thenReturn(payment);

        PaymentResponse response = paymentService.getPaymentDetail(1L);

        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals("CREATED", response.getStatus());
    }

    /**
     * P0: 查询支付 - 不存在返回404
     */
    @Test
    public void testGetPaymentDetail_NotFound() {
        when(paymentDao.findById(999L)).thenReturn(null);

        assertThrows(PaymentNotFoundException.class, () -> {
            paymentService.getPaymentDetail(999L);
        });
    }

    // ===== 状态机相关测试 =====

    /**
     * P0: 状态迁移 - 合法迁移
     */
    @Test
    public void testValidatePayment_Success() {
        Payment payment = Payment.builder()
            .id(1L)
            .status("CREATED")
            .build();

        when(paymentDao.findById(1L)).thenReturn(payment);
        when(paymentDao.updateStatus(eq(1L), eq("VALIDATED"), any(), any())).thenReturn(1);
        when(paymentDao.findById(1L)).thenReturn(
            Payment.builder()
                .id(1L)
                .status("VALIDATED")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build()
        );

        PaymentResponse response = paymentService.validatePayment(1L);

        assertEquals("VALIDATED", response.getStatus());
        verify(paymentDao, times(2)).findById(1L);
        verify(paymentDao, times(1)).updateStatus(eq(1L), eq("VALIDATED"), any(), any());
    }

    /**
     * P0: 状态迁移 - 非法迁移
     */
    @Test
    public void testValidatePayment_InvalidTransition() {
        Payment payment = Payment.builder()
            .id(1L)
            .status("COMPLETED")  // 已完成，不能再转移
            .build();

        when(paymentDao.findById(1L)).thenReturn(payment);

        assertThrows(Exception.class, () -> {
            paymentService.validatePayment(1L);
        });
    }
}

