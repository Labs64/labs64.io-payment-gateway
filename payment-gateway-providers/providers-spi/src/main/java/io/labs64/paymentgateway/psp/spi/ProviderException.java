package io.labs64.paymentgateway.psp.spi;

/**
 * Base unchecked exception for failures reported by a payment provider.
 *
 * <p>The Payment Gateway owns transport-level error mapping and transaction
 * state changes. Provider implementations should use a more specific subtype
 * whenever possible.</p>
 */
public class ProviderException extends RuntimeException {

    public ProviderException(final String message) {
        super(message);
    }

    public ProviderException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
