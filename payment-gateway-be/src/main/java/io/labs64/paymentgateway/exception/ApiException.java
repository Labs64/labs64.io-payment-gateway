package io.labs64.paymentgateway.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import io.labs64.paymentgateway.model.ErrorCode;

/**
 * Abstract base exception for all Payment Gateway API errors.
 * Consistent with checkout and auditflow module patterns.
 */
@Getter
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final ErrorCode errorCode;

    protected ApiException(final String message, final HttpStatus status, final ErrorCode errorCode) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    protected ApiException(final String message, final HttpStatus status, final ErrorCode errorCode,
                           final Throwable cause) {
        super(message, cause);
        this.status = status;
        this.errorCode = errorCode;
    }
}
