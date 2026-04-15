package io.labs64.paymentgateway.exception;

import org.springframework.http.HttpStatus;

import io.labs64.paymentgateway.v1.model.ErrorCode;

/**
 * Exception thrown when an Idempotency-Key is reused with a different request body.
 */
public class IdempotencyConflictException extends ApiException {

    public IdempotencyConflictException(final String message) {
        super(message, HttpStatus.CONFLICT, ErrorCode.IDEMPOTENCY_CONFLICT);
    }
}
