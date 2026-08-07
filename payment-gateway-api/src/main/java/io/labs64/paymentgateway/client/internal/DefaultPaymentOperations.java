package io.labs64.paymentgateway.client.internal;

import io.labs64.paymentgateway.client.CallOptions;
import io.labs64.paymentgateway.client.PagedResult;
import io.labs64.paymentgateway.client.PaymentOperations;
import io.labs64.paymentgateway.client.PaymentQuery;
import io.labs64.paymentgateway.model.CreatePaymentRequest;
import io.labs64.paymentgateway.model.ExecutePaymentResponse;
import io.labs64.paymentgateway.model.PayPaymentRequest;
import io.labs64.paymentgateway.model.Payment;
import io.labs64.paymentgateway.model.PaymentListResponse;
import java.util.Objects;
import java.util.UUID;

final class DefaultPaymentOperations implements PaymentOperations {

    private final HttpTransport transport;

    DefaultPaymentOperations(final HttpTransport transport) {
        this.transport = transport;
    }

    @Override
    public PagedResult<Payment> list(final PaymentQuery query, final CallOptions options) {
        Objects.requireNonNull(query, "query");
        final QueryParameters parameters = new QueryParameters()
                .add("status", query.status())
                .add("page", query.page())
                .add("size", query.pageSize())
                .addAll("sort", query.sort());
        final PaymentListResponse response = transport.get(
                "/payments", parameters, PaymentListResponse.class, options);
        return PagedResults.paged(response.getItems(), response.getPage(), response.getPageSize(),
                response.getTotalItems(), response.getTotalPages(), response.getHasPrev(), response.getHasNext());
    }

    @Override
    public Payment create(final CreatePaymentRequest request, final CallOptions options) {
        return transport.post("/payments", Objects.requireNonNull(request, "request"), Payment.class, options);
    }

    @Override
    public Payment get(final UUID paymentId, final CallOptions options) {
        return transport.get(path(paymentId), new QueryParameters(), Payment.class, options);
    }

    @Override
    public ExecutePaymentResponse pay(final UUID paymentId, final PayPaymentRequest request,
            final CallOptions options) {
        return transport.post(path(paymentId) + "/pay", request, ExecutePaymentResponse.class, options);
    }

    @Override
    public Payment close(final UUID paymentId, final CallOptions options) {
        return transport.post(path(paymentId) + "/close", null, Payment.class, options);
    }

    private static String path(final UUID paymentId) {
        return "/payments/" + Objects.requireNonNull(paymentId, "paymentId");
    }
}
