package io.labs64.paymentgateway.exception;

import java.time.OffsetDateTime;

import io.labs64.paymentgateway.correlation.CorrelationContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import io.labs64.paymentgateway.model.ErrorCode;
import io.labs64.paymentgateway.model.ErrorResponse;
import io.labs64.paymentgateway.message.ValidationMessages;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;

/**
 * Global exception handler for the Payment Gateway API.
 * Converts exceptions to the standard {@link ErrorResponse} model.
 * Consistent with checkout and auditflow error handling patterns.
 */
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final ValidationMessages msg;

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(final ApiException ex) {
        log.warn("API error: code={}, message={}", ex.getErrorCode(), ex.toString());
        return buildResponse(ex.getStatus(), ex.getErrorCode(), ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(final MethodArgumentNotValidException ex) {
        final FieldError firstError = ex.getBindingResult().getFieldErrors().stream().findFirst().orElse(null);
        final String message = firstError != null
                ? msg.invalidField(firstError.getField(), firstError.getDefaultMessage())
                : msg.failed();

        log.warn("Request validation error: {} | ex={}", message, ex.toString());
        return buildResponse(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, message);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolationException(final ConstraintViolationException ex) {
        final String message = ex.getConstraintViolations().stream()
                .findFirst()
                .map(v -> msg.invalidField(String.valueOf(v.getPropertyPath()), v.getMessage()))
                .orElse(msg.failed());

        log.warn("Request constraint violation: {} | ex={}", message, ex.toString());
        return buildResponse(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, message);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFoundException(final NoResourceFoundException ex) {
        final String message = msg.endpointNotFound(ex.getResourcePath());

        log.warn("Endpoint not found: {}", ex.getResourcePath());
        return buildResponse(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, message);
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
        CorrelationContextHolder.get().ifPresent(error::setTraceId);

        return ResponseEntity.status(status).body(error);
    }
}
