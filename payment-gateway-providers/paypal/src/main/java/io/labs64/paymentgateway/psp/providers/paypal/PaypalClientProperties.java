package io.labs64.paymentgateway.psp.providers.paypal;

import java.net.URI;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Process-level PayPal SDK client settings owned by the PayPal provider.
 *
 * @param apiBaseUrl optional PayPal REST API endpoint override; absent means the SDK default
 */
@ConfigurationProperties("payment-provider.paypal")
public record PaypalClientProperties(URI apiBaseUrl) {
}
