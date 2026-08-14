package io.labs64.paymentgateway.event.payment;

/** Stable public names of payment lifecycle events. */
public enum PaymentEventType {
    CREATED("payment.created"),
    FINALIZED("payment.finalized"),
    CLOSED("payment.closed");

    private final String eventType;

    PaymentEventType(final String eventType) {
        this.eventType = eventType;
    }

    public String eventType() {
        return eventType;
    }
}
