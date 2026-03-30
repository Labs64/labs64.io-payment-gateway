package io.labs64.paymentgateway.psp.noop;

import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import io.labs64.paymentgateway.entity.PaymentEntity;
import io.labs64.paymentgateway.entity.TransactionEntity.TransactionStatus;
import io.labs64.paymentgateway.psp.PaymentProvider;
import io.labs64.paymentgateway.psp.PspNextAction;
import io.labs64.paymentgateway.psp.PspPaymentResponse;
import io.labs64.paymentgateway.psp.PspWebhookResult;

/**
 * No-Op payment provider for testing purposes.
 * Always returns a successful synchronous payment result.
 */
@Component
public class NoOpPaymentProvider implements PaymentProvider {

    private static final Logger log = LoggerFactory.getLogger(NoOpPaymentProvider.class);

    @Override
    public String getProviderId() {
        return "noop";
    }

    @Override
    public PspPaymentResponse executePayment(final PaymentEntity payment, final Map<String, String> pspConfig) {
        log.info("NoOp PSP: Executing payment for paymentId={}, amount={} {}", payment.getId(), payment.getAmount(),
                payment.getCurrency());

        return PspPaymentResponse.builder()
                .status(TransactionStatus.SUCCESS)
                .pspData(Map.of(
                        "provider", "noop",
                        "referenceId", "noop_" + UUID.randomUUID(),
                        "message", "NoOp payment always succeeds"))
                .nextAction(PspNextAction.builder()
                        .type("none")
                        .build())
                .asyncCompletion(false)
                .build();
    }

    @Override
    public PspWebhookResult verifyWebhook(final Map<String, Object> payload, final Map<String, String> pspConfig) {
        log.info("NoOp PSP: Webhook verification (always valid)");

        UUID paymentId = null;
        if (payload != null && payload.containsKey("paymentId")) {
            try {
                paymentId = UUID.fromString(payload.get("paymentId").toString());
            } catch (Exception e) {
                log.warn("Failed to parse paymentId from NoOp webhook payload", e);
            }
        }

        TransactionStatus status = TransactionStatus.SUCCESS;
        if (payload != null && payload.containsKey("status")) {
            try {
                status = TransactionStatus.valueOf(payload.get("status").toString().toUpperCase());
            } catch (Exception e) {
                log.warn("Failed to parse status from NoOp webhook payload", e);
            }
        }

        return PspWebhookResult.builder()
                .valid(true)
                .transactionStatus(status)
                .paymentId(paymentId)
                .pspData(payload)
                .build();
    }

    @Override
    public boolean supportsRecurring() {
        return false;
    }
}
