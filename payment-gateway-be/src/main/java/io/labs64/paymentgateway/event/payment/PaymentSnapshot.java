package io.labs64.paymentgateway.event.payment;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import io.labs64.paymentgateway.model.PaymentStatus;
import io.labs64.paymentgateway.model.PaymentType;

/**
 * Immutable payment state snapshot embedded into payment integration events.
 * <p>
 * Tenant id is intentionally not included here because it belongs to the top-level event envelope.
 * The snapshot should not expose tenant payment provider configuration or other sensitive internals.
 */
public record PaymentSnapshot(
        UUID id,
        UUID paymentProviderId,
        String provider,
        PaymentStatus status,
        PaymentType type,
        String description,
        Map<String, Object> purchaseOrder,
        Map<String, Object> billingInfo,
        Map<String, Object> shippingInfo,
        Map<String, Object> recurrence,
        Map<String, Object> extra,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
