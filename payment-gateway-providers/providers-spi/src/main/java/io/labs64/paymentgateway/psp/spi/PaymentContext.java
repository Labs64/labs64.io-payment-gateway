package io.labs64.paymentgateway.psp.spi;

public record PaymentContext(
        Payment payment,
        PaymentTransaction transaction,
        ProviderConfig provider,
        PaymentExecutionRequest request,
        ProviderCheckout checkout) {

    public PaymentContext(
            final Payment payment,
            final PaymentTransaction transaction,
            final ProviderConfig provider) {
        this(payment, transaction, provider, PaymentExecutionRequest.empty(), null);
    }
}
