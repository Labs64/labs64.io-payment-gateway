package io.labs64.paymentgateway.exception;

import org.springframework.http.HttpStatus;

import io.labs64.paymentgateway.v1.model.ErrorCode;

/**
 * Exception thrown when a requested resource is not found.
 */
public class NotFoundException extends ApiException {

    public NotFoundException(final String message) {
        super(message, HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND);
    }
}
