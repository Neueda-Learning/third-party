package com.team.payment.service;

import com.team.payment.dto.CreatePaymentRequest;
import com.team.payment.dto.HistoryResponse;
import com.team.payment.dto.PaymentListResponse;
import com.team.payment.dto.PaymentResponse;
import com.team.payment.entity.PaymentStatus;
import com.team.payment.entity.Payment;
import com.team.payment.entity.PaymentHistory;
import com.team.payment.exception.*;
import com.team.payment.dao.PaymentDao;
import com.team.payment.dao.PaymentHistoryDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
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



	@Autowired
	private PaymentDao paymentDao;

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
	public PaymentResponse createPayment(CreatePaymentRequest request, String idempotencyKey) {
		throw new UnsupportedOperationException("创建支付功能暂未实现");
	}

	@Override
	public PaymentResponse getPaymentDetail(Long paymentId) {
		throw new UnsupportedOperationException("支付详情功能暂未实现");
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
	public List<HistoryResponse> getPaymentHistory(Long paymentId) {
		throw new UnsupportedOperationException("支付历史功能暂未实现");
	}

	@Override
	public PaymentResponse validatePayment(Long paymentId) {
		throw new UnsupportedOperationException("状态推进功能暂未实现");
	}

	@Override
	public PaymentResponse sendPayment(Long paymentId) {
		throw new UnsupportedOperationException("状态推进功能暂未实现");
	}

	@Override
	public PaymentResponse completePayment(Long paymentId) {
		throw new UnsupportedOperationException("状态推进功能暂未实现");
	}

	@Override
	public PaymentResponse failPayment(Long paymentId, String errorCode, String errorMessage) {
		throw new UnsupportedOperationException("状态推进功能暂未实现");
	}
}

