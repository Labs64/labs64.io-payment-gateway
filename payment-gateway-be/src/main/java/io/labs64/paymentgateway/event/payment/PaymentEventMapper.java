package io.labs64.paymentgateway.event.payment;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import io.labs64.auditflow.client.AuditEvents;
import io.labs64.auditflow.model.AuditEvent;
import io.labs64.paymentgateway.correlation.CorrelationContextHolder;
import io.labs64.paymentgateway.entity.PaymentEntity;
import io.labs64.paymentgateway.entity.PaymentTransactionEntity;
import io.labs64.paymentgateway.integration.auditflow.AuditFlowProperties;
import io.labs64.paymentgateway.mapper.PaymentMapper;
import io.labs64.paymentgateway.mapper.PaymentTransactionMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = AuditFlowProperties.PREFIX, name = "enabled", havingValue = "true")
public class PaymentEventMapper {

    private static final int EVENT_VERSION = 1;

    private final String sourceSystem;
    private final PaymentMapper paymentMapper;
    private final PaymentTransactionMapper paymentTransactionMapper;

    public PaymentEventMapper(
            final AuditFlowProperties properties,
            final PaymentMapper paymentMapper,
            final PaymentTransactionMapper paymentTransactionMapper) {
        this.sourceSystem = properties.getSourceSystem();
        this.paymentMapper = paymentMapper;
        this.paymentTransactionMapper = paymentTransactionMapper;
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
                .extra("payment", paymentMapper.toDto(payment));
        if (transaction != null) {
            builder.extra("transaction", paymentTransactionMapper.toDto(transaction));
        }
        return builder.build();
    }
}
