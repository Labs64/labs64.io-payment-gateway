package io.labs64.paymentgateway.integration.auditflow;

import io.labs64.auditflow.client.AuditFlowClient;
import io.labs64.auditflow.client.RetryPolicy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = AuditFlowProperties.PREFIX, name = "enabled", havingValue = "true")
@EnableConfigurationProperties(AuditFlowProperties.class)
public class AuditFlowConfiguration {

    @Bean
    AuditFlowClient auditFlowClient(final AuditFlowProperties properties) {
        return AuditFlowClient.builder()
                .baseUrl(properties.getUrl())
                .defaultSourceSystem(properties.getSourceSystem())
                .connectTimeout(properties.getConnectTimeout())
                .requestTimeout(properties.getRequestTimeout())
                .retry(RetryPolicy.exponential(properties.getRetryMaxAttempts()))
                .build();
    }
}
