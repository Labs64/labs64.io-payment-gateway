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

    public static final String PREFIX = "labs64.auditflow";

    private boolean enabled;

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
    private InternalCall internalCall = new InternalCall();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }

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

    public InternalCall getInternalCall() {
        return internalCall;
    }

    public void setInternalCall(final InternalCall internalCall) {
        this.internalCall = internalCall;
    }

    public static class InternalCall {

        @NotBlank
        private String serviceName = "payment-gateway";

        @NotEmpty
        private List<@NotBlank String> scopes = new ArrayList<>(List.of("audit-event:write"));

        public String getServiceName() {
            return serviceName;
        }

        public void setServiceName(final String serviceName) {
            this.serviceName = serviceName;
        }

        public List<String> getScopes() {
            return scopes;
        }

        public void setScopes(final List<String> scopes) {
            this.scopes = scopes;
        }
    }
}
