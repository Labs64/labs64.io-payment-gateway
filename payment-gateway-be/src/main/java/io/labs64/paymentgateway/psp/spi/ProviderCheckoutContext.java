package io.labs64.paymentgateway.psp.spi;

import java.util.List;
import java.util.Map;

/**
 * Provider-facing context for browser checkout return/cancel callbacks.
 *
 * @param payment immutable payment snapshot
 * @param transaction immutable payment transaction snapshot linked to the checkout session
 * @param provider tenant provider configuration snapshot
 * @param checkoutSession persisted checkout session snapshot
 * @param queryParams callback query parameters received from the PSP/browser redirect
 */
public record ProviderCheckoutContext(
        Payment payment,
        PaymentTransaction transaction,
        ProviderConfig provider,
        CheckoutSession checkoutSession,
        Map<String, List<String>> queryParams) {
}
