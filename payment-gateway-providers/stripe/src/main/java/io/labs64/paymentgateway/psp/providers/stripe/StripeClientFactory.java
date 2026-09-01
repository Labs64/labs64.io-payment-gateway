package io.labs64.paymentgateway.psp.providers.stripe;

import com.stripe.StripeClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Constructs tenant-authenticated Stripe SDK clients without leaking SDK setup into payment logic. */
public final class StripeClientFactory {

    private static final Logger log = LoggerFactory.getLogger(StripeClientFactory.class);

    private final StripeClientProperties properties;

    public StripeClientFactory(final StripeClientProperties properties) {
        this.properties = properties;

        if (properties.apiBaseUrl() != null) {
            log.info("Stripe SDK API base URL overridden: {}", properties.apiBaseUrl());
        }
    }

    public StripeClient create(final String apiKey) {
        final StripeClient.StripeClientBuilder builder = StripeClient.builder().setApiKey(apiKey);

        if (properties.apiBaseUrl() != null) {
            builder.setApiBase(properties.apiBaseUrl().toString());
        }

        return builder.build();
    }
}
