package io.labs64.paymentgateway.exception;

import org.springframework.http.HttpStatus;

import io.labs64.paymentgateway.model.ErrorCode;

/**
 * Exception thrown when an upstream PSP call fails.
 */
public class PspException extends ApiException {

    public PspException(final String message) {
        super(message, HttpStatus.BAD_GATEWAY, ErrorCode.PSP_ERROR);
    }

    public PspException(final String message, final Throwable cause) {
        super(message, HttpStatus.BAD_GATEWAY, ErrorCode.PSP_ERROR, cause);
    }
}
