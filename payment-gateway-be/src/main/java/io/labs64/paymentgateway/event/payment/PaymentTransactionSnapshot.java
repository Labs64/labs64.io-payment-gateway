package io.labs64.paymentgateway.event.payment;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import io.labs64.paymentgateway.model.PaymentTransactionStatus;
import io.labs64.paymentgateway.model.StatusDetails;

/**
 * Immutable payment transaction state snapshot embedded into payment integration events.
 * <p>
 * PSP data included here must already be sanitized by the provider/gateway before publication.
 * Tenant id is intentionally omitted because it belongs to the top-level event envelope.
 */
public record PaymentTransactionSnapshot(
        UUID id,
        PaymentTransactionStatus status,
        StatusDetails statusDetails,
        Map<String, Object> pspData,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
