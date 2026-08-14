package io.labs64.paymentgateway.integration.auditflow;

import io.labs64.auditflow.client.auth.TokenProvider;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;

/** Adapts Spring Security's authorized-client lifecycle to AuditFlow's token callback. */
public class AuditFlowAccessTokenProvider implements TokenProvider {

    private static final String PRINCIPAL_NAME = "svc:payment-gateway";

    private final OAuth2AuthorizedClientManager authorizedClientManager;
    private final OAuth2AuthorizedClientService authorizedClientService;
    private final String registrationId;

    public AuditFlowAccessTokenProvider(
            final OAuth2AuthorizedClientManager authorizedClientManager,
            final OAuth2AuthorizedClientService authorizedClientService,
            final String registrationId) {
        this.authorizedClientManager = authorizedClientManager;
        this.authorizedClientService = authorizedClientService;
        this.registrationId = registrationId;
    }

    @Override
    public String token() {
        final OAuth2AuthorizeRequest request = OAuth2AuthorizeRequest
                .withClientRegistrationId(registrationId)
                .principal(PRINCIPAL_NAME)
                .build();
        final OAuth2AuthorizedClient client = authorizedClientManager.authorize(request);
        if (client == null) {
            throw new IllegalStateException("OAuth2 authorization returned no client for " + registrationId);
        }
        return client.getAccessToken().getTokenValue();
    }

    public void invalidate() {
        authorizedClientService.removeAuthorizedClient(registrationId, PRINCIPAL_NAME);
    }
}
