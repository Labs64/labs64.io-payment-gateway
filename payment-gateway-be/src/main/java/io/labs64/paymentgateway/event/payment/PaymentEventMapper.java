package io.labs64.paymentgateway.event.payment;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import io.labs64.auditflow.client.AuditEvents;
import io.labs64.auditflow.model.AuditEvent;
import io.labs64.paymentgateway.correlation.CorrelationContextHolder;
import io.labs64.paymentgateway.entity.PaymentEntity;
import io.labs64.paymentgateway.entity.PaymentProviderEntity;
import io.labs64.paymentgateway.entity.PaymentTransactionEntity;
import io.labs64.paymentgateway.integration.auditflow.AuditFlowProperties;
import io.labs64.paymentgateway.model.StatusDetails;
import org.springframework.stereotype.Component;

@Component
public class PaymentEventMapper {

    private static final int EVENT_VERSION = 1;

    private final String sourceSystem;

    public PaymentEventMapper(final AuditFlowProperties properties) {
        this.sourceSystem = properties.getSourceSystem();
    }

    public AuditEvent toAuditEvent(
            final PaymentEventType type,
            final PaymentEntity payment,
            final PaymentTransactionEntity transaction) {
        final OffsetDateTime occurredAt = OffsetDateTime.now(ZoneOffset.UTC);
        final AuditEvents.Builder builder = AuditEvents.builder(type.eventType())
                .eventId(UUID.randomUUID())
                .eventTime(occurredAt)
                .sourceSystem(sourceSystem)
                .tenantId(payment.getTenantId())
                .correlationId(CorrelationContextHolder.get().orElse(null))
                .extra("eventVersion", EVENT_VERSION)
                .extra("payment", toPayment(payment, occurredAt));
        if (transaction != null) {
            builder.extra("transaction", toPaymentTransaction(transaction, occurredAt));
        }
        return builder.build();
    }

    private PaymentSnapshot toPayment(final PaymentEntity payment, final OffsetDateTime fallbackTimestamp) {
        final PaymentProviderEntity paymentProvider = payment.getPaymentProvider();
        final UUID paymentProviderId = payment.getPaymentProviderId() != null
                ? payment.getPaymentProviderId()
                : paymentProvider != null ? paymentProvider.getId() : null;
        return new PaymentSnapshot(
                payment.getId(),
                paymentProviderId,
                paymentProvider != null ? paymentProvider.getProvider() : null,
                payment.getStatus(),
                payment.getType(),
                payment.getDescription(),
                copy(payment.getPurchaseOrder()),
                copy(payment.getBillingInfo()),
                copy(payment.getShippingInfo()),
                copy(payment.getRecurrence()),
                copy(payment.getExtra()),
                timestamp(payment.getCreatedAt(), fallbackTimestamp),
                timestamp(payment.getUpdatedAt(), fallbackTimestamp));
    }

    private PaymentTransactionSnapshot toPaymentTransaction(
            final PaymentTransactionEntity transaction,
            final OffsetDateTime fallbackTimestamp) {
        return new PaymentTransactionSnapshot(
                transaction.getId(),
                transaction.getStatus(),
                copy(transaction.getStatusDetails()),
                copy(transaction.getPspData()),
                timestamp(transaction.getCreatedAt(), fallbackTimestamp),
                timestamp(transaction.getUpdatedAt(), fallbackTimestamp));
    }

    private StatusDetails copy(final StatusDetails value) {
        return value != null
                ? StatusDetails.builder().code(value.getCode()).message(value.getMessage()).build()
                : null;
    }

    private Map<String, Object> copy(final Map<String, Object> value) {
        return value != null ? new LinkedHashMap<>(value) : null;
    }

    private OffsetDateTime timestamp(final OffsetDateTime value, final OffsetDateTime fallback) {
        return value != null ? value : fallback;
    }
}
