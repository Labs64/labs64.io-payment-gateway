package io.labs64.paymentgateway.client;

import io.labs64.paymentgateway.model.PaymentStatus;
import java.util.ArrayList;
import java.util.List;

/** Optional filters and pagination for listing payments. */
public final class PaymentQuery {

    private static final PaymentQuery EMPTY = new Builder().build();

    private final PaymentStatus status;
    private final Integer page;
    private final Integer pageSize;
    private final List<String> sort;

    private PaymentQuery(final Builder builder) {
        this.status = builder.status;
        this.page = nonNegative(builder.page, "page");
        this.pageSize = positive(builder.pageSize, "pageSize");
        this.sort = List.copyOf(builder.sort);
    }

    public static PaymentQuery empty() {
        return EMPTY;
    }

    public static Builder builder() {
        return new Builder();
    }

    public PaymentStatus status() {
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

    private static Integer nonNegative(final Integer value, final String name) {
        if (value != null && value < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
        return value;
    }

    private static Integer positive(final Integer value, final String name) {
        if (value != null && value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    public static final class Builder {

        private PaymentStatus status;
        private Integer page;
        private Integer pageSize;
        private final List<String> sort = new ArrayList<>();

        private Builder() {
        }

        public Builder status(final PaymentStatus status) {
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

        public PaymentQuery build() {
            return new PaymentQuery(this);
        }
    }
}
