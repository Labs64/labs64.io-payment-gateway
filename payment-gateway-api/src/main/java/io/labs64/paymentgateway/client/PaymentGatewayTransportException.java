package io.labs64.paymentgateway.client;

/** Network-level failure while calling the Payment Gateway. */
public class PaymentGatewayTransportException extends PaymentGatewayException {

    public PaymentGatewayTransportException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
