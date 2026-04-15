package io.labs64.paymentgateway.exception;

import java.time.OffsetDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import io.labs64.paymentgateway.v1.model.ErrorCode;
import io.labs64.paymentgateway.v1.model.ErrorResponse;
import io.labs64.paymentgateway.web.CorrelationIdFilter;

/**
 * Global exception handler for the Payment Gateway API.
 * Converts exceptions to the standard {@link ErrorResponse} model.
 * Consistent with checkout and auditflow error handling patterns.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(final ApiException ex) {
        log.warn("API error: code={}, message={}", ex.getErrorCode(), ex.getMessage());
        return buildResponse(ex.getHttpStatus(), ex.getErrorCode(), ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(final Exception ex) {
        log.error("Unexpected error", ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR,
                "An unexpected error occurred.");
    }

    private ResponseEntity<ErrorResponse> buildResponse(final HttpStatus status, final ErrorCode code,
            final String message) {
        final ErrorResponse error = new ErrorResponse();
        error.setCode(code);
        error.setMessage(message);
        error.setTimestamp(OffsetDateTime.now());
        error.setTraceId(MDC.get(CorrelationIdFilter.MDC_CORRELATION_ID));
        return ResponseEntity.status(status).body(error);
    }
}
