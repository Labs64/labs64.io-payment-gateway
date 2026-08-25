package io.labs64.paymentgateway.service;

import io.labs64.auditflow.client.AuditFlowClient;
import io.labs64.paymentgateway.event.payment.PaymentEventMapper;
import io.labs64.paymentgateway.integration.auditflow.AuditFlowConfiguration;
import io.labs64.paymentgateway.integration.auditflow.AuditFlowPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentEventPublisherConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(
                    AuditFlowConfiguration.class,
                    AuditFlowPublisher.class,
                    PaymentEventMapper.class,
                    PaymentEventPublisherImpl.class,
                    NoOpPaymentEventPublisher.class);

    @Test
    void usesNoOpPublisherWhenAuditFlowIsNotConfigured() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(PaymentEventPublisher.class);
            assertThat(context.getBean(PaymentEventPublisher.class))
                    .isInstanceOf(NoOpPaymentEventPublisher.class);
            assertThat(context).doesNotHaveBean(AuditFlowClient.class);
            assertThat(context).doesNotHaveBean(PaymentEventMapper.class);
        });
    }

    @Test
    void usesAuditFlowPublisherWhenIntegrationIsEnabledAndConfigured() {
        contextRunner
                .withPropertyValues(
                        "labs64.auditflow.enabled=true",
                        "labs64.auditflow.url=http://auditflow.test",
                        "labs64.auditflow.source-system=labs64.io-payment-gateway")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(PaymentEventPublisher.class);
                    assertThat(context.getBean(PaymentEventPublisher.class))
                            .isInstanceOf(PaymentEventPublisherImpl.class);
                    assertThat(context).hasSingleBean(AuditFlowClient.class);
                    assertThat(context).hasSingleBean(PaymentEventMapper.class);
                    assertThat(context).doesNotHaveBean(NoOpPaymentEventPublisher.class);
                });
    }
}
