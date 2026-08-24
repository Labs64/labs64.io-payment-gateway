package io.labs64.paymentgateway.integration.auditflow;

import io.labs64.auditflow.client.AuditFlowClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class AuditFlowConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(AuditFlowConfiguration.class)
            .withPropertyValues(
                    "labs64.auditflow.enabled=true",
                    "labs64.auditflow.url=http://auditflow.test",
                    "labs64.auditflow.source-system=labs64.io-payment-gateway");

    @Test
    void createsDirectInternalAuditFlowClient() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(AuditFlowClient.class);

            final AuditFlowProperties properties = context.getBean(AuditFlowProperties.class);
            assertThat(properties.getInternalCall().getServiceName()).isEqualTo("payment-gateway");
            assertThat(properties.getInternalCall().getScopes()).containsExactly("audit-event:write");
        });
    }

    @Test
    void doesNotCreateAuditFlowBeansWhenIntegrationIsDisabled() {
        new ApplicationContextRunner()
                .withUserConfiguration(AuditFlowConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(AuditFlowProperties.class);
                    assertThat(context).doesNotHaveBean(AuditFlowClient.class);
                });
    }

    @Test
    void failsWhenIntegrationIsEnabledWithoutRequiredConfiguration() {
        new ApplicationContextRunner()
                .withUserConfiguration(AuditFlowConfiguration.class)
                .withPropertyValues("labs64.auditflow.enabled=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining("Could not bind properties to 'AuditFlowProperties'");
                });
    }
}
