package io.labs64.paymentgateway.psp.spi;

import java.util.Map;

public record PaymentWebhookContext(
        Payment payment,
        PaymentTransaction transaction,
        ProviderConfig provider,
        Map<String, Object> payload,
        Map<String, String> headers) {
}
