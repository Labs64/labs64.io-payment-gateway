package io.labs64.paymentgateway.psp.spi;

public record PaymentContext(
        Payment payment,
        PaymentTransaction transaction,
        ProviderConfig provider) {
}
