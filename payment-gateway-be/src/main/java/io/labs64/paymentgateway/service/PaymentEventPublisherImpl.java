package io.labs64.paymentgateway.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import io.labs64.paymentgateway.correlation.CorrelationConstants;
import io.labs64.paymentgateway.correlation.CorrelationContextHolder;
import io.labs64.paymentgateway.entity.PaymentEntity;
import io.labs64.paymentgateway.entity.PaymentProviderEntity;
import io.labs64.paymentgateway.entity.PaymentTransactionEntity;
import io.labs64.paymentgateway.event.EventMetadata;
import io.labs64.paymentgateway.event.Event;
import io.labs64.paymentgateway.event.payment.PaymentEventPayload;
import io.labs64.paymentgateway.event.payment.PaymentEvent;
import io.labs64.paymentgateway.event.payment.PaymentEventRoute;
import io.labs64.paymentgateway.event.payment.PaymentSnapshot;
import io.labs64.paymentgateway.event.payment.PaymentTransactionSnapshot;
import io.labs64.paymentgateway.model.StatusDetails;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;

import static org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT;

/**
 * Builds stable payment event payloads and sends them to RabbitMQ after commit.
 */
@Slf4j
@Service
public class PaymentEventPublisherImpl implements PaymentEventPublisher {

    private static final String EVENT_ID_HEADER = "eventId";
    private static final String EVENT_TYPE_HEADER = "eventType";

    private final ApplicationEventPublisher applicationEventPublisher;
    private final StreamBridge streamBridge;
    private final String origin;

    public PaymentEventPublisherImpl(
            final ApplicationEventPublisher applicationEventPublisher,
            final StreamBridge streamBridge,
            @Value("${spring.application.name}") final String origin) {
        this.applicationEventPublisher = applicationEventPublisher;
        this.streamBridge = streamBridge;
        this.origin = origin;
    }

    @Override
    public void publishCreated(final PaymentEntity payment) {
        publish(PaymentEventRoute.CREATED, payment, null);
    }

    @Override
    public void publishFinalized(final PaymentEntity payment, final PaymentTransactionEntity transaction) {
        publish(PaymentEventRoute.FINALIZED, payment, transaction);
    }

    @Override
    public void publishClosed(final PaymentEntity payment, final PaymentTransactionEntity transaction) {
        publish(PaymentEventRoute.CLOSED, payment, transaction);
    }

    @TransactionalEventListener(phase = AFTER_COMMIT, fallbackExecution = true)
    public void send(final PaymentEvent paymentEvent) {
        final Event<PaymentEventPayload> message = paymentEvent.message();
        final MessageBuilder<Event<PaymentEventPayload>> messageBuilder = MessageBuilder
                .withPayload(message)
                .setHeader(EVENT_ID_HEADER, message.event().id().toString())
                .setHeader(EVENT_TYPE_HEADER, message.event().type());

        if (message.correlationId() != null) {
            messageBuilder.setHeader(CorrelationConstants.CORRELATION_ID_HEADER, message.correlationId());
        }

        final boolean sent = streamBridge.send(paymentEvent.route().bindingName(), messageBuilder.build());
        if (!sent) {
            log.warn("Payment event was not accepted by stream bridge: eventType={}, paymentId={}",
                    message.event().type(), message.payload().payment().id());
        }
    }

    private void publish(
            final PaymentEventRoute route,
            final PaymentEntity payment,
            final PaymentTransactionEntity transaction) {
        applicationEventPublisher.publishEvent(new PaymentEvent(route, toMessage(route, payment, transaction)));
    }

    private Event<PaymentEventPayload> toMessage(
            final PaymentEventRoute route,
            final PaymentEntity payment,
            final PaymentTransactionEntity transaction) {
        final OffsetDateTime occurredAt = OffsetDateTime.now(ZoneOffset.UTC);
        return new Event<>(
                new EventMetadata(
                        UUID.randomUUID(),
                        route.eventType(),
                        1,
                        origin,
                        occurredAt),
                payment.getTenantId(),
                CorrelationContextHolder.get().orElse(null),
                new PaymentEventPayload(
                        toPayment(payment, occurredAt),
                        toPaymentTransaction(transaction, occurredAt)));
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
        if (transaction == null) {
            return null;
        }
        return new PaymentTransactionSnapshot(
                transaction.getId(),
                transaction.getStatus(),
                copy(transaction.getStatusDetails()),
                copy(transaction.getPspData()),
                timestamp(transaction.getCreatedAt(), fallbackTimestamp),
                timestamp(transaction.getUpdatedAt(), fallbackTimestamp));
    }

    private StatusDetails copy(final StatusDetails value) {
        return value != null ? new StatusDetails(value.getCode(), value.getMessage()) : null;
    }

    private Map<String, Object> copy(final Map<String, Object> value) {
        return value != null ? new LinkedHashMap<>(value) : null;
    }

    private OffsetDateTime timestamp(final OffsetDateTime value, final OffsetDateTime fallback) {
        return value != null ? value : fallback;
    }
}
