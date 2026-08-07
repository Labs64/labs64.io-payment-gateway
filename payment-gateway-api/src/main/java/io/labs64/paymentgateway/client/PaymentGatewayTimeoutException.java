package io.labs64.paymentgateway.client;

/** The configured call timeout elapsed. */
public final class PaymentGatewayTimeoutException extends PaymentGatewayTransportException {

    public PaymentGatewayTimeoutException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
