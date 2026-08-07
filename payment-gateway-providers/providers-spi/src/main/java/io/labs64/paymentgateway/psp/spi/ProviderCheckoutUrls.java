package io.labs64.paymentgateway.psp.spi;

/**
 * Gateway-owned callback URLs that a hosted checkout provider passes through to the PSP.
 */
public record ProviderCheckoutUrls(
        String returnUrl,
        String cancelUrl) {
}
