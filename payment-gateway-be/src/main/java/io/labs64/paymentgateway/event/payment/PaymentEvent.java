package io.labs64.paymentgateway.event.payment;

import io.labs64.auditflow.model.AuditEvent;

/** Internal event used to defer AuditFlow publication until the transaction commits. */
public record PaymentEvent(
        AuditEvent auditEvent) {
}
