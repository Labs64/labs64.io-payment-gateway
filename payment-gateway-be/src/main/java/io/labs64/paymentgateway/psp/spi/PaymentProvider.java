package io.labs64.paymentgateway.psp.spi;

import java.util.Map;
import java.util.UUID;

public interface PaymentProvider {

    String provider();

    PaymentResult execute(PaymentContext context);

    UUID resolvePaymentTransactionId(WebhookPayload payload);

    PaymentWebhookResult handleWebhook(PaymentWebhookContext context);

    Map<String, String> validateAndSanitizePaymentProviderConfig(Map<String, String> config);
}
