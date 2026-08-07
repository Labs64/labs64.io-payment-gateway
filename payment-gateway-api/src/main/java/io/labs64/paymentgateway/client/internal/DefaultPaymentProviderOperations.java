package io.labs64.paymentgateway.client.internal;

import io.labs64.paymentgateway.client.CallOptions;
import io.labs64.paymentgateway.client.PagedResult;
import io.labs64.paymentgateway.client.PaymentProviderOperations;
import io.labs64.paymentgateway.client.PaymentProviderQuery;
import io.labs64.paymentgateway.model.PaymentProvider;
import io.labs64.paymentgateway.model.PaymentProviderCreateRequest;
import io.labs64.paymentgateway.model.PaymentProvidersResponse;
import io.labs64.paymentgateway.model.PaymentProviderUpdateRequest;
import java.util.Objects;
import java.util.UUID;

final class DefaultPaymentProviderOperations implements PaymentProviderOperations {

    private final HttpTransport transport;

    DefaultPaymentProviderOperations(final HttpTransport transport) {
        this.transport = transport;
    }

    @Override
    public PagedResult<PaymentProvider> list(final PaymentProviderQuery query, final CallOptions options) {
        Objects.requireNonNull(query, "query");
        final QueryParameters parameters = new QueryParameters()
                .add("currency", query.currency())
                .add("country", query.country())
                .add("active", query.active())
                .add("page", query.page())
                .add("size", query.pageSize())
                .addAll("sort", query.sort());
        final PaymentProvidersResponse response = transport.get(
                "/payment-providers", parameters, PaymentProvidersResponse.class, options);
        return PagedResults.paged(response.getItems(), response.getPage(), response.getPageSize(),
                response.getTotalItems(), response.getTotalPages(), response.getHasPrev(), response.getHasNext());
    }

    @Override
    public PaymentProvider create(final PaymentProviderCreateRequest request, final CallOptions options) {
        return transport.post("/payment-providers", Objects.requireNonNull(request, "request"),
                PaymentProvider.class, options);
    }

    @Override
    public PaymentProvider get(final UUID paymentProviderId, final CallOptions options) {
        return transport.get(path(paymentProviderId), new QueryParameters(), PaymentProvider.class, options);
    }

    @Override
    public PaymentProvider update(final UUID paymentProviderId, final PaymentProviderUpdateRequest request,
            final CallOptions options) {
        return transport.patch(path(paymentProviderId), Objects.requireNonNull(request, "request"),
                PaymentProvider.class, options);
    }

    @Override
    public void delete(final UUID paymentProviderId, final CallOptions options) {
        transport.delete(path(paymentProviderId), options);
    }

    private static String path(final UUID paymentProviderId) {
        return "/payment-providers/" + Objects.requireNonNull(paymentProviderId, "paymentProviderId");
    }
}
