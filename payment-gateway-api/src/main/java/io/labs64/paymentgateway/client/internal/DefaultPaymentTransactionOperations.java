package io.labs64.paymentgateway.client.internal;

import io.labs64.paymentgateway.client.CallOptions;
import io.labs64.paymentgateway.client.PagedResult;
import io.labs64.paymentgateway.client.PaymentTransactionOperations;
import io.labs64.paymentgateway.client.PaymentTransactionQuery;
import io.labs64.paymentgateway.model.PaymentTransaction;
import io.labs64.paymentgateway.model.PaymentTransactionsResponse;
import java.util.Objects;
import java.util.UUID;

final class DefaultPaymentTransactionOperations implements PaymentTransactionOperations {

    private final HttpTransport transport;

    DefaultPaymentTransactionOperations(final HttpTransport transport) {
        this.transport = transport;
    }

    @Override
    public PagedResult<PaymentTransaction> list(final PaymentTransactionQuery query, final CallOptions options) {
        Objects.requireNonNull(query, "query");
        final QueryParameters parameters = new QueryParameters()
                .add("paymentId", query.paymentId())
                .add("status", query.status())
                .add("page", query.page())
                .add("size", query.pageSize())
                .addAll("sort", query.sort());
        final PaymentTransactionsResponse response = transport.get(
                "/payment-transactions", parameters, PaymentTransactionsResponse.class, options);
        return PagedResults.paged(response.getItems(), response.getPage(), response.getPageSize(),
                response.getTotalItems(), response.getTotalPages(), response.getHasPrev(), response.getHasNext());
    }

    @Override
    public PaymentTransaction get(final UUID paymentTransactionId, final CallOptions options) {
        final String path = "/payment-transactions/"
                + Objects.requireNonNull(paymentTransactionId, "paymentTransactionId");
        return transport.get(path, new QueryParameters(), PaymentTransaction.class, options);
    }
}
