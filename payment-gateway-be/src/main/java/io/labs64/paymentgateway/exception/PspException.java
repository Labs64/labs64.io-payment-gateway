package io.labs64.paymentgateway.exception;

import org.springframework.http.HttpStatus;

import io.labs64.paymentgateway.v1.model.ErrorCode;

/**
 * Exception thrown when upstream PSP communication fails.
 */
public class PspException extends ApiException {

    public PspException(final String message) {
        super(message, HttpStatus.BAD_GATEWAY, ErrorCode.PSP_ERROR);
    }

    public PspException(final String message, final Throwable cause) {
        super(message, cause, HttpStatus.BAD_GATEWAY, ErrorCode.PSP_ERROR);
    }
}
