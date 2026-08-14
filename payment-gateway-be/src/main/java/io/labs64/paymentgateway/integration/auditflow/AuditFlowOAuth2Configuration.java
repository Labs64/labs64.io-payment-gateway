package io.labs64.paymentgateway.integration.auditflow;

import io.labs64.auditflow.client.AuditFlowClient;
import io.labs64.auditflow.client.RetryPolicy;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AuditFlowProperties.class)
public class AuditFlowOAuth2Configuration {

    @Bean
    ClientRegistrationRepository auditFlowClientRegistrationRepository(
            final AuditFlowProperties properties) {
        final AuditFlowProperties.OAuth2 oauth2 = properties.getOauth2();
        final ClientRegistration registration = ClientRegistration
                .withRegistrationId(oauth2.getRegistrationId())
                .clientId(oauth2.getClientId())
                .clientSecret(oauth2.getClientSecret())
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .tokenUri(oauth2.getTokenUri())
                .scope(oauth2.getScopes())
                .build();
        return new InMemoryClientRegistrationRepository(registration);
    }

    @Bean
    OAuth2AuthorizedClientService auditFlowAuthorizedClientService(
            final ClientRegistrationRepository clientRegistrationRepository) {
        return new InMemoryOAuth2AuthorizedClientService(clientRegistrationRepository);
    }

    @Bean
    OAuth2AuthorizedClientManager auditFlowAuthorizedClientManager(
            final ClientRegistrationRepository clientRegistrationRepository,
            final OAuth2AuthorizedClientService authorizedClientService) {
        final OAuth2AuthorizedClientProvider provider = OAuth2AuthorizedClientProviderBuilder.builder()
                .clientCredentials()
                .build();
        final AuthorizedClientServiceOAuth2AuthorizedClientManager manager =
                new AuthorizedClientServiceOAuth2AuthorizedClientManager(
                        clientRegistrationRepository,
                        authorizedClientService);
        manager.setAuthorizedClientProvider(provider);
        return manager;
    }

    @Bean
    AuditFlowAccessTokenProvider auditFlowAccessTokenProvider(
            final OAuth2AuthorizedClientManager authorizedClientManager,
            final OAuth2AuthorizedClientService authorizedClientService,
            final AuditFlowProperties properties) {
        return new AuditFlowAccessTokenProvider(
                authorizedClientManager,
                authorizedClientService,
                properties.getOauth2().getRegistrationId());
    }

    @Bean
    AuditFlowClient auditFlowClient(
            final AuditFlowProperties properties,
            final AuditFlowAccessTokenProvider tokenProvider) {
        return AuditFlowClient.builder()
                .baseUrl(properties.getUrl())
                .tokenProvider(tokenProvider)
                .defaultSourceSystem(properties.getSourceSystem())
                .connectTimeout(properties.getConnectTimeout())
                .requestTimeout(properties.getRequestTimeout())
                .retry(RetryPolicy.exponential(properties.getRetryMaxAttempts()))
                .build();
    }
}
