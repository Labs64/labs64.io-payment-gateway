package io.labs64.paymentgateway.service;

import java.util.Map;

import io.labs64.paymentgateway.psp.spi.PaymentWebhookResult;

/**
 * Service for handling PSP webhook notifications.
 */
public interface WebhookService {

    /**
     * Process a webhook notification from a PSP.
     *
     * @param provider  the PSP provider identifier
     * @param payload   the raw webhook payload
     * @return webhook processing result
     */
    PaymentWebhookResult processWebhook(String provider, Map<String, Object> payload);
}
