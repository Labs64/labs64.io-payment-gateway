package io.labs64.paymentgateway.event;

/**
 * Common JSON envelope for integration events published between ecosystem services.
 * <p>
 * This is the object serialized to RabbitMQ. Service-specific data belongs to {@code payload},
 * while tenant and correlation context stay at the envelope level for routing, filtering, and tracing.
 *
 * @param <T> service-specific event payload type
 */
public record Event<T>(
        EventMetadata event,
        String tenantId,
        String correlationId,
        T payload) {
}
