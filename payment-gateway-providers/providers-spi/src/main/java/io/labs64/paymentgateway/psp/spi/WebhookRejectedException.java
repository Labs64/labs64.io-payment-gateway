package io.labs64.paymentgateway.psp.spi;

/**
 * Indicates that a provider rejected an incoming webhook before it could be trusted or handled.
 * <p>
 * The gateway must return an error response and leave payment state unchanged. Typical reasons
 * include an invalid signature, malformed provider payload, or a mismatch between the verified
 * webhook and the restored payment transaction.
 */
public class WebhookRejectedException extends ProviderException {

    public WebhookRejectedException(final String message) {
        super(message);
    }

    public WebhookRejectedException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
