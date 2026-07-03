package io.labs64.paymentgateway.exception;

import org.springframework.http.HttpStatus;

import io.labs64.paymentgateway.model.ErrorCode;

/**
 * Exception thrown when request payload validation fails.
 */
public class ValidationException extends ApiException {

    public ValidationException(final String message) {
        super(message, HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR);
    }
}
