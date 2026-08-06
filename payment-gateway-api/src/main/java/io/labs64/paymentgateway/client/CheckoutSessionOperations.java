package io.labs64.paymentgateway.client;

import io.labs64.paymentgateway.model.CheckoutSessionConfirmation;
import java.util.UUID;

/** Public-safe checkout session operations. */
public interface CheckoutSessionOperations {

    default CheckoutSessionConfirmation getConfirmation(final UUID sessionId) {
        return getConfirmation(sessionId, CallOptions.empty());
    }

    CheckoutSessionConfirmation getConfirmation(UUID sessionId, CallOptions options);
}
