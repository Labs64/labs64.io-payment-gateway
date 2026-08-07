package io.labs64.paymentgateway.client;

import io.labs64.paymentgateway.model.PaymentProvider;
import io.labs64.paymentgateway.model.PaymentProviderCreateRequest;
import io.labs64.paymentgateway.model.PaymentProviderUpdateRequest;
import java.util.UUID;

/** Operations for tenant payment providers. */
public interface PaymentProviderOperations {

    default PagedResult<PaymentProvider> list() {
        return list(PaymentProviderQuery.empty(), CallOptions.empty());
    }

    default PagedResult<PaymentProvider> list(final PaymentProviderQuery query) {
        return list(query, CallOptions.empty());
    }

    PagedResult<PaymentProvider> list(PaymentProviderQuery query, CallOptions options);

    default PaymentProvider create(final PaymentProviderCreateRequest request) {
        return create(request, CallOptions.empty());
    }

    PaymentProvider create(PaymentProviderCreateRequest request, CallOptions options);

    default PaymentProvider get(final UUID paymentProviderId) {
        return get(paymentProviderId, CallOptions.empty());
    }

    PaymentProvider get(UUID paymentProviderId, CallOptions options);

    default PaymentProvider update(final UUID paymentProviderId, final PaymentProviderUpdateRequest request) {
        return update(paymentProviderId, request, CallOptions.empty());
    }

    PaymentProvider update(UUID paymentProviderId, PaymentProviderUpdateRequest request, CallOptions options);

    default void delete(final UUID paymentProviderId) {
        delete(paymentProviderId, CallOptions.empty());
    }

    void delete(UUID paymentProviderId, CallOptions options);
}
