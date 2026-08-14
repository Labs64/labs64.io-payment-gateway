package io.labs64.paymentgateway.integration.auditflow;

import io.labs64.auditflow.client.AuditFlowClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

import static org.assertj.core.api.Assertions.assertThat;

class AuditFlowOAuth2ConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(AuditFlowOAuth2Configuration.class)
            .withPropertyValues(
                    "labs64.auditflow.url=http://auditflow.test",
                    "labs64.auditflow.source-system=labs64.io-payment-gateway",
                    "labs64.auditflow.oauth2.token-uri=http://idp.test/token",
                    "labs64.auditflow.oauth2.client-id=payment-gateway",
                    "labs64.auditflow.oauth2.client-secret=test-secret",
                    "labs64.auditflow.oauth2.scopes=audit-event:write,audit-event:delegate-tenant");

    @Test
    void createsClientCredentialsRegistrationAndAuditFlowClient() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(OAuth2AuthorizedClientManager.class);
            assertThat(context).hasSingleBean(AuditFlowClient.class);

            final ClientRegistration registration = context
                    .getBean(ClientRegistrationRepository.class)
                    .findByRegistrationId("auditflow");
            assertThat(registration).isNotNull();
            assertThat(registration.getAuthorizationGrantType())
                    .isEqualTo(AuthorizationGrantType.CLIENT_CREDENTIALS);
            assertThat(registration.getProviderDetails().getTokenUri()).isEqualTo("http://idp.test/token");
            assertThat(registration.getScopes())
                    .containsExactlyInAnyOrder("audit-event:write", "audit-event:delegate-tenant");
        });
    }
}
