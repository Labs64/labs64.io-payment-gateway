package io.labs64.paymentgateway.message;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PaymentMessages {
    private final Messages msg;

    public String notFound(final UUID id) {
        return msg.get("payment.not_found", id);
    }

    public String notPayable(final UUID id) {
        return msg.get("payment.not_payable", id);
    }

    public String inactivePaymentProvider(final String provider) {
        return msg.get("payment.payment_provider_inactive", provider);
    }

    public String providerDisabled(final String provider) {
        return msg.get("payment.provider_disabled", provider);
    }

    public String idempotencyConflict(final String idempotencyKey) {
        return msg.get("payment.idempotency_conflict", idempotencyKey);
    }

    public String retryOnlyOneTime(final UUID id) {
        return msg.get("payment.retry_only_one_time", id);
    }

    public String retryRequiresFailedTransaction(final UUID id) {
        return msg.get("payment.retry_requires_failed_transaction", id);
    }

    public String cannotUpdateClosed(final UUID id) {
        return msg.get("payment.cannot_update_closed", id);
    }
}
