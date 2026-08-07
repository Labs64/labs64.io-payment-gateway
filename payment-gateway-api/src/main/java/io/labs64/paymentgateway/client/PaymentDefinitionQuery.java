package io.labs64.paymentgateway.client;

/**
 * Filters for listing payment definitions.
 *
 * <p>The current contract has no filters; keeping the query type in the public
 * API allows filters to be added later without changing the list operation.
 */
public final class PaymentDefinitionQuery {

    private static final PaymentDefinitionQuery EMPTY = new PaymentDefinitionQuery();

    private PaymentDefinitionQuery() {
    }

    public static PaymentDefinitionQuery empty() {
        return EMPTY;
    }
}
