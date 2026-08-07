package io.labs64.paymentgateway.client;

import java.util.ArrayList;
import java.util.List;

/** Optional filters for listing tenant payment providers. */
public final class PaymentProviderQuery {

    private static final PaymentProviderQuery EMPTY = new Builder().build();

    private final String currency;
    private final String country;
    private final Boolean active;
    private final Integer page;
    private final Integer pageSize;
    private final List<String> sort;

    private PaymentProviderQuery(final Builder builder) {
        this.currency = normalizeCode(builder.currency, 3, "currency");
        this.country = normalizeCode(builder.country, 2, "country");
        this.active = builder.active;
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

    public static PaymentProviderQuery empty() {
        return EMPTY;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String currency() {
        return currency;
    }

    public String country() {
        return country;
    }

    public Boolean active() {
        return active;
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

    private static String normalizeCode(final String value, final int length, final String name) {
        if (value == null) {
            return null;
        }
        final String normalized = value.trim().toUpperCase(java.util.Locale.ROOT);
        if (normalized.length() != length || !normalized.chars().allMatch(Character::isLetter)) {
            throw new IllegalArgumentException(name + " must contain " + length + " letters");
        }
        return normalized;
    }

    public static final class Builder {

        private String currency;
        private String country;
        private Boolean active;
        private Integer page;
        private Integer pageSize;
        private final List<String> sort = new ArrayList<>();

        private Builder() {
        }

        public Builder currency(final String currency) {
            this.currency = currency;
            return this;
        }

        public Builder country(final String country) {
            this.country = country;
            return this;
        }

        public Builder active(final Boolean active) {
            this.active = active;
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

        public PaymentProviderQuery build() {
            return new PaymentProviderQuery(this);
        }
    }
}
