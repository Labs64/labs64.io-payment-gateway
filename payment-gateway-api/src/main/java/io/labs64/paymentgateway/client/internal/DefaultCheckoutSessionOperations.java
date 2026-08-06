package io.labs64.paymentgateway.client.internal;

import io.labs64.paymentgateway.client.CallOptions;
import io.labs64.paymentgateway.client.CheckoutSessionOperations;
import io.labs64.paymentgateway.model.CheckoutSessionConfirmation;
import java.util.Objects;
import java.util.UUID;

final class DefaultCheckoutSessionOperations implements CheckoutSessionOperations {

    private final HttpTransport transport;

    DefaultCheckoutSessionOperations(final HttpTransport transport) {
        this.transport = transport;
    }

    @Override
    public CheckoutSessionConfirmation getConfirmation(final UUID sessionId, final CallOptions options) {
        final String path = "/checkout-sessions/" + Objects.requireNonNull(sessionId, "sessionId")
                + "/confirmation";
        return transport.get(path, new QueryParameters(), CheckoutSessionConfirmation.class, options);
    }
}
