package io.labs64.paymentgateway.client;

/** Pagination metadata returned with a list operation. */
public record PageInfo(
        int pageNumber,
        int pageSize,
        long totalItems,
        int totalPages,
        boolean hasPrevious,
        boolean hasNext) {

    public PageInfo {
        if (pageNumber < 0 || pageSize < 0 || totalItems < 0 || totalPages < 0) {
            throw new IllegalArgumentException("Pagination values must not be negative");
        }
    }

    public static PageInfo singlePage(final int itemCount) {
        if (itemCount < 0) {
            throw new IllegalArgumentException("itemCount must not be negative");
        }
        return new PageInfo(0, itemCount, itemCount, itemCount == 0 ? 0 : 1, false, false);
    }
}
