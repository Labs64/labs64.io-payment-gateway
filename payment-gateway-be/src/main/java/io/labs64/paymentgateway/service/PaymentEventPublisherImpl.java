package io.labs64.paymentgateway.service;

import io.labs64.paymentgateway.entity.PaymentEntity;
import io.labs64.paymentgateway.entity.PaymentTransactionEntity;
import io.labs64.paymentgateway.event.payment.PaymentEvent;
import io.labs64.paymentgateway.event.payment.PaymentEventMapper;
import io.labs64.paymentgateway.event.payment.PaymentEventType;
import io.labs64.paymentgateway.integration.auditflow.AuditFlowProperties;
import io.labs64.paymentgateway.integration.auditflow.AuditFlowPublisher;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;

import static org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT;

/** Maps payment lifecycle changes and publishes them to AuditFlow after commit. */
@Slf4j
@Service
@ConditionalOnProperty(prefix = AuditFlowProperties.PREFIX, name = "enabled", havingValue = "true")
public class PaymentEventPublisherImpl implements PaymentEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;
    private final PaymentEventMapper eventMapper;
    private final AuditFlowPublisher auditFlowPublisher;

    public PaymentEventPublisherImpl(
            final ApplicationEventPublisher applicationEventPublisher,
            final PaymentEventMapper eventMapper,
            final AuditFlowPublisher auditFlowPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
        this.eventMapper = eventMapper;
        this.auditFlowPublisher = auditFlowPublisher;
    }

    @Override
    public void publishCreated(final PaymentEntity payment) {
        publish(PaymentEventType.CREATED, payment, null);
    }

    @Override
    public void publishFinalized(final PaymentEntity payment, final PaymentTransactionEntity transaction) {
        publish(PaymentEventType.FINALIZED, payment, transaction);
    }

    @Override
    public void publishClosed(final PaymentEntity payment, final PaymentTransactionEntity transaction) {
        publish(PaymentEventType.CLOSED, payment, transaction);
    }

    @Async
    @TransactionalEventListener(phase = AFTER_COMMIT, fallbackExecution = true)
    public void send(final PaymentEvent paymentEvent) {
        try {
            auditFlowPublisher.publish(paymentEvent.auditEvent());
        } catch (RuntimeException exception) {
            log.error("AuditFlow event delivery failed | eventId={}, eventType={}",
                    paymentEvent.auditEvent().getEventId(),
                    paymentEvent.auditEvent().getEventType(),
                    exception);
        }
    }

    private void publish(
            final PaymentEventType type,
            final PaymentEntity payment,
            final PaymentTransactionEntity transaction) {
        applicationEventPublisher.publishEvent(new PaymentEvent(
                eventMapper.toAuditEvent(type, payment, transaction)));
    }
}
