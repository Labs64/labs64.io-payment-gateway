package io.labs64.paymentgateway.service;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

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
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.Message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentEventPublisherImplTest {

    private static final String TENANT_ID = "tenant-a";
    private static final String PROVIDER = "noop";
    private static final String CORRELATION_ID = "correlation-1";

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @Mock
    private StreamBridge streamBridge;

    private PaymentEventPublisherImpl publisher;

    @BeforeEach
    void setUp() {
        publisher = new PaymentEventPublisherImpl(
                applicationEventPublisher,
                streamBridge,
                "labs64.io-payment-gateway");
    }

    @AfterEach
    void tearDown() {
        CorrelationContextHolder.clear();
    }

    @Test
    void publishFinalizedBuildsStablePayloadWithCorrelationId() {
        CorrelationContextHolder.set(CORRELATION_ID);
        final PaymentEntity payment = payment();
        final PaymentTransactionEntity transaction = transaction(payment);

        publisher.publishFinalized(payment, transaction);

        final ArgumentCaptor<PaymentEvent> captor = ArgumentCaptor.forClass(PaymentEvent.class);
        verify(applicationEventPublisher).publishEvent(captor.capture());

        final PaymentEvent publication = captor.getValue();
        final Event<PaymentEventPayload> message = publication.message();

        assertThat(publication.route()).isEqualTo(PaymentEventRoute.FINALIZED);
        assertThat(message.event().type()).isEqualTo("payment.finalized");
        assertThat(message.event().version()).isEqualTo(1);
        assertThat(message.event().origin()).isEqualTo("labs64.io-payment-gateway");
        assertThat(message.tenantId()).isEqualTo(TENANT_ID);
        assertThat(message.correlationId()).isEqualTo(CORRELATION_ID);
        assertThat(message.payload().payment().id()).isEqualTo(payment.getId());
        assertThat(message.payload().payment().provider()).isEqualTo(PROVIDER);
        assertThat(message.payload().payment().purchaseOrder()).containsEntry("grossAmount", 3000L);
        assertThat(message.payload().transaction().id()).isEqualTo(transaction.getId());
        assertThat(message.payload().transaction().status()).isEqualTo(PaymentTransactionStatus.SUCCESS);
        assertThat(message.payload().transaction().statusDetails()).isEqualTo(new StatusDetails("SUCCESS", "Success"));
    }

    @Test
    void sendUsesBindingAndCorrelationHeaders() {
        final PaymentEntity payment = payment();
        final Event<PaymentEventPayload> message = new Event<>(
                new EventMetadata(
                        UUID.randomUUID(),
                        "payment.created",
                        1,
                        "payment-gateway",
                        OffsetDateTime.now()),
                TENANT_ID,
                CORRELATION_ID,
                new PaymentEventPayload(
                        new PaymentSnapshot(
                                payment.getId(),
                                PROVIDER,
                                PaymentStatus.READY,
                                payment.getType(),
                                null,
                                payment.getPurchaseOrder(),
                                null,
                                null,
                                null,
                                null,
                                payment.getCreatedAt(),
                                payment.getUpdatedAt()),
                        null));
        when(streamBridge.send(eq("paymentCreated-out-0"), any())).thenReturn(true);

        publisher.send(new PaymentEvent(PaymentEventRoute.CREATED, message));

        final ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(streamBridge).send(eq("paymentCreated-out-0"), captor.capture());
        assertThat(captor.getValue().getHeaders()).containsEntry("eventType", "payment.created");
        assertThat(captor.getValue().getHeaders()).containsEntry("X-Correlation-ID", CORRELATION_ID);
        assertThat(captor.getValue().getPayload()).isSameAs(message);
    }

    private static PaymentEntity payment() {
        return PaymentEntity.builder()
                .id(UUID.randomUUID())
                .tenantId(TENANT_ID)
                .paymentProvider(PaymentProviderEntity.builder()
                        .id(UUID.randomUUID())
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
                .statusDetails(new StatusDetails("SUCCESS", "Success"))
                .pspData(Map.of("providerReference", "noop-1"))
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
    }
}
