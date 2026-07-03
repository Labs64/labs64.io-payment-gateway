package io.labs64.paymentgateway.message;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentProviderMessages {
    private final Messages msg;

    public String notFound(final String provider) {
        return msg.get("payment_provider.not_found", provider);
    }

    public String notSupported(final String provider) {
        return msg.get("payment_provider.not_supported", provider);
    }

    public String alreadyExists(final String provider) {
        return msg.get("payment_provider.already_exists", provider);
    }

    public String configRequired(final String provider) {
        return msg.get("payment_provider.config_required", provider);
    }

    public String configScopeRequired(final String scope) {
        return msg.get("payment_provider.config_scope_required", scope);
    }

    public String cannotDeleteWithPayments(final String provider) {
        return msg.get("payment_provider.cannot_delete_with_payments", provider);
    }
}
