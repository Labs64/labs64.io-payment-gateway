package io.labs64.paymentgateway.exception;

import org.springframework.http.HttpStatus;

import io.labs64.paymentgateway.v1.model.ErrorCode;

/**
 * Exception thrown when a payment is not in a payable state.
 */
public class PaymentNotPayableException extends ApiException {

    public PaymentNotPayableException(final String message) {
        super(message, HttpStatus.CONFLICT, ErrorCode.PAYMENT_NOT_PAYABLE);
    }
}
