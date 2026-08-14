package io.labs64.paymentgateway.integration.auditflow;

import io.labs64.auditflow.client.AuditFlowClient;
import io.labs64.auditflow.client.PublishResult;
import io.labs64.auditflow.client.exception.AuditFlowException;
import io.labs64.auditflow.model.AuditEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuditFlowPublisherTest {

    @Test
    void invalidatesRejectedTokenAndRetriesSameEventOnce() {
        final AuditFlowClient client = mock(AuditFlowClient.class);
        final AuditFlowAccessTokenProvider tokenProvider = mock(AuditFlowAccessTokenProvider.class);
        final AuditFlowPublisher publisher = new AuditFlowPublisher(client, tokenProvider);
        final AuditEvent event = event();
        final PublishResult expected = new PublishResult(event.getEventId(), null, 200);
        when(client.publish(event))
                .thenThrow(new AuditFlowException("expired token", 401, null))
                .thenReturn(expected);

        final PublishResult result = publisher.publish(event);

        assertThat(result).isSameAs(expected);
        verify(tokenProvider).invalidate();
        verify(client, org.mockito.Mockito.times(2)).publish(event);
    }

    @Test
    void doesNotRetryNonAuthenticationFailure() {
        final AuditFlowClient client = mock(AuditFlowClient.class);
        final AuditFlowAccessTokenProvider tokenProvider = mock(AuditFlowAccessTokenProvider.class);
        final AuditFlowPublisher publisher = new AuditFlowPublisher(client, tokenProvider);
        final AuditEvent event = event();
        final AuditFlowException failure = new AuditFlowException("forbidden", 403, null);
        when(client.publish(event)).thenThrow(failure);

        assertThatThrownBy(() -> publisher.publish(event)).isSameAs(failure);
        verify(tokenProvider, never()).invalidate();
        verify(client).publish(event);
    }

    private static AuditEvent event() {
        return new AuditEvent()
                .eventId(java.util.UUID.randomUUID())
                .eventType("payment.created")
                .sourceSystem("labs64.io-payment-gateway");
    }
}
