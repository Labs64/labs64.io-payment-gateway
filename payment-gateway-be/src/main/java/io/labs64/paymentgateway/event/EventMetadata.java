package io.labs64.paymentgateway.event;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Common metadata describing the event itself.
 * <p>
 * This block is service-agnostic and should keep the same shape across ecosystem modules.
 * Context such as tenant id and correlation id belongs to the surrounding {@link Event} envelope.
 */
public record EventMetadata(
        UUID id,
        String type,
        int version,
        String origin,
        OffsetDateTime occurredAt) {
}
