package io.labs64.paymentgateway.service;

import java.util.UUID;

import io.labs64.paymentgateway.config.PaymentGatewayProperties;
import io.labs64.paymentgateway.psp.spi.ProviderCheckoutUrls;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Owns public Payment Gateway callback route construction for hosted PSP checkout flows.
 */
@Component
@RequiredArgsConstructor
public class CheckoutCallbackUrlFactory {

    private final PaymentGatewayProperties properties;

    public ProviderCheckoutUrls create(final String provider, final UUID checkoutSessionId) {
        return new ProviderCheckoutUrls(
                callbackUrl(provider, checkoutSessionId, "return"),
                callbackUrl(provider, checkoutSessionId, "cancel"));
    }

    private String callbackUrl(final String provider, final UUID checkoutSessionId, final String action) {
        return UriComponentsBuilder.fromUriString(properties.getPublicBaseUrl())
                .pathSegment("providers", provider, "checkout-sessions", checkoutSessionId.toString(), action)
                .build()
                .toUriString();
    }
}
