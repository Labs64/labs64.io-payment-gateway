package io.labs64.paymentgateway.idempotency;

public record IdempotencyContext(
        String tenantId,
        String idempotencyKey,
        String requestHash,
        IdempotencyOperation operation) {
}
