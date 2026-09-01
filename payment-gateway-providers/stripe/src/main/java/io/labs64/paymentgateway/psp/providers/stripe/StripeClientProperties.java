package io.labs64.paymentgateway.psp.providers.stripe;

import java.net.URI;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Process-level Stripe SDK client settings owned by the Stripe provider.
 *
 * @param apiBaseUrl optional Stripe API endpoint override; absent means the SDK default
 */
@ConfigurationProperties("payment-provider.stripe")
public record StripeClientProperties(URI apiBaseUrl) {
}
