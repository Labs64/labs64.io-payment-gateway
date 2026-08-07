package io.labs64.paymentgateway.client;

import io.labs64.paymentgateway.model.PaymentDefinition;

/** Read operations for globally supported payment definitions. */
public interface PaymentDefinitionOperations {

    default PagedResult<PaymentDefinition> list() {
        return list(PaymentDefinitionQuery.empty(), CallOptions.empty());
    }

    default PagedResult<PaymentDefinition> list(final PaymentDefinitionQuery query) {
        return list(query, CallOptions.empty());
    }

    PagedResult<PaymentDefinition> list(PaymentDefinitionQuery query, CallOptions options);
}
