package io.labs64.paymentgateway.integration.auditflow;

import io.labs64.auditflow.client.AuditFlowClient;
import io.labs64.auditflow.client.PublishResult;
import io.labs64.auditflow.client.exception.AuditFlowException;
import io.labs64.auditflow.model.AuditEvent;
import org.springframework.stereotype.Component;

/** Publishes through AuditFlow and replaces a rejected cached token once. */
@Component
public class AuditFlowPublisher {

    private static final int HTTP_UNAUTHORIZED = 401;

    private final AuditFlowClient client;
    private final AuditFlowAccessTokenProvider tokenProvider;

    public AuditFlowPublisher(
            final AuditFlowClient client,
            final AuditFlowAccessTokenProvider tokenProvider) {
        this.client = client;
        this.tokenProvider = tokenProvider;
    }

    public PublishResult publish(final AuditEvent event) {
        try {
            return client.publish(event);
        } catch (AuditFlowException exception) {
            if (exception.statusCode() != HTTP_UNAUTHORIZED) {
                throw exception;
            }
            tokenProvider.invalidate();
            return client.publish(event);
        }
    }
}
