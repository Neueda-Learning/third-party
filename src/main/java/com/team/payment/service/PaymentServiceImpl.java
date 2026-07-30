package com.team.payment.service;

import com.team.payment.dao.AccountDao;
import com.team.payment.dao.PaymentDao;
import com.team.payment.dao.PaymentHistoryDao;
import com.team.payment.entity.Account;
import com.team.payment.dto.CreatePaymentRequest;
import com.team.payment.dto.HistoryResponse;
import com.team.payment.dto.PaymentListResponse;
import com.team.payment.dto.PaymentResponse;
import com.team.payment.entity.PaymentStatus;
import com.team.payment.entity.Payment;
import com.team.payment.entity.PaymentHistory;
import com.team.payment.exception.*;
import com.team.payment.exception.PaymentException;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.util.List;
import java.util.Locale;
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

    @Autowired
    private PaymentDao paymentDao;

    @Autowired
    private PaymentHistoryDao paymentHistoryDao;

    @Autowired
    private AccountDao accountDao;


    @Override
    @Transactional(readOnly = true)
    public List<HistoryResponse> getPaymentHistory(Long paymentId) {
        Payment payment = paymentDao.findById(paymentId);
        if (payment == null) {
            throw new PaymentNotFoundException(paymentId);
        }

        return paymentHistoryDao.findByPaymentId(paymentId).stream()
            .map(this::toHistoryResponse)
            .collect(Collectors.toList());
    }

    private HistoryResponse toHistoryResponse(PaymentHistory history) {
        return HistoryResponse.builder()
            .fromStatus(history.getFromStatus())
            .toStatus(history.getToStatus())
            .reason(history.getReason())
            .triggeredBy(history.getTriggeredBy())
            .createdAt(history.getCreatedAt())
            .build();
    }

	private PaymentResponse toResponse(Payment payment) {
		if (payment == null) {
			return null;
		}

		return PaymentResponse.builder()
			.id(payment.getId())
			.idempotencyKey(payment.getIdempotencyKey())
			.sourceAccount(payment.getSourceAccount())
			.destinationAccount(payment.getDestinationAccount())
			.amount(payment.getAmount())
			.currency(payment.getCurrency())
			.status(payment.getStatus())
			.errorCode(payment.getErrorCode())
			.errorMessage(payment.getErrorMessage())
			.reference(payment.getReference())
			.createdAt(payment.getCreatedAt())
			.updatedAt(payment.getUpdatedAt())
			.build();
	}

	@Override
	public PaymentListResponse listPayments(int page, int size, String status) {
		if (page < 0) {
			throw new ValidationException("page 不能小于 0");
		}
		if (size <= 0) {
			throw new ValidationException("size 必须大于 0");
		}

		String normalizedStatus = null;
		if (status != null && !status.isBlank()) {
			normalizedStatus = status.trim().toUpperCase(Locale.ROOT);
			if (PaymentStatus.fromValue(normalizedStatus) == null) {
				throw new ValidationException("status 必须是 CREATED、VALIDATED、SENT、COMPLETED、FAILED 之一");
			}
		}

		List<Payment> payments = paymentDao.findPaginated(page, size, normalizedStatus);
		long totalElements = paymentDao.countByStatus(normalizedStatus);
		int totalPages = (int) ((totalElements + size - 1) / size);

		return PaymentListResponse.builder()
			.content(payments.stream().map(this::toResponse).collect(Collectors.toList()))
			.totalElements(totalElements)
			.totalPages(totalPages)
			.currentPage(page)
			.pageSize(size)
			.build();
	}




    @Override
    public PaymentResponse getPaymentDetail(Long paymentId) {
        Payment payment = paymentDao.findById(paymentId);
        if (payment == null) {
            throw new PaymentNotFoundException(paymentId);
        }

        return PaymentResponse.builder()
                .id(payment.getId())
                .idempotencyKey(payment.getIdempotencyKey())
                .sourceAccount(payment.getSourceAccount())
                .destinationAccount(payment.getDestinationAccount())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .status(payment.getStatus())
                .errorCode(payment.getErrorCode())
                .errorMessage(payment.getErrorMessage())
                .reference(payment.getReference())
                .createdAt(payment.getCreatedAt())
                .updatedAt(payment.getUpdatedAt())
                .build();
    }




    private static final Logger log = LoggerFactory.getLogger(PaymentServiceImpl.class);
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("1000000");
    private static final Pattern ACCOUNT_PATTERN = Pattern.compile("^[A-Za-z0-9]{4,32}$");
    private static final Set<String> ZERO_DECIMAL_CURRENCIES = Set.of("JPY", "KRW", "VND");
    private static final Set<String> ISO_4217_CURRENCY_CODES = Currency.getAvailableCurrencies().stream()
            .map(Currency::getCurrencyCode)
            .collect(Collectors.toUnmodifiableSet());





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

        PaymentException validationError = validateCreateRequest(request, idempotencyKey);
        if (validationError != null) {
            return buildValidationFailureResponse(request, idempotencyKey, validationError);
        }

        Payment existing = paymentDao.findByIdempotencyKey(idempotencyKey);
        if (existing != null) {
            return PaymentResponse.fromEntity(existing);
        }

        Payment created = persistCreatedPayment(request, idempotencyKey);
        runAutoLifecycleAfterCreate(created.getId());

