package io.labs64.paymentgateway.service;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import io.labs64.auditflow.model.AuditEvent;
import io.labs64.paymentgateway.correlation.CorrelationContextHolder;
import io.labs64.paymentgateway.entity.PaymentEntity;
import io.labs64.paymentgateway.entity.PaymentProviderEntity;
import io.labs64.paymentgateway.entity.PaymentTransactionEntity;
import io.labs64.paymentgateway.event.payment.PaymentEvent;
import io.labs64.paymentgateway.event.payment.PaymentEventMapper;
import io.labs64.paymentgateway.event.payment.PaymentSnapshot;
import io.labs64.paymentgateway.event.payment.PaymentTransactionSnapshot;
import io.labs64.paymentgateway.integration.auditflow.AuditFlowProperties;
import io.labs64.paymentgateway.integration.auditflow.AuditFlowPublisher;
import io.labs64.paymentgateway.model.PaymentStatus;
import io.labs64.paymentgateway.model.PaymentTransactionStatus;
import io.labs64.paymentgateway.model.StatusDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentEventPublisherImplTest {

    private static final String TENANT_ID = "tenant-a";
    private static final String PROVIDER = "noop";
    private static final String CORRELATION_ID = "correlation-1";
    private static final String SOURCE_SYSTEM = "labs64.io-payment-gateway";
    private static final UUID PAYMENT_PROVIDER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440010");

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @Mock
    private AuditFlowPublisher auditFlowPublisher;

    private PaymentEventPublisherImpl publisher;

    @BeforeEach
    void setUp() {
        final AuditFlowProperties properties = new AuditFlowProperties();
        properties.setSourceSystem(SOURCE_SYSTEM);
        publisher = new PaymentEventPublisherImpl(
                applicationEventPublisher,
                new PaymentEventMapper(properties),
                auditFlowPublisher);
    }

    @AfterEach
    void tearDown() {
        CorrelationContextHolder.clear();
    }

    @Test
    void publishFinalizedBuildsAuditEventWithDomainSnapshots() {
        CorrelationContextHolder.set(CORRELATION_ID);
        final PaymentEntity payment = payment();
        final PaymentTransactionEntity transaction = transaction(payment);

        publisher.publishFinalized(payment, transaction);

        final ArgumentCaptor<PaymentEvent> captor = ArgumentCaptor.forClass(PaymentEvent.class);
        verify(applicationEventPublisher).publishEvent(captor.capture());

        final AuditEvent event = captor.getValue().auditEvent();
        final PaymentSnapshot paymentSnapshot = (PaymentSnapshot) event.getExtra().get("payment");
        final PaymentTransactionSnapshot transactionSnapshot =
                (PaymentTransactionSnapshot) event.getExtra().get("transaction");

        assertThat(event.getEventType()).isEqualTo("payment.finalized");
        assertThat(event.getSourceSystem()).isEqualTo(SOURCE_SYSTEM);
        assertThat(event.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(event.getCorrelationId()).isEqualTo(CORRELATION_ID);
        assertThat(event.getEventId()).isNotNull();
        assertThat(event.getEventTime()).isNotNull();
        assertThat(event.getExtra()).containsEntry("eventVersion", 1);
        assertThat(paymentSnapshot.id()).isEqualTo(payment.getId());
        assertThat(paymentSnapshot.paymentProviderId()).isEqualTo(PAYMENT_PROVIDER_ID);
        assertThat(paymentSnapshot.provider()).isEqualTo(PROVIDER);
        assertThat(paymentSnapshot.purchaseOrder()).containsEntry("grossAmount", 3000L);
        assertThat(transactionSnapshot.id()).isEqualTo(transaction.getId());
        assertThat(transactionSnapshot.status()).isEqualTo(PaymentTransactionStatus.SUCCESS);
        assertThat(transactionSnapshot.statusDetails())
                .isEqualTo(new StatusDetails().code("SUCCESS").message("Success"));
    }

    @Test
    void sendPublishesMappedEventThroughAuditFlow() {
        final AuditEvent event = new AuditEvent()
                .eventType("payment.created")
                .sourceSystem(SOURCE_SYSTEM)
                .tenantId(TENANT_ID);

        publisher.send(new PaymentEvent(event));

        verify(auditFlowPublisher).publish(event);
    }

    @Test
    void sendDoesNotPropagateAuditFlowFailure() {
        final AuditEvent event = new AuditEvent()
                .eventId(UUID.randomUUID())
                .eventType("payment.created")
                .sourceSystem(SOURCE_SYSTEM)
                .tenantId(TENANT_ID);
        doThrow(new IllegalStateException("AuditFlow unavailable"))
                .when(auditFlowPublisher).publish(event);

        assertThatCode(() -> publisher.send(new PaymentEvent(event)))
                .doesNotThrowAnyException();

        verify(auditFlowPublisher).publish(event);
    }

    private static PaymentEntity payment() {
        return PaymentEntity.builder()
                .id(UUID.randomUUID())
                .tenantId(TENANT_ID)
                .paymentProviderId(PAYMENT_PROVIDER_ID)
                .paymentProvider(PaymentProviderEntity.builder()
                        .id(PAYMENT_PROVIDER_ID)
                        .tenantId(TENANT_ID)
                        .provider(PROVIDER)
                        .build())
                .status(PaymentStatus.READY)
                .purchaseOrder(Map.of("grossAmount", 3000L, "currency", "USD"))
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
    }

    private static PaymentTransactionEntity transaction(final PaymentEntity payment) {
        return PaymentTransactionEntity.builder()
                .id(UUID.randomUUID())
                .tenantId(TENANT_ID)
                .payment(payment)
                .status(PaymentTransactionStatus.SUCCESS)
                .statusDetails(new StatusDetails().code("SUCCESS").message("Success"))
                .pspData(Map.of("providerReference", "noop-1"))
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
    }
}
