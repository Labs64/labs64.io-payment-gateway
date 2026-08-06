package io.labs64.paymentgateway.client;

import io.labs64.paymentgateway.model.PaymentTransactionStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Optional filters and pagination for listing payment transactions. */
public final class PaymentTransactionQuery {

    private static final PaymentTransactionQuery EMPTY = new Builder().build();

    private final UUID paymentId;
    private final PaymentTransactionStatus status;
    private final Integer page;
    private final Integer pageSize;
    private final List<String> sort;

    private PaymentTransactionQuery(final Builder builder) {
        this.paymentId = builder.paymentId;
        this.status = builder.status;
        this.page = builder.page;
        this.pageSize = builder.pageSize;
        this.sort = List.copyOf(builder.sort);
        if (page != null && page < 0) {
            throw new IllegalArgumentException("page must not be negative");
        }
        if (pageSize != null && pageSize <= 0) {
            throw new IllegalArgumentException("pageSize must be positive");
        }
    }

    public static PaymentTransactionQuery empty() {
        return EMPTY;
    }

    public static Builder builder() {
        return new Builder();
    }

    public UUID paymentId() {
        return paymentId;
    }

    public PaymentTransactionStatus status() {
        return status;
    }

    public Integer page() {
        return page;
    }

    public Integer pageSize() {
        return pageSize;
    }

    public List<String> sort() {
        return sort;
    }

    public static final class Builder {

        private UUID paymentId;
        private PaymentTransactionStatus status;
        private Integer page;
        private Integer pageSize;
        private final List<String> sort = new ArrayList<>();

        private Builder() {
        }

        public Builder paymentId(final UUID paymentId) {
            this.paymentId = paymentId;
            return this;
        }

        public Builder status(final PaymentTransactionStatus status) {
            this.status = status;
            return this;
        }

        public Builder page(final Integer page) {
            this.page = page;
            return this;
        }

        public Builder pageSize(final Integer pageSize) {
            this.pageSize = pageSize;
            return this;
        }

        public Builder sort(final String sort) {
            if (sort == null || sort.isBlank()) {
                throw new IllegalArgumentException("sort must not be blank");
            }
            this.sort.add(sort.trim());
            return this;
        }

        public PaymentTransactionQuery build() {
            return new PaymentTransactionQuery(this);
        }
    }
}