// 创建接口始终返回“创建成功时”的快照
        return PaymentResponse.fromEntity(created);
    }

    private PaymentResponse buildValidationFailureResponse(
            CreatePaymentRequest request,
            String idempotencyKey,
            PaymentException validationError
    ) {
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

    private Payment persistCreatedPayment(CreatePaymentRequest request, String idempotencyKey) {
        LocalDateTime now = LocalDateTime.now();

        // 若前端未传 sourceAccountId / destinationAccountId，则按账户名自动查询补全
        Long sourceAccountId = request.getSourceAccountId();
        Long destinationAccountId = request.getDestinationAccountId();
        if (sourceAccountId == null) {
            Account src = accountDao.findByAccountName(request.getSourceAccount().trim());
            if (src != null) sourceAccountId = src.getId();
        }
        if (destinationAccountId == null) {
            Account dst = accountDao.findByAccountName(request.getDestinationAccount().trim());
            if (dst != null) destinationAccountId = dst.getId();
        }

        Payment created = paymentDao.create(Payment.builder()
                .idempotencyKey(idempotencyKey)
                .sourceAccount(request.getSourceAccount().trim())
                .destinationAccount(request.getDestinationAccount().trim())
                .fromAccountId(sourceAccountId)
                .toAccountId(destinationAccountId)
                .exchangeRate(request.getExchangeRate())
                .amount(request.getAmount())
                .currency(normalizeCurrency(request.getCurrency()))
                .status(PaymentStatus.CREATED.name())
                .reference(request.getReference())
                .createdAt(now)
                .updatedAt(now)
                .build());

        saveHistory(created.getId(), null, PaymentStatus.CREATED.name(), "Payment created", "API");
        return created;
    }

    private void runAutoLifecycleAfterCreate(Long paymentId) {
        if (shouldFail(createFailureRatio)) {
            failPaymentInternal(paymentId, "CREATE_STEP_FAILED", "Create step failed accidentally", "SYSTEM");
            return;
        }

        PaymentResponse validated = validatePayment(paymentId);
        if (PaymentStatus.FAILED.name().equals(validated.getStatus())) {
            return;
        }

        PaymentResponse sent = sendPayment(paymentId);
        if (PaymentStatus.FAILED.name().equals(sent.getStatus())) {
            return;
        }

        completePayment(paymentId);
    }

    private String normalizeCurrency(String currency) {
        return currency == null || currency.isBlank()
                ? "CNY"
                : currency.trim().toUpperCase();
    }

    @Override
    public PaymentResponse validatePayment(Long paymentId) {
        Payment payment = getRequiredPayment(paymentId);

        // 校验账户存在 + 余额充足
        if (payment.getFromAccountId() != null && payment.getToAccountId() != null) {
            Account fromAccount = accountDao.findById(payment.getFromAccountId());
            Account toAccount   = accountDao.findById(payment.getToAccountId());

            if (fromAccount == null) {
                return failPaymentInternal(paymentId, "ACCOUNT_NOT_FOUND", "付款账户不存在: " + payment.getFromAccountId(), "SYSTEM");
            }
            if (toAccount == null) {
                return failPaymentInternal(paymentId, "ACCOUNT_NOT_FOUND", "收款账户不存在: " + payment.getToAccountId(), "SYSTEM");
            }
            // 跨币种时汇率必填
            if (!fromAccount.getCurrency().equals(toAccount.getCurrency()) && payment.getExchangeRate() == null) {
                return failPaymentInternal(paymentId, "MISSING_EXCHANGE_RATE", "跨币种转账必须提供汇率", "SYSTEM");
            }
            // 校验余额
            if (fromAccount.getBalance().compareTo(payment.getAmount()) < 0) {
                return failPaymentInternal(paymentId, "INSUFFICIENT_BALANCE",
                        "余额不足，当前余额: " + fromAccount.getBalance(), "SYSTEM");
            }
        }

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
    @Transactional
    public PaymentResponse sendPayment(Long paymentId) {
        Payment payment = getRequiredPayment(paymentId);

        // 执行余额扣款和入账（事务保证原子性）
        if (payment.getFromAccountId() != null && payment.getToAccountId() != null) {
            // 固定顺序加锁（小ID先锁），防止死锁
            Long firstId  = Math.min(payment.getFromAccountId(), payment.getToAccountId());
            Long secondId = Math.max(payment.getFromAccountId(), payment.getToAccountId());

            Account first  = accountDao.findByIdForUpdate(firstId);
            Account second = accountDao.findByIdForUpdate(secondId);

            Account fromAccount = payment.getFromAccountId().equals(firstId) ? first : second;
            Account toAccount   = payment.getToAccountId().equals(firstId)   ? first : second;

            if (fromAccount == null || toAccount == null) {
                return failPaymentInternal(paymentId, "ACCOUNT_NOT_FOUND", "账户不存在", "SYSTEM");
            }

            // 并发二次校验余额
            if (fromAccount.getBalance().compareTo(payment.getAmount()) < 0) {
                return failPaymentInternal(paymentId, "INSUFFICIENT_BALANCE",
                        "余额不足（并发校验），当前余额: " + fromAccount.getBalance(), "SYSTEM");
            }

            // 计算收款金额：跨币种乘以汇率，同币种原额
            BigDecimal receiveAmount = payment.getExchangeRate() != null
                    ? payment.getAmount().multiply(payment.getExchangeRate())
                    : payment.getAmount();

            // 扣款（乐观锁）
            int updated = accountDao.updateBalance(fromAccount.getId(),
                    payment.getAmount().negate(), fromAccount.getVersion());
            if (updated == 0) {
                return failPaymentInternal(paymentId, "CONCURRENT_UPDATE",
                        "账户并发更新冲突，请重试", "SYSTEM");
            }
            // 入账（乐观锁）
            int updated2 = accountDao.updateBalance(toAccount.getId(),
                    receiveAmount, toAccount.getVersion());
            if (updated2 == 0) {
                return failPaymentInternal(paymentId, "CONCURRENT_UPDATE",
                        "收款账户并发更新冲突，请重试", "SYSTEM");
            }
        }

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

    private PaymentException validateCreateRequest(CreatePaymentRequest request, String idempotencyKey) {
        if (request == null) {
            return new PaymentException("INVALID_REQUEST", "Request must not be null");
        }

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return new PaymentException("INVALID_IDEMPOTENCY_KEY", "Idempotency key must not be blank");
        }

        String normalizedIdempotencyKey = idempotencyKey.trim();

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

        // 校验源账户名在数据库中是否存在
        Account srcAccount = accountDao.findByAccountName(normalizedSource);
        if (srcAccount == null) {
            return new PaymentException("ACCOUNT_NOT_FOUND", "源账户不存在: " + normalizedSource);
        }
        // 校验目标账户名在数据库中是否存在
        Account dstAccount = accountDao.findByAccountName(normalizedDestination);
        if (dstAccount == null) {
            return new PaymentException("ACCOUNT_NOT_FOUND", "目标账户不存在: " + normalizedDestination);
        }

        Payment existingPayment = paymentDao.findByIdempotencyKey(normalizedIdempotencyKey);
        if (existingPayment != null) {
            return new PaymentException("DUPLICATE_IDEMPOTENCY_KEY", "Idempotency key already exists: " + normalizedIdempotencyKey);
        }

        return null;
    }

    private boolean isSupportedCurrency(String currencyCode) {
        return ISO_4217_CURRENCY_CODES.contains(currencyCode);
    }
}
