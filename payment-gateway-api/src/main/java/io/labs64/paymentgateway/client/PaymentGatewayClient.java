package io.labs64.paymentgateway.client;

import io.labs64.paymentgateway.client.internal.ClientFactory;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Objects;

/**
 * Thread-safe facade for the Payment Gateway API.
 *
 * <p>Resource operation objects are created once and may be safely reused by
 * multiple callers.
 */
public final class PaymentGatewayClient {

    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration DEFAULT_CALL_TIMEOUT = Duration.ofSeconds(30);

    private final URI baseUrl;
    private final PaymentProviderOperations paymentProviders;
    private final PaymentOperations payments;
    private final PaymentTransactionOperations paymentTransactions;
    private final PaymentDefinitionOperations paymentDefinitions;
    private final CheckoutSessionOperations checkoutSessions;

    private PaymentGatewayClient(final Builder builder) {
        this.baseUrl = normalizeBaseUrl(builder.baseUrl);
        final AccessTokenProvider tokenProvider = resolveTokenProvider(builder);
        final HttpClient httpClient = resolveHttpClient(builder);
        final ClientFactory.Operations operations = ClientFactory.create(
                baseUrl,
                httpClient,
                tokenProvider,
                builder.correlationProvider,
                positive(builder.callTimeout, "callTimeout"));
        this.paymentProviders = operations.paymentProviders();
        this.payments = operations.payments();
        this.paymentTransactions = operations.paymentTransactions();
        this.paymentDefinitions = operations.paymentDefinitions();
        this.checkoutSessions = operations.checkoutSessions();
    }

    public static Builder builder() {
        return new Builder();
    }

    public URI baseUrl() {
        return baseUrl;
    }

    public PaymentProviderOperations paymentProviders() {
        return paymentProviders;
    }

    public PaymentOperations payments() {
        return payments;
    }

    public PaymentTransactionOperations paymentTransactions() {
        return paymentTransactions;
    }

    public PaymentDefinitionOperations paymentDefinitions() {
        return paymentDefinitions;
    }

    public CheckoutSessionOperations checkoutSessions() {
        return checkoutSessions;
    }

    private static AccessTokenProvider resolveTokenProvider(final Builder builder) {
        if (builder.accessTokenProvider != null && builder.bearerToken != null) {
            throw new IllegalStateException("bearerToken and accessTokenProvider are mutually exclusive");
        }
        if (builder.bearerToken == null) {
            return builder.accessTokenProvider;
        }
        final String token = builder.bearerToken.trim();
        if (token.isEmpty()) {
            throw new IllegalArgumentException("bearerToken must not be blank");
        }
        return () -> token;
    }

    private static HttpClient resolveHttpClient(final Builder builder) {
        if (builder.httpClient != null) {
            if (builder.connectTimeoutConfigured) {
                throw new IllegalStateException(
                        "connectTimeout cannot be configured together with a custom httpClient");
            }
            return builder.httpClient;
        }
        return HttpClient.newBuilder()
                .connectTimeout(positive(builder.connectTimeout, "connectTimeout"))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    private static URI normalizeBaseUrl(final URI baseUrl) {
        Objects.requireNonNull(baseUrl, "baseUrl is required");
        final String scheme = baseUrl.getScheme();
        if (!baseUrl.isAbsolute() || scheme == null
                || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new IllegalArgumentException("baseUrl must be an absolute HTTP or HTTPS URI");
        }
        if (baseUrl.getHost() == null) {
            throw new IllegalArgumentException("baseUrl must contain a host");
        }
        if (baseUrl.getRawQuery() != null || baseUrl.getRawFragment() != null) {
            throw new IllegalArgumentException("baseUrl must not contain a query or fragment");
        }
        if (baseUrl.getUserInfo() != null) {
            throw new IllegalArgumentException("baseUrl must not contain user info");
        }
        final String normalized = baseUrl.toString().replaceFirst("/+$", "");
        return URI.create(normalized);
    }

    private static Duration positive(final Duration value, final String name) {
        Objects.requireNonNull(value, name);
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    /** Fluent builder for an immutable {@link PaymentGatewayClient}. */
    public static final class Builder {

        private URI baseUrl;
        private AccessTokenProvider accessTokenProvider;
        private String bearerToken;
        private CorrelationProvider correlationProvider;
        private Duration connectTimeout = DEFAULT_CONNECT_TIMEOUT;
        private Duration callTimeout = DEFAULT_CALL_TIMEOUT;
        private HttpClient httpClient;
        private boolean connectTimeoutConfigured;

        private Builder() {
        }

        public Builder baseUrl(final URI baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder accessTokenProvider(final AccessTokenProvider accessTokenProvider) {
            this.accessTokenProvider = accessTokenProvider;
            return this;
        }

        /** Configures a fixed raw token value; the client adds the Bearer scheme. */
        public Builder bearerToken(final String bearerToken) {
            this.bearerToken = bearerToken;
            return this;
        }

        public Builder correlationProvider(final CorrelationProvider correlationProvider) {
            this.correlationProvider = correlationProvider;
            return this;
        }

        public Builder connectTimeout(final Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
            this.connectTimeoutConfigured = true;
            return this;
        }

        public Builder callTimeout(final Duration callTimeout) {
            this.callTimeout = callTimeout;
            return this;
        }

        /**
         * Uses a caller-owned HTTP client. Its connect timeout and executor are
         * preserved, so this option cannot be combined with {@link #connectTimeout(Duration)}.
         */
        public Builder httpClient(final HttpClient httpClient) {
            this.httpClient = httpClient;
            return this;
        }

        public PaymentGatewayClient build() {
            return new PaymentGatewayClient(this);
        }
    }
}
