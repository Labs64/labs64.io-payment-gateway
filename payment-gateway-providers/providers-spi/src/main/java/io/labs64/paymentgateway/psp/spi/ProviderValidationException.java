package io.labs64.paymentgateway.psp.spi;

/**
 * Indicates invalid provider configuration, provider-specific request data, or
 * untrusted browser callback input detected before a PSP payment side effect.
 *
 * <p>Preflight validation must be side-effect-free and should run before the
 * gateway creates a payment transaction. When this exception is raised for an
 * existing browser checkout, the gateway must reject the callback and leave the
 * existing transaction, including its status details, unchanged.</p>
 *
 * <p>An adapter must not use this exception after a PSP operation might have
 * produced a side effect. An incomplete or malformed PSP response in that phase
 * is a {@link ProviderExecutionException} with
 * {@link ProviderExecutionFailure#INVALID_RESPONSE}.</p>
 */
public class ProviderValidationException extends ProviderException {

    public ProviderValidationException(final String message) {
        super(message);
    }

    public ProviderValidationException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
