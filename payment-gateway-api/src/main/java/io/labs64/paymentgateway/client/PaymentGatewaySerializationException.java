package io.labs64.paymentgateway.client;

/** Request encoding or response decoding failure. */
public final class PaymentGatewaySerializationException extends PaymentGatewayException {

    public PaymentGatewaySerializationException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
