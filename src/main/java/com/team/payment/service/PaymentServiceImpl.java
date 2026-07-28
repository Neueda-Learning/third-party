package com.team.payment.service;

import com.team.payment.dao.PaymentDao;
import com.team.payment.dao.PaymentHistoryDao;
import com.team.payment.dto.CreatePaymentRequest;
import com.team.payment.dto.PaymentResponse;
import com.team.payment.entity.Payment;
import com.team.payment.entity.PaymentHistory;
import com.team.payment.entity.PaymentStatus;
import com.team.payment.exception.PaymentException;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Currency;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 支付业务服务实现
 * 主要包含：创建支付、参数校验、幂等性检查、状态管理等
 */
@Service
public class PaymentServiceImpl implements PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentServiceImpl.class);
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("1000000");
    private static final Pattern ACCOUNT_PATTERN = Pattern.compile("^[A-Za-z0-9]{4,32}$");
    private static final Set<String> ZERO_DECIMAL_CURRENCIES = Set.of("JPY", "KRW", "VND");
    private static final Set<String> ISO_4217_CURRENCY_CODES = Currency.getAvailableCurrencies().stream()
            .map(Currency::getCurrencyCode)
            .collect(Collectors.toUnmodifiableSet());

    @Autowired
    private PaymentDao paymentDao;

    @Autowired
    private PaymentHistoryDao paymentHistoryDao;

    @Value("${payment.failure-ratio.create:0.0}")
    private double createFailureRatio;

    @Value("${payment.failure-ratio.validate:0.0}")
    private double validateFailureRatio;

    @Value("${payment.failure-ratio.send:0.0}")
    private double sendFailureRatio;

    @Value("${payment.failure-ratio.complete:0.0}")
    private double completeFailureRatio;

    @PostConstruct
    public void onInit() {
        log.info("PaymentServiceImpl initialized");
    }

    @Override
    public PaymentResponse createPayment(CreatePaymentRequest request, String idempotencyKey) {
        log.info("Creating payment in service. idempotencyKey={}, request={}", idempotencyKey, request);

        PaymentException validationError = validatePayment(request, idempotencyKey);
        if (validationError != null) {
            return PaymentResponse.builder()
                    .idempotencyKey(idempotencyKey)
                    .sourceAccount(request != null ? request.getSourceAccount() : null)
                    .destinationAccount(request != null ? request.getDestinationAccount() : null)
                    .amount(request != null ? request.getAmount() : null)
                    .currency(request != null && request.getCurrency() != null && !request.getCurrency().isBlank() ? request.getCurrency() : "CNY")
                    .status(PaymentStatus.FAILED.name())
                    .errorCode(validationError.getErrorCode())
                    .errorMessage(validationError.getMessage())
                    .reference(request != null ? request.getReference() : null)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
        }

        Payment existing = paymentDao.findByIdempotencyKey(idempotencyKey);
        if (existing != null) {
            return PaymentResponse.fromEntity(existing);
        }

        String normalizedCurrency = request.getCurrency() == null || request.getCurrency().isBlank()
                ? "CNY"
                : request.getCurrency().trim().toUpperCase();

        LocalDateTime now = LocalDateTime.now();
        Payment payment = Payment.builder()
                .idempotencyKey(idempotencyKey)
                .sourceAccount(request.getSourceAccount().trim())
                .destinationAccount(request.getDestinationAccount().trim())
                .amount(request.getAmount())
                .currency(normalizedCurrency)
                .status(PaymentStatus.CREATED.name())
                .reference(request.getReference())
                .createdAt(now)
                .updatedAt(now)
                .build();

        Payment created = paymentDao.create(payment);

        saveHistory(created.getId(), null, PaymentStatus.CREATED.name(), "Payment created", "API");

        if (shouldFail(createFailureRatio)) {
            failPaymentInternal(created.getId(), "CREATE_STEP_FAILED", "Create step failed accidentally", "SYSTEM");
        } else {
            PaymentResponse validated = validatePayment(created.getId());
            if (!PaymentStatus.FAILED.name().equals(validated.getStatus())) {
                PaymentResponse sent = sendPayment(created.getId());
                if (!PaymentStatus.FAILED.name().equals(sent.getStatus())) {
                    completePayment(created.getId());
                }
            }
        }

// 创建接口始终返回“创建成功时”的快照
        return PaymentResponse.fromEntity(created);
    }

    @Override
    public PaymentResponse validatePayment(Long paymentId) {
        Payment payment = getRequiredPayment(paymentId);
        return advanceOrFailByRatio(
                payment,
                PaymentStatus.CREATED,
                PaymentStatus.VALIDATED,
                validateFailureRatio,
                "VALIDATE_STEP_FAILED",
                "Validation failed accidentally",
                "Validation passed"
        );
    }

    @Override
    public PaymentResponse sendPayment(Long paymentId) {
        Payment payment = getRequiredPayment(paymentId);
        return advanceOrFailByRatio(
                payment,
                PaymentStatus.VALIDATED,
                PaymentStatus.SENT,
                sendFailureRatio,
                "SEND_STEP_FAILED",
                "Send failed accidentally",
                "Payment sent"
        );
    }

    @Override
    public PaymentResponse completePayment(Long paymentId) {
        Payment payment = getRequiredPayment(paymentId);
        return advanceOrFailByRatio(
                payment,
                PaymentStatus.SENT,
                PaymentStatus.COMPLETED,
                completeFailureRatio,
                "COMPLETE_STEP_FAILED",
                "Complete failed by ratio",
                "Payment completed"
        );
    }

    @Override
    public PaymentResponse failPayment(Long paymentId, String errorCode, String errorMessage) {
        return failPaymentInternal(paymentId, errorCode, errorMessage, "API");
    }

    private PaymentResponse advanceOrFailByRatio(
            Payment payment,
            PaymentStatus expectedFrom,
            PaymentStatus targetStatus,
            double failureRatio,
            String stepFailureCode,
            String stepFailureMessage,
            String successReason
    ) {
        PaymentStatus currentStatus = PaymentStatus.fromValue(payment.getStatus());

        if (currentStatus == PaymentStatus.FAILED || currentStatus == PaymentStatus.COMPLETED) {
            return PaymentResponse.fromEntity(payment);
        }

        if (currentStatus != expectedFrom) {
            throw new PaymentException(
                    "INVALID_STATUS_TRANSITION",
                    "Cannot move payment from " + payment.getStatus() + " to " + targetStatus.name()
            );
        }

        if (shouldFail(failureRatio)) {
            return failPaymentInternal(payment.getId(), stepFailureCode, stepFailureMessage, "SYSTEM");
        }

        paymentDao.updateStatus(payment.getId(), targetStatus.name(), null, null);
        saveHistory(payment.getId(), expectedFrom.name(), targetStatus.name(), successReason, "SYSTEM");
        return PaymentResponse.fromEntity(getRequiredPayment(payment.getId()));
    }

    private PaymentResponse failPaymentInternal(Long paymentId, String errorCode, String errorMessage, String triggeredBy) {
        Payment payment = getRequiredPayment(paymentId);
        PaymentStatus currentStatus = PaymentStatus.fromValue(payment.getStatus());

        if (currentStatus == PaymentStatus.FAILED) {
            return PaymentResponse.fromEntity(payment);
        }

        if (currentStatus == PaymentStatus.COMPLETED) {
            throw new PaymentException("INVALID_STATUS_TRANSITION", "Completed payment cannot be failed");
        }

        String safeErrorCode = (errorCode == null || errorCode.isBlank()) ? "PROCESSING_ERROR" : errorCode;
        String safeErrorMessage = (errorMessage == null || errorMessage.isBlank()) ? "Payment failed" : errorMessage;

        paymentDao.updateStatus(paymentId, PaymentStatus.FAILED.name(), safeErrorCode, safeErrorMessage);
        saveHistory(paymentId, payment.getStatus(), PaymentStatus.FAILED.name(), safeErrorMessage, triggeredBy);
        return PaymentResponse.fromEntity(getRequiredPayment(paymentId));
    }

    private Payment getRequiredPayment(Long paymentId) {
        Payment payment = paymentDao.findById(paymentId);
        if (payment == null) {
            throw new PaymentException("PAYMENT_NOT_FOUND", "Payment not found: " + paymentId);
        }
        return payment;
    }

    private boolean shouldFail(double ratio) {
        double normalizedRatio = Math.max(0D, Math.min(1D, ratio));
        return ThreadLocalRandom.current().nextDouble() < normalizedRatio;
    }

    private void saveHistory(Long paymentId, String fromStatus, String toStatus, String reason, String triggeredBy) {
        paymentHistoryDao.insert(PaymentHistory.builder()
                .paymentId(paymentId)
                .fromStatus(fromStatus)
                .toStatus(toStatus)
                .reason(reason)
                .triggeredBy(triggeredBy)
                .createdAt(LocalDateTime.now())
                .build());
    }

    private PaymentException validatePayment(CreatePaymentRequest request, String idempotencyKey) {
        if (request == null) {
            return new PaymentException("INVALID_REQUEST", "Request must not be null");
        }

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return new PaymentException("INVALID_IDEMPOTENCY_KEY", "Idempotency key must not be blank");
        }

        String normalizedIdempotencyKey = idempotencyKey.trim();
        Payment existingPayment = paymentDao.findByIdempotencyKey(normalizedIdempotencyKey);
        if (existingPayment != null) {
            return new PaymentException("DUPLICATE_IDEMPOTENCY_KEY", "Idempotency key already exists: " + normalizedIdempotencyKey);
        }

        String sourceAccount = request.getSourceAccount();
        String destinationAccount = request.getDestinationAccount();
        BigDecimal amount = request.getAmount();
        String currency = request.getCurrency();

        if (sourceAccount == null || sourceAccount.isBlank()) {
            return new PaymentException("INVALID_SOURCE_ACCOUNT", "Source account must not be blank");
        }
        if (destinationAccount == null || destinationAccount.isBlank()) {
            return new PaymentException("INVALID_DESTINATION_ACCOUNT", "Destination account must not be blank");
        }

        String normalizedSource = sourceAccount.trim();
        String normalizedDestination = destinationAccount.trim();

        if (normalizedSource.equals(normalizedDestination)) {
            return new PaymentException("INVALID_ACCOUNT", "Source and destination accounts must not be same");
        }

        if (!ACCOUNT_PATTERN.matcher(normalizedSource).matches()) {
            return new PaymentException("INVALID_SOURCE_ACCOUNT_FORMAT", "Source account format is invalid");
        }
        if (!ACCOUNT_PATTERN.matcher(normalizedDestination).matches()) {
            return new PaymentException("INVALID_DESTINATION_ACCOUNT_FORMAT", "Destination account format is invalid");
        }

        if (amount == null) {
            return new PaymentException("INVALID_AMOUNT", "Amount must not be null");
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return new PaymentException("INVALID_AMOUNT", "Amount must be greater than 0");
        }
        if (amount.compareTo(MAX_AMOUNT) > 0) {
            return new PaymentException("AMOUNT_EXCEEDS_LIMIT", "Amount must not exceed 1000000");
        }

        String normalizedCurrency = (currency == null || currency.isBlank()) ? "CNY" : currency.trim().toUpperCase();
        if (!isSupportedCurrency(normalizedCurrency)) {
            return new PaymentException("INVALID_CURRENCY", "Currency is invalid or not supported: " + normalizedCurrency);
        }

        int scale = Math.max(amount.stripTrailingZeros().scale(), 0);
        int allowedScale = ZERO_DECIMAL_CURRENCIES.contains(normalizedCurrency) ? 0 : 2;
        if (scale > allowedScale) {
            return new PaymentException(
                    "INVALID_AMOUNT_SCALE",
                    "Amount has too many decimal places for currency " + normalizedCurrency + ", allowed: " + allowedScale
            );
        }

        return null;
    }

    private boolean isSupportedCurrency(String currencyCode) {
        return ISO_4217_CURRENCY_CODES.contains(currencyCode);
    }
}
