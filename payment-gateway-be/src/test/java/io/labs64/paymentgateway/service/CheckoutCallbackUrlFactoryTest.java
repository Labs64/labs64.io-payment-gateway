package io.labs64.paymentgateway.service;

import java.util.UUID;

import io.labs64.paymentgateway.config.PaymentGatewayProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CheckoutCallbackUrlFactoryTest {

    @Test
    void createsGatewayOwnedReturnAndCancelUrls() {
        final PaymentGatewayProperties properties = new PaymentGatewayProperties();
        properties.setPublicBaseUrl("https://gateway.example/api/v1");
        final CheckoutCallbackUrlFactory factory = new CheckoutCallbackUrlFactory(properties);
        final UUID sessionId = UUID.fromString("550e8400-e29b-41d4-a716-446655440002");

        final var urls = factory.create("paypal", sessionId);

        assertThat(urls.returnUrl()).isEqualTo(
                "https://gateway.example/api/v1/providers/paypal/checkout-sessions/"
                        + sessionId + "/return");
        assertThat(urls.cancelUrl()).isEqualTo(
                "https://gateway.example/api/v1/providers/paypal/checkout-sessions/"
                        + sessionId + "/cancel");
    }
}
