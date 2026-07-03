package io.labs64.paymentgateway.exception;

import org.springframework.http.HttpStatus;

import io.labs64.paymentgateway.model.ErrorCode;

/**
 * Exception thrown when an authenticated request lacks required scopes.
 */
public class ForbiddenException extends ApiException {

    public ForbiddenException(final String message) {
        super(message, HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN);
    }
}
