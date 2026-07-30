package com.team.payment.exception;

import com.team.payment.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 全局异常处理器
 * 成员D负责实现
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理支付异常
     */
    @ExceptionHandler(PaymentException.class)
    public ResponseEntity<ErrorResponse> handlePaymentException(PaymentException e) {
        HttpStatus status = getHttpStatus(e.getErrorCode());
        
        ErrorResponse response = ErrorResponse.builder()
            .errorCode(e.getErrorCode())
            .message(e.getMessage())
            .timestamp(LocalDateTime.now())
            .traceId(UUID.randomUUID().toString())
            .build();

        return new ResponseEntity<>(response, status);
    }

    /**
     * 处理参数校验异常
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException e) {
        FieldError firstError = e.getBindingResult().getFieldErrors().stream().findFirst().orElse(null);
        String message = firstError != null ? firstError.getDefaultMessage() : "参数校验失败";

        ErrorResponse response = ErrorResponse.builder()
            .errorCode("VALIDATION_FAILED")
            .message(message)
            .timestamp(LocalDateTime.now())
            .traceId(UUID.randomUUID().toString())
            .build();

        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    /**
     * 处理静态资源找不到的异常（如 favicon.ico），返回 404
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFoundException(NoResourceFoundException e) {
        ErrorResponse response = ErrorResponse.builder()
            .errorCode("NOT_FOUND")
            .message(e.getMessage())
            .timestamp(LocalDateTime.now())
            .traceId(UUID.randomUUID().toString())
            .build();
        return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
    }

    /**
     * 处理所有其他异常
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception e) {
        ErrorResponse response = ErrorResponse.builder()
            .errorCode("PROCESSING_ERROR")
            .message("内部系统错误: " + e.getMessage())
            .timestamp(LocalDateTime.now())
            .traceId(UUID.randomUUID().toString())
            .build();
        System.out.println("Unhandled exception: " + e);
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * 根据错误码获取对应的HTTP状态码
     */
    private HttpStatus getHttpStatus(String errorCode) {
        return switch (errorCode) {
            case "VALIDATION_FAILED", "INVALID_AMOUNT", "INVALID_CURRENCY", 
                 "INVALID_ACCOUNT", "INVALID_STATUS_TRANSITION" -> HttpStatus.BAD_REQUEST;
            case "DUPLICATE_IDEMPOTENCY_KEY" -> HttpStatus.CONFLICT;
            case "PAYMENT_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "NETWORK_TIMEOUT" -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}

