package io.labs64.paymentgateway.integration.auditflow;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import io.labs64.auditflow.client.AuditFlowClient;
import io.labs64.auditflow.client.AuditFlowRequestOptions;
import io.labs64.auditflow.client.PublishResult;
import io.labs64.auditflow.model.AuditEvent;
import io.labs64.authcontext.core.AuthContextHeaders;

import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Publishes directly to AuditFlow with an internal service auth context. */
@Component
@ConditionalOnProperty(prefix = AuditFlowProperties.PREFIX, name = "enabled", havingValue = "true")
public class AuditFlowPublisher {

    private final AuditFlowClient client;
    private final String serviceName;
    private final Set<String> scopes;

    public AuditFlowPublisher(
            final AuditFlowClient client,
            final AuditFlowProperties properties) {
        this.client = client;
        this.serviceName = properties.getInternalCall().getServiceName();
        this.scopes = Set.copyOf(properties.getInternalCall().getScopes());
    }

    public PublishResult publish(final AuditEvent event) {
        final String requestId = requestId(event);
        final Map<String, String> headers = AuthContextHeaders.builder()
                .servicePrincipal(serviceName)
                .tenant(event.getTenantId())
                .scopes(scopes)
                .requestId(requestId)
                .build();
        final AuditFlowRequestOptions requestOptions = AuditFlowRequestOptions.builder()
                .headers(headers)
                .build();

        return client.publish(event, requestOptions);
    }

    private String requestId(final AuditEvent event) {
        if (StringUtils.isNotBlank(event.getCorrelationId())) {
            return event.getCorrelationId();
        }

        final String requestId = event.getEventId() != null
                ? event.getEventId().toString()
                : UUID.randomUUID().toString();
        event.setCorrelationId(requestId);
        return requestId;
    }
}
