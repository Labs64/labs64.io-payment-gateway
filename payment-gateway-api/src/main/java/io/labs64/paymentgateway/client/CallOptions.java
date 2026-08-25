package io.labs64.paymentgateway.client;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Optional settings applied to one Payment Gateway API call. */
public final class CallOptions {

    private static final CallOptions EMPTY = new Builder().build();

    private final String correlationId;
    private final String idempotencyKey;
    private final Duration timeout;
    private final Map<String, String> headers;

    private CallOptions(final Builder builder) {
        this.correlationId = normalize(builder.correlationId, "correlationId");
        this.idempotencyKey = normalize(builder.idempotencyKey, "idempotencyKey");
        this.timeout = positive(builder.timeout, "timeout");
        this.headers = Map.copyOf(builder.headers);
    }

    public static CallOptions empty() {
        return EMPTY;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder()
                .correlationId(correlationId)
                .idempotencyKey(idempotencyKey)
                .timeout(timeout)
                .headers(headers);
    }

    public String correlationId() {
        return correlationId;
    }

    public String idempotencyKey() {
        return idempotencyKey;
    }

    public Duration timeout() {
        return timeout;
    }

    public Map<String, String> headers() {
        return headers;
    }

    private static String normalize(final String value, final String name) {
        if (value == null) {
            return null;
        }
        final String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }

    private static Duration positive(final Duration value, final String name) {
        if (value != null && (value.isZero() || value.isNegative())) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    public static final class Builder {

        private String correlationId;
        private final Map<String, String> headers = new LinkedHashMap<>();
        private String idempotencyKey;
        private Duration timeout;

        private Builder() {
        }

        public Builder correlationId(final String correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        public Builder idempotencyKey(final String idempotencyKey) {
            this.idempotencyKey = idempotencyKey;
            return this;
        }

        public Builder timeout(final Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder headers(final Map<String, String> headers) {
            this.headers.clear();
            this.headers.putAll(Objects.requireNonNull(headers, "headers"));
            return this;
        }

        public Builder header(final String name, final String value) {
            this.headers.put(Objects.requireNonNull(name, "name"), Objects.requireNonNull(value, "value"));
            return this;
        }

        public CallOptions build() {
            return new CallOptions(this);
        }
    }
}
