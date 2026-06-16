package io.labs64.paymentgateway.psp.providers.noop;

import java.util.Map;
import java.util.UUID;

import io.labs64.paymentgateway.model.PaymentTransactionStatus;
import io.labs64.paymentgateway.psp.spi.Payment;
import io.labs64.paymentgateway.psp.spi.PaymentContext;
import io.labs64.paymentgateway.psp.spi.PaymentProvider;
import io.labs64.paymentgateway.psp.spi.PaymentResult;
import io.labs64.paymentgateway.psp.spi.PaymentTransaction;
import io.labs64.paymentgateway.psp.spi.PaymentWebhookContext;
import io.labs64.paymentgateway.psp.spi.PaymentWebhookResult;
import io.labs64.paymentgateway.psp.spi.StatusDetails;
import io.labs64.paymentgateway.psp.spi.WebhookPayload;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * No-Op payment provider for testing purposes.
 * Always returns a successful synchronous payment result.
 */
@Slf4j
@Component
public class NoopPaymentProvider implements PaymentProvider {
    @Override
    public String provider() {
        return "noop";
    }

    @Override
    public PaymentResult execute(final PaymentContext context) {
        final Payment payment = context.payment();
        final PaymentTransaction transaction = context.transaction();

        log.info("Noop PSP: Executing payment for paymentId={}, transaction={}", payment.id(), transaction.id());

        return new PaymentResult(
                provider(),
                PaymentTransactionStatus.SUCCESS,
                Map.of(),
                new StatusDetails("SUCCESS", "TBD"),
                null);
    }

    @Override
    public UUID resolvePaymentTransactionId(WebhookPayload payload) {
        return payload.transactionId();
    }

    @Override
    public PaymentWebhookResult handleWebhook(PaymentWebhookContext context) {
        return new PaymentWebhookResult(
                provider(),
                PaymentTransactionStatus.SUCCESS,
                Map.of(),
                new StatusDetails("SUCCESS", "TBD"));
    }

    @Override
    public Map<String, String> validateAndSanitizePaymentProviderConfig(Map<String, String> config) {
        return Map.of();
    }
}
