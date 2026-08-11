package io.labs64.paymentgateway.psp.spi;

import java.util.UUID;

/**
 * Optional capability for payment providers that support PSP webhooks.
 */
public interface ProviderWebhookSupport {

    /**
     * Extracts the gateway transaction identifier from a provider-specific webhook payload.
     * <p>
     * This step exists only so the gateway can restore the provider configuration needed by
     * {@link #handleWebhook(PaymentWebhookContext)}. Implementations must not treat the
     * payload as authenticated at this stage and must not perform side effects.
     */
    UUID extractPaymentTransactionId(WebhookRequest request);

    /**
     * Verifies and handles a provider-specific webhook.
     * <p>
     * Implementations that reject the webhook must throw {@link WebhookRejectedException}; a
     * rejected webhook is a transport/security failure and must never be represented as a failed
     * payment result.
     */
    PaymentWebhookResult handleWebhook(PaymentWebhookContext context);
}
