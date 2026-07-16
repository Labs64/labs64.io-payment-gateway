package io.labs64.paymentgateway.psp.spi;

public record PaymentContext(
        Payment payment,
        PaymentTransaction transaction,
        ProviderConfig provider,
        CheckoutSession checkoutSession,
        PaymentExecutionRequest request) {

    public PaymentContext(
            final Payment payment,
            final PaymentTransaction transaction,
            final ProviderConfig provider) {
        this(payment, transaction, provider, null, PaymentExecutionRequest.empty());
    }

    public PaymentContext(
            final Payment payment,
            final PaymentTransaction transaction,
            final ProviderConfig provider,
            final CheckoutSession checkoutSession) {
        this(payment, transaction, provider, checkoutSession, PaymentExecutionRequest.empty());
    }
}
