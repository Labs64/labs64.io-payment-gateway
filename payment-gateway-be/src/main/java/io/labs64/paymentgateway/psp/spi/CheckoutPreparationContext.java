package io.labs64.paymentgateway.psp.spi;

/**
 * Context used by checkout-capable providers before the gateway executes a payment attempt.
 *
 * @param payment immutable payment snapshot
 * @param transaction immutable payment transaction snapshot created for this attempt
 * @param provider tenant provider configuration snapshot
 * @param request payment execution request data supplied by the tenant
 */
public record CheckoutPreparationContext(
        Payment payment,
        PaymentTransaction transaction,
        ProviderConfig provider,
        PaymentExecutionRequest request) {
}
