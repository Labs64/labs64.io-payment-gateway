package io.labs64.paymentgateway.psp.spi;

/**
 * Base unchecked exception for a provider adapter that cannot return a
 * definitive normalized provider result.
 *
 * <p>Provider exceptions are part of the SPI control-flow contract; they are not
 * payment outcomes. The payment gateway must never transition a payment
 * transaction to {@code SUCCESS} or {@code FAILED} because an exception was
 * thrown. A terminal transition is allowed only when a provider returns a normal
 * {@link ProviderResult} with a terminal status.</p>
 *
 * <p>Adapters must use the most specific subtype. They remain responsible for
 * translating PSP SDK exceptions and protocol details, while the gateway remains
 * responsible for persistence, transaction state, transport responses, and
 * events.</p>
 */
public abstract class ProviderException extends RuntimeException {

    protected ProviderException(final String message) {
        super(message);
    }

    protected ProviderException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
