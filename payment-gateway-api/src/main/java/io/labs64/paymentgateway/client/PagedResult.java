package io.labs64.paymentgateway.client;

import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/** Immutable list of API resources together with its pagination metadata. */
public final class PagedResult<T> implements Iterable<T> {

    private final List<T> items;
    private final PageInfo pageInfo;

    public PagedResult(final List<T> items, final PageInfo pageInfo) {
        this.items = List.copyOf(Objects.requireNonNull(items, "items"));
        this.pageInfo = Objects.requireNonNull(pageInfo, "pageInfo");
    }

    public List<T> items() {
        return List.copyOf(items);
    }

    public PageInfo pageInfo() {
        return pageInfo;
    }

    public int size() {
        return items.size();
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public Stream<T> stream() {
        return items.stream();
    }

    @Override
    public Iterator<T> iterator() {
        return items.iterator();
    }
}
