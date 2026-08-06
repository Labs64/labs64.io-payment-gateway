package io.labs64.paymentgateway.client;

import io.labs64.paymentgateway.model.CreatePaymentRequest;
import io.labs64.paymentgateway.model.ExecutePaymentResponse;
import io.labs64.paymentgateway.model.PayPaymentRequest;
import io.labs64.paymentgateway.model.Payment;
import java.util.UUID;

/** Operations for payment instances. */
public interface PaymentOperations {

    default PagedResult<Payment> list() {
        return list(PaymentQuery.empty(), CallOptions.empty());
    }

    default PagedResult<Payment> list(final PaymentQuery query) {
        return list(query, CallOptions.empty());
    }

    PagedResult<Payment> list(PaymentQuery query, CallOptions options);

    default Payment create(final CreatePaymentRequest request) {
        return create(request, CallOptions.empty());
    }

    Payment create(CreatePaymentRequest request, CallOptions options);

    default Payment get(final UUID paymentId) {
        return get(paymentId, CallOptions.empty());
    }

    Payment get(UUID paymentId, CallOptions options);

    default ExecutePaymentResponse pay(final UUID paymentId) {
        return pay(paymentId, null, CallOptions.empty());
    }

    default ExecutePaymentResponse pay(final UUID paymentId, final PayPaymentRequest request) {
        return pay(paymentId, request, CallOptions.empty());
    }

    ExecutePaymentResponse pay(UUID paymentId, PayPaymentRequest request, CallOptions options);

    default Payment close(final UUID paymentId) {
        return close(paymentId, CallOptions.empty());
    }

    Payment close(UUID paymentId, CallOptions options);
}
