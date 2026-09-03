package io.labs64.paymentgateway.psp.spi;

/**
 * Indicates that an incoming webhook could not be trusted or safely correlated
 * with the restored payment transaction.
 *
 * <p>Typical reasons are an invalid signature, malformed payload, unsupported
 * event, or a mismatch between verified provider data and the restored gateway
 * transaction. The gateway must return a rejection response and leave the
 * payment transaction, status details, and provider data unchanged. This
 * exception must never be converted into a failed payment result.</p>
 */
public class WebhookRejectedException extends ProviderException {

    public WebhookRejectedException(final String message) {
        super(message);
    }

    public WebhookRejectedException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
