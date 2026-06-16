package io.labs64.paymentgateway.exception;

import io.labs64.paymentgateway.model.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * Exception thrown when request payload validation fails.
 */
public class ConflictException extends ApiException {

    public ConflictException(final String message) {
        super(message, HttpStatus.CONFLICT, ErrorCode.CONFLICT);
    }
}
