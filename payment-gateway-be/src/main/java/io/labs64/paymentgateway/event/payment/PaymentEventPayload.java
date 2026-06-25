package io.labs64.paymentgateway.event.payment;

/**
 * Payment-specific payload embedded into the common {@link io.labs64.paymentgateway.event.Event} envelope.
 * <p>
 * The payload contains snapshots of the domain objects relevant to consumers. Snapshot fields are
 * additive: consumers should ignore unknown fields when the contract evolves.
 */
public record PaymentEventPayload(
        PaymentSnapshot payment,
        PaymentTransactionSnapshot transaction) {
}
