package io.labs64.paymentgateway.client.internal;

import io.labs64.paymentgateway.client.CallOptions;
import io.labs64.paymentgateway.client.PagedResult;
import io.labs64.paymentgateway.client.PaymentDefinitionOperations;
import io.labs64.paymentgateway.client.PaymentDefinitionQuery;
import io.labs64.paymentgateway.model.PaymentDefinition;
import io.labs64.paymentgateway.model.PaymentDefinitionListResponse;
import java.util.Objects;

final class DefaultPaymentDefinitionOperations implements PaymentDefinitionOperations {

    private final HttpTransport transport;

    DefaultPaymentDefinitionOperations(final HttpTransport transport) {
        this.transport = transport;
    }

    @Override
    public PagedResult<PaymentDefinition> list(
            final PaymentDefinitionQuery query, final CallOptions options) {
        Objects.requireNonNull(query, "query");
        final PaymentDefinitionListResponse response = transport.get(
                "/payment-definitions", new QueryParameters(), PaymentDefinitionListResponse.class, options);
        return PagedResults.singlePage(response.getItems());
    }
}
