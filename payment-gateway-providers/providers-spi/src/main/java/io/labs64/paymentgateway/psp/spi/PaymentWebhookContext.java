package io.labs64.paymentgateway.psp.spi;

public record PaymentWebhookContext(
        Payment payment,
        PaymentTransaction transaction,
        ProviderConfig provider,
        WebhookRequest request) {
}
