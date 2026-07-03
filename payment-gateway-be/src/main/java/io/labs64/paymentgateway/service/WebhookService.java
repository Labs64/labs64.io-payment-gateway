package io.labs64.paymentgateway.service;

import io.labs64.paymentgateway.psp.spi.PaymentWebhookResult;
import io.labs64.paymentgateway.psp.spi.WebhookRequest;

/**
 * Service for handling PSP webhook notifications.
 */
public interface WebhookService {

    /**
     * Process a webhook notification from a PSP.
     *
     * @param request PSP webhook request data
     * @return webhook processing result
     */
    PaymentWebhookResult processWebhook(WebhookRequest request);
}
