package io.labs64.paymentgateway.psp.spi;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Provider-owned data used by the gateway to create a checkout session.
 *
 * @param payload provider-owned data persisted by the gateway and later exposed
 *                back to the same provider during checkout return/cancel handling
 * @param expiresAt optional checkout session expiration timestamp
 */
public record CheckoutSessionDraft(
        Map<String, Object> payload,
        OffsetDateTime expiresAt) {
}
