package io.labs64.paymentgateway.service;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.labs64.paymentgateway.entity.PaymentEntity;
import io.labs64.paymentgateway.entity.TransactionEntity;
import io.labs64.paymentgateway.entity.TransactionEntity.TransactionStatus;
import io.labs64.paymentgateway.exception.NotFoundException;
import io.labs64.paymentgateway.exception.ValidationException;
import io.labs64.paymentgateway.psp.PaymentProvider;
import io.labs64.paymentgateway.psp.PaymentProviderRegistry;
import io.labs64.paymentgateway.psp.PspWebhookResult;
import io.labs64.paymentgateway.repository.PaymentRepository;
import io.labs64.paymentgateway.repository.TransactionRepository;
import io.labs64.paymentgateway.web.CorrelationIdFilter;
import lombok.RequiredArgsConstructor;

/**
 * Implementation of {@link WebhookService}.
 * Routes webhook to the appropriate PSP adapter, verifies signature,
 * and updates transaction status.
 */
@Service
@RequiredArgsConstructor
public class WebhookServiceImpl implements WebhookService {

    private static final Logger log = LoggerFactory.getLogger(WebhookServiceImpl.class);

    private final PaymentProviderRegistry providerRegistry;
    private final PaymentRepository paymentRepository;
    private final TransactionRepository transactionRepository;
    private final StreamBridge streamBridge;

    @Override
    @Transactional
    public PspWebhookResult processWebhook(final String provider, final Map<String, Object> payload) {
        log.info("Processing webhook from provider={}", provider);

        final PaymentProvider paymentProvider = providerRegistry.getProvider(provider);

        // For Milestone 2, we use empty config - tenant resolution will happen via webhook payload
        final PspWebhookResult result = paymentProvider.verifyWebhook(payload, Map.of());

        if (!result.isValid()) {
            throw new ValidationException("Webhook signature verification failed for provider: " + provider);
        }

        if (result.getPaymentId() != null) {
            final PaymentEntity payment = paymentRepository.findById(result.getPaymentId())
                    .orElseThrow(() -> new NotFoundException("Payment not found for ID: " + result.getPaymentId()));

            // Restore correlationId from original payment record
            MDC.put(CorrelationIdFilter.MDC_CORRELATION_ID, payment.getCorrelationId());
            // MDC.put("tenantId", payment.getTenantId()); // Custom MDC logging

            // Update transaction status based on webhook result
            final TransactionEntity transaction = transactionRepository.findFirstByPaymentIdOrderByCreatedAtDesc(payment.getId())
                    .orElseThrow(() -> new NotFoundException("No pending transaction found for payment ID: " + payment.getId()));

            transaction.setStatus(result.getTransactionStatus());
            if (result.getFailureCode() != null) {
                transaction.setFailureCode(result.getFailureCode());
            }
            if (result.getFailureMessage() != null) {
                transaction.setFailureMessage(result.getFailureMessage());
            }
            transactionRepository.save(transaction);

            // Update Payment Status
            if (result.getTransactionStatus() == TransactionStatus.SUCCESS) {
                if (payment.getType() == PaymentEntity.PaymentType.ONE_TIME) {
                    payment.setStatus(PaymentEntity.PaymentStatus.CLOSED);
                } else {
                    payment.setStatus(PaymentEntity.PaymentStatus.ACTIVE);
                }
                paymentRepository.save(payment);

                // Publish payment.finalized event
                log.info("Publishing payment.finalized event for paymentId={}", payment.getId());
                streamBridge.send("paymentFinalized-out-0", payment);
            } else if (result.getTransactionStatus() == TransactionStatus.FAILED) {
                payment.setStatus(PaymentEntity.PaymentStatus.INCOMPLETE);
                paymentRepository.save(payment);
            }
        }

        log.info("Webhook processed: provider={}, valid={}, status={}",
                provider, result.isValid(), result.getTransactionStatus());

        return result;
    }
}
