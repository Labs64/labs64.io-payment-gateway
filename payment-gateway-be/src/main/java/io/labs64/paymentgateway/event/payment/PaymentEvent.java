package io.labs64.paymentgateway.event.payment;

import io.labs64.paymentgateway.event.Event;

/**
 * Internal Spring event that binds a payment event message to its RabbitMQ route.
 * <p>
 * This record is not the serialized RabbitMQ payload. The serialized payload is {@link Event}
 * with {@link PaymentEventPayload}. This wrapper is used only inside the application so the
 * after-commit listener knows which binding should receive the message.
 */
public record PaymentEvent(
        PaymentEventRoute route,
        Event<PaymentEventPayload> message) {
}
