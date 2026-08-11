package io.labs64.paymentgateway.psp.spi;

public record PaymentWebhookContext(
        PaymentTransaction transaction,
        ProviderConfig provider,
        WebhookRequest request) {
}
