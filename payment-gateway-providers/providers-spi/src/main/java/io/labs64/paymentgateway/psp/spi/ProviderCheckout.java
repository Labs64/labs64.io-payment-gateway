package io.labs64.paymentgateway.psp.spi;

import java.util.Objects;

/**
 * Gateway-owned hosted checkout context for a provider payment execution.
 */
public record ProviderCheckout(
        CheckoutSession session,
        ProviderCheckoutUrls urls) {

    public ProviderCheckout {
        Objects.requireNonNull(session, "session is required");
        Objects.requireNonNull(urls, "urls are required");
    }
}
