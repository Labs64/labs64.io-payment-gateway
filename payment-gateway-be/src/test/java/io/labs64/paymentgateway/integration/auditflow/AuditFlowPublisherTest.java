package io.labs64.paymentgateway.integration.auditflow;

import java.util.List;
import java.util.UUID;

import io.labs64.auditflow.client.AuditFlowClient;
import io.labs64.auditflow.client.AuditFlowRequestOptions;
import io.labs64.auditflow.client.PublishResult;
import io.labs64.auditflow.client.exception.AuditFlowException;
import io.labs64.auditflow.model.AuditEvent;
import io.labs64.authcontext.core.AuthHeaders;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AuditFlowPublisherTest {

    @Test
    void publishesWithInternalServiceContext() {
        final AuditFlowClient client = mock(AuditFlowClient.class);
        final AuditFlowPublisher publisher = new AuditFlowPublisher(client, properties());
        final AuditEvent event = event();
        final PublishResult expected = new PublishResult(event.getEventId(), null, 200);
        when(client.publish(eq(event), any(AuditFlowRequestOptions.class))).thenReturn(expected);

        final PublishResult result = publisher.publish(event);

        assertThat(result).isSameAs(expected);
        final ArgumentCaptor<AuditFlowRequestOptions> options =
                ArgumentCaptor.forClass(AuditFlowRequestOptions.class);
        verify(client).publish(eq(event), options.capture());
        assertThat(options.getValue().headers())
                .containsEntry(AuthHeaders.USER, "svc:payment-gateway")
                .containsEntry(AuthHeaders.TENANT, "tenant-1")
                .containsEntry(AuthHeaders.SCOPES, "audit-event:write")
                .containsEntry(AuthHeaders.REQUEST_ID, "request-1");
    }

    @Test
    void usesEventIdAsRequestIdWhenCorrelationIdIsBlank() {
        final AuditFlowClient client = mock(AuditFlowClient.class);
        final AuditFlowPublisher publisher = new AuditFlowPublisher(client, properties());
        final UUID eventId = UUID.randomUUID();
        final AuditEvent event = event()
                .eventId(eventId)
                .correlationId("  ");
        when(client.publish(eq(event), any(AuditFlowRequestOptions.class)))
                .thenReturn(new PublishResult(eventId, null, 200));

        publisher.publish(event);

        final ArgumentCaptor<AuditFlowRequestOptions> options =
                ArgumentCaptor.forClass(AuditFlowRequestOptions.class);
        verify(client).publish(eq(event), options.capture());
        assertThat(options.getValue().headers())
                .containsEntry(AuthHeaders.REQUEST_ID, eventId.toString());
        assertThat(event.getCorrelationId()).isEqualTo(eventId.toString());
    }

    @Test
    void rejectsInvalidServiceNameBeforeCallingClient() {
        final AuditFlowClient client = mock(AuditFlowClient.class);
        final AuditFlowProperties properties = properties();
        properties.getInternalCall().setServiceName("payment gateway");
        final AuditFlowPublisher publisher = new AuditFlowPublisher(client, properties);

        assertThatThrownBy(() -> publisher.publish(event()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("serviceName");
        verifyNoInteractions(client);
    }

    @Test
    void rejectsInvalidScopeBeforeCallingClient() {
        final AuditFlowClient client = mock(AuditFlowClient.class);
        final AuditFlowProperties properties = properties();
        properties.getInternalCall().setScopes(List.of("audit event:write"));
        final AuditFlowPublisher publisher = new AuditFlowPublisher(client, properties);

        assertThatThrownBy(() -> publisher.publish(event()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("scope");
        verifyNoInteractions(client);
    }

    @Test
    void propagatesFailureWithoutTokenRetry() {
        final AuditFlowClient client = mock(AuditFlowClient.class);
        final AuditFlowPublisher publisher = new AuditFlowPublisher(client, properties());
        final AuditEvent event = event();
        final AuditFlowException failure = new AuditFlowException("unauthorized", 401, null);
        when(client.publish(eq(event), any(AuditFlowRequestOptions.class))).thenThrow(failure);

        assertThatThrownBy(() -> publisher.publish(event)).isSameAs(failure);
        verify(client).publish(eq(event), any(AuditFlowRequestOptions.class));
    }

    private static AuditFlowProperties properties() {
        return new AuditFlowProperties();
    }

    private static AuditEvent event() {
        return new AuditEvent()
                .eventId(UUID.randomUUID())
                .eventType("payment.created")
                .tenantId("tenant-1")
                .correlationId("request-1")
                .sourceSystem("labs64.io-payment-gateway");
    }
}
