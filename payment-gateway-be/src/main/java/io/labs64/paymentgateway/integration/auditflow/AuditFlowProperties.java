package io.labs64.paymentgateway.integration.auditflow;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("labs64.auditflow")
public class AuditFlowProperties {

    @NotBlank
    private String url;

    @NotBlank
    private String sourceSystem;

    @NotNull
    private Duration connectTimeout = Duration.ofSeconds(5);

    @NotNull
    private Duration requestTimeout = Duration.ofSeconds(10);

    @Min(1)
    private int retryMaxAttempts = 3;

    @Valid
    @NotNull
    private OAuth2 oauth2 = new OAuth2();

    public String getUrl() {
        return url;
    }

    public void setUrl(final String url) {
        this.url = url;
    }

    public String getSourceSystem() {
        return sourceSystem;
    }

    public void setSourceSystem(final String sourceSystem) {
        this.sourceSystem = sourceSystem;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(final Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(final Duration requestTimeout) {
        this.requestTimeout = requestTimeout;
    }

    public int getRetryMaxAttempts() {
        return retryMaxAttempts;
    }

    public void setRetryMaxAttempts(final int retryMaxAttempts) {
        this.retryMaxAttempts = retryMaxAttempts;
    }

    public OAuth2 getOauth2() {
        return oauth2;
    }

    public void setOauth2(final OAuth2 oauth2) {
        this.oauth2 = oauth2;
    }

    public static class OAuth2 {

        @NotBlank
        private String registrationId = "auditflow";

        @NotBlank
        private String tokenUri;

        @NotBlank
        private String clientId;

        @NotBlank
        private String clientSecret;

        @NotEmpty
        private List<@NotBlank String> scopes = new ArrayList<>();

        public String getRegistrationId() {
            return registrationId;
        }

        public void setRegistrationId(final String registrationId) {
            this.registrationId = registrationId;
        }

        public String getTokenUri() {
            return tokenUri;
        }

        public void setTokenUri(final String tokenUri) {
            this.tokenUri = tokenUri;
        }

        public String getClientId() {
            return clientId;
        }

        public void setClientId(final String clientId) {
            this.clientId = clientId;
        }

        public String getClientSecret() {
            return clientSecret;
        }

        public void setClientSecret(final String clientSecret) {
            this.clientSecret = clientSecret;
        }

        public List<String> getScopes() {
            return scopes;
        }

        public void setScopes(final List<String> scopes) {
            this.scopes = scopes;
        }
    }
}
