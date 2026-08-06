package io.labs64.paymentgateway.client;

/** Base class for failures reported by the Payment Gateway client. */
public class PaymentGatewayException extends RuntimeException {

    public PaymentGatewayException(final String message) {
        super(message);
    }

    public PaymentGatewayException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
