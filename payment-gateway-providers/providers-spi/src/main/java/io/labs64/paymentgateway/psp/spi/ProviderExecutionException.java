package io.labs64.paymentgateway.psp.spi;

/**
 * Indicates a technical failure while a provider executes or completes a payment.
 */
public class ProviderExecutionException extends ProviderException {

    public ProviderExecutionException(final String message) {
        super(message);
    }

    public ProviderExecutionException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
