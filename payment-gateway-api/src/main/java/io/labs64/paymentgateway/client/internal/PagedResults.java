package io.labs64.paymentgateway.client.internal;

import io.labs64.paymentgateway.client.PageInfo;
import io.labs64.paymentgateway.client.PagedResult;
import io.labs64.paymentgateway.client.PaymentGatewaySerializationException;
import java.util.List;

final class PagedResults {

    private PagedResults() {
    }

    static <T> PagedResult<T> singlePage(final List<T> items) {
        final List<T> safeItems = items == null ? List.of() : items;
        return new PagedResult<>(safeItems, PageInfo.singlePage(safeItems.size()));
    }

    static <T> PagedResult<T> paged(
            final List<T> items,
            final Integer page,
            final Integer pageSize,
            final Long totalItems,
            final Integer totalPages,
            final Boolean hasPrevious,
            final Boolean hasNext) {
        if (items == null || page == null || pageSize == null || totalItems == null || totalPages == null
                || hasPrevious == null || hasNext == null) {
            throw new PaymentGatewaySerializationException(
                    "Payment Gateway returned incomplete pagination metadata", null);
        }
        return new PagedResult<>(items,
                new PageInfo(page, pageSize, totalItems, totalPages, hasPrevious, hasNext));
    }
}
