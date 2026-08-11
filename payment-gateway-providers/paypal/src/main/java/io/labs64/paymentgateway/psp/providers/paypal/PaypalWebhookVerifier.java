package io.labs64.paymentgateway.psp.providers.paypal;

import com.paypal.sdk.PaypalServerSdkClient;
import io.labs64.paymentgateway.psp.spi.WebhookRequest;

/**
 * Verifies an incoming PayPal webhook against the PayPal API.
 */
@FunctionalInterface
interface PaypalWebhookVerifier {

    void verify(PaypalServerSdkClient client, String webhookId, WebhookRequest request);
}
