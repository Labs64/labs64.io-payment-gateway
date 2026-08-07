package io.labs64.paymentgateway.client;

import java.time.Duration;

/** Optional settings applied to one Payment Gateway API call. */
public final class CallOptions {

    private static final CallOptions EMPTY = new Builder().build();

    private final String correlationId;
    private final String idempotencyKey;
    private final Duration timeout;

    private CallOptions(final Builder builder) {
        this.correlationId = normalize(builder.correlationId, "correlationId");
        this.idempotencyKey = normalize(builder.idempotencyKey, "idempotencyKey");
        this.timeout = positive(builder.timeout, "timeout");
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
                .timeout(timeout);
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

        public CallOptions build() {
            return new CallOptions(this);
        }
    }
}
