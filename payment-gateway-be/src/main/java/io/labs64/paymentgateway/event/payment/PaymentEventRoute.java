package io.labs64.paymentgateway.event.payment;

/**
 * RabbitMQ routing metadata for payment integration events.
 * <p>
 * {@code eventType} is copied into {@link io.labs64.paymentgateway.event.EventMetadata#type()} and is part
 * of the public event contract. {@code bindingName} is an internal Spring Cloud Stream binding name and
 * must not leak into serialized payloads.
 */
public enum PaymentEventRoute {
    CREATED("payment.created", "paymentCreated-out-0"),
    FINALIZED("payment.finalized", "paymentFinalized-out-0"),
    CLOSED("payment.closed", "paymentClosed-out-0");

    private final String eventType;
    private final String bindingName;

    PaymentEventRoute(final String eventType, final String bindingName) {
        this.eventType = eventType;
        this.bindingName = bindingName;
    }

    public String eventType() {
        return eventType;
    }

    public String bindingName() {
        return bindingName;
    }
}
