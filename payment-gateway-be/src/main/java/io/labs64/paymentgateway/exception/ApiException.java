package io.labs64.paymentgateway.exception;

import org.springframework.http.HttpStatus;

import io.labs64.paymentgateway.v1.model.ErrorCode;

/**
 * Abstract base exception for all Payment Gateway API errors.
 * Consistent with checkout and auditflow module patterns.
 */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus httpStatus;
    private final ErrorCode errorCode;

    protected ApiException(final String message, final HttpStatus httpStatus, final ErrorCode errorCode) {
        super(message);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
    }

    protected ApiException(final String message, final Throwable cause, final HttpStatus httpStatus,
            final ErrorCode errorCode) {
        super(message, cause);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
