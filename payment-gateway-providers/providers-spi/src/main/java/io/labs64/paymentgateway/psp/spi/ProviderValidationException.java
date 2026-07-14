package io.labs64.paymentgateway.psp.spi;

/**
 * Indicates invalid provider configuration or provider-specific request data.
 */
public class ProviderValidationException extends ProviderException {

    public ProviderValidationException(final String message) {
        super(message);
    }

    public ProviderValidationException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
