package io.labs64.paymentgateway.psp.spi;

import java.util.Objects;

/**
 * Indicates that a PSP operation could not produce a definitive normalized
 * payment result.
 *
 * <p>This exception is used after a provider operation has started or when the
 * adapter cannot prove that no PSP-side effect occurred. Typical cases are
 * network timeouts, rate limits, PSP server errors, rejected credentials, and
 * incomplete successful responses.</p>
 *
 * <p>The gateway must leave the payment transaction non-terminal. It may update
 * normalized technical status details and return an appropriate transport
 * response, but it must not publish a finalized event. If the PSP returned a
 * definitive business outcome such as success, decline, cancellation, or expiry,
 * the adapter must return a {@link PaymentResult} instead of throwing this
 * exception.</p>
 */
public class ProviderExecutionException extends ProviderException {

    private final ProviderExecutionFailure failure;

    public ProviderExecutionException(
            final ProviderExecutionFailure failure,
            final String message) {
        super(message);
        this.failure = Objects.requireNonNull(failure, "failure");
    }

    public ProviderExecutionException(
            final ProviderExecutionFailure failure,
            final String message,
            final Throwable cause) {
        super(message, cause);
        this.failure = Objects.requireNonNull(failure, "failure");
    }

    /**
     * Returns the provider-neutral reason that the operation produced no
     * definitive payment result.
     *
     * @return normalized execution failure
     */
    public ProviderExecutionFailure failure() {
        return failure;
    }
}
