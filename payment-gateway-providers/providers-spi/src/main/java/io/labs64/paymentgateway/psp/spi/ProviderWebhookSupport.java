package io.labs64.paymentgateway.psp.spi;

import java.util.UUID;

/**
 * Optional capability for payment providers that support PSP webhooks.
 */
public interface ProviderWebhookSupport {

    UUID resolveTransactionId(WebhookRequest request);

    PaymentWebhookResult handleWebhook(PaymentWebhookContext context);
}
