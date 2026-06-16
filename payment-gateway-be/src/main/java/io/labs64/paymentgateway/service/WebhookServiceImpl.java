package io.labs64.paymentgateway.service;

import java.util.Map;
import java.util.UUID;

import io.labs64.paymentgateway.correlation.CorrelationConstants;
import io.labs64.paymentgateway.correlation.CorrelationEntityType;
import io.labs64.paymentgateway.entity.PaymentEntity;
import io.labs64.paymentgateway.entity.PaymentTransactionEntity;
import io.labs64.paymentgateway.exception.NotFoundException;
import io.labs64.paymentgateway.exception.ValidationException;
import io.labs64.paymentgateway.mapper.PaymentContextMapper;
import io.labs64.paymentgateway.model.PaymentStatus;
import io.labs64.paymentgateway.model.PaymentTransactionStatus;
import io.labs64.paymentgateway.model.PaymentType;
import io.labs64.paymentgateway.model.StatusDetails;
import io.labs64.paymentgateway.psp.internal.PaymentProviderRegistry;
import io.labs64.paymentgateway.psp.spi.PaymentProvider;
import io.labs64.paymentgateway.psp.spi.PaymentWebhookContext;
import io.labs64.paymentgateway.psp.spi.PaymentWebhookResult;
import io.labs64.paymentgateway.psp.spi.WebhookPayload;
import io.labs64.paymentgateway.repository.CorrelationTraceRepository;
import io.labs64.paymentgateway.repository.PaymentRepository;
import io.labs64.paymentgateway.repository.PaymentTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link WebhookService}.
 * Routes webhook payloads to the matching PSP provider and lets the gateway
 * keep ownership of payment and transaction state changes.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookServiceImpl implements WebhookService {

    private final PaymentProviderRegistry providerRegistry;
    private final PaymentRepository paymentRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final CorrelationTraceRepository correlationTraceRepository;
    private final PaymentContextMapper paymentContextMapper;
    private final StreamBridge streamBridge;

    @Override
    @Transactional
    public PaymentWebhookResult processWebhook(final String provider, final Map<String, Object> payload) {
        log.info("Processing webhook from provider={}", provider);

        final PaymentProvider paymentProvider = providerRegistry.getProvider(provider);
        final UUID transactionId = paymentProvider.resolvePaymentTransactionId(toWebhookPayload(payload));
        final PaymentTransactionEntity transaction = paymentTransactionRepository.findById(transactionId)
                .orElseThrow(() -> new NotFoundException("Payment transaction not found for ID: " + transactionId));
        final PaymentEntity payment = transaction.getPayment();

        restoreCorrelationId(transaction, payment);

        final PaymentWebhookContext context = new PaymentWebhookContext(
                paymentContextMapper.toPayment(payment),
                paymentContextMapper.toPaymentTransaction(transaction),
                paymentContextMapper.toProviderConfig(payment.getPaymentProvider()),
                payload,
                Map.of());
        final PaymentWebhookResult result = paymentProvider.handleWebhook(context);

        final StatusDetails statusDetails = new StatusDetails(result.statusDetails().code(), result.statusDetails().message());

        transaction.setStatus(result.status());
        transaction.setStatusDetails(statusDetails);
        transaction.setPspData(result.pspData());

        if (PaymentTransactionStatus.SUCCESS.equals(result.status())) {
            if (PaymentType.ONE_TIME.equals(payment.getType())) {
                payment.setStatus(PaymentStatus.CLOSED);
            } else {
                payment.setStatus(PaymentStatus.READY);
            }
            paymentRepository.save(payment);
            streamBridge.send("paymentFinalized-out-0", payment);
        }

        log.info("Webhook processed: provider={}, paymentTransactionId={}, status={}",
                provider, transaction.getId(), result.status());

        return result;
    }

    private WebhookPayload toWebhookPayload(final Map<String, Object> payload) {
        final Object rawTransactionId = payload.get("transactionId");
        if (rawTransactionId == null) {
            throw new ValidationException("Webhook payload must contain transactionId.");
        }
        return new WebhookPayload(UUID.fromString(rawTransactionId.toString()));
    }

    private void restoreCorrelationId(final PaymentTransactionEntity transaction, final PaymentEntity payment) {
        correlationTraceRepository
                .findFirstByEntityTypeAndEntityIdOrderByCreatedAtDesc(
                        CorrelationEntityType.PAYMENT_TRANSACTION.name(), transaction.getId())
                .or(() -> correlationTraceRepository.findFirstByEntityTypeAndEntityIdOrderByCreatedAtDesc(
                        CorrelationEntityType.PAYMENT.name(), payment.getId()))
                .ifPresent(trace -> MDC.put(CorrelationConstants.MDC_CORRELATION_ID, trace.getCorrelationId()));
    }
}
