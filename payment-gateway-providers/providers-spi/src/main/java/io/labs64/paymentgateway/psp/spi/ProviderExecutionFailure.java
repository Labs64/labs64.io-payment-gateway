package io.labs64.paymentgateway.psp.spi;

/**
 * Normalized reason why a provider operation could not produce a definitive
 * {@link PaymentResult}.
 *
 * <p>Provider adapters translate PSP-specific SDK exceptions, HTTP responses,
 * and protocol errors into this provider-neutral contract. The payment gateway
 * must not inspect PSP exception types or response codes directly. None of these
 * values represents a definitive failed payment and none may trigger a terminal
 * payment transaction transition.</p>
 */
public enum ProviderExecutionFailure {

    /**
     * The PSP was unavailable or did not return a response, for example because
     * of a connection error, timeout, rate limit, or server-side failure.
     */
    UNAVAILABLE,

    /**
     * The PSP rejected credentials while an operation was being prepared or
     * executed. The payment outcome is not represented by this failure.
     */
    AUTHENTICATION_FAILED,

    /**
     * The PSP returned a response that could not be safely converted into a
     * normalized result, for example a successful response without its required
     * provider object identifier.
     */
    INVALID_RESPONSE
}
