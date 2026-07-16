package io.labs64.paymentgateway.psp.spi;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Provider-facing snapshot of a user-facing checkout session.
 */
public record CheckoutSession(
        UUID id,
        Map<String, Object> payload,
        Map<String, Object> nextAction,
        OffsetDateTime expiresAt) {
}
