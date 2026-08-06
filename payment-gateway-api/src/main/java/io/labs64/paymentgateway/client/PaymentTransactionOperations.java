package io.labs64.paymentgateway.client;

import io.labs64.paymentgateway.model.PaymentTransaction;
import java.util.UUID;

/** Read operations for payment transactions. */
public interface PaymentTransactionOperations {

    default PagedResult<PaymentTransaction> list() {
        return list(PaymentTransactionQuery.empty(), CallOptions.empty());
    }

    default PagedResult<PaymentTransaction> list(final PaymentTransactionQuery query) {
        return list(query, CallOptions.empty());
    }

    PagedResult<PaymentTransaction> list(PaymentTransactionQuery query, CallOptions options);

    default PaymentTransaction get(final UUID paymentTransactionId) {
        return get(paymentTransactionId, CallOptions.empty());
    }

    PaymentTransaction get(UUID paymentTransactionId, CallOptions options);
}
