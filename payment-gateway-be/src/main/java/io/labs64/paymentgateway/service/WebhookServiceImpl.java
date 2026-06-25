package io.labs64.paymentgateway.service;

import java.util.UUID;

import io.labs64.paymentgateway.correlation.CorrelationConstants;
import io.labs64.paymentgateway.correlation.CorrelationEntityType;
import io.labs64.paymentgateway.entity.PaymentEntity;
import io.labs64.paymentgateway.entity.PaymentTransactionEntity;
import io.labs64.paymentgateway.exception.ConflictException;
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
import io.labs64.paymentgateway.psp.spi.WebhookRequest;
import io.labs64.paymentgateway.repository.CorrelationTraceRepository;
import io.labs64.paymentgateway.repository.PaymentRepository;
import io.labs64.paymentgateway.repository.PaymentTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
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
    private final PaymentEventPublisher paymentEventPublisher;

    @Override
    @Transactional
    public PaymentWebhookResult processWebhook(final WebhookRequest request) {
        log.info("Processing webhook from provider={}", request.provider());

        final PaymentProvider paymentProvider = providerRegistry.getProvider(request.provider());
        final UUID transactionId = paymentProvider.resolvePaymentTransactionId(request);
        final PaymentTransactionEntity transaction = paymentTransactionRepository.findById(transactionId)
                .orElseThrow(() -> new NotFoundException("Payment transaction not found for ID: " + transactionId));
        final PaymentEntity payment = transaction.getPayment();

        restoreCorrelationId(transaction, payment);
        ensureWebhookProviderMatchesPaymentProvider(request, payment);

        final PaymentWebhookContext context = new PaymentWebhookContext(
                paymentContextMapper.toPayment(payment),
                paymentContextMapper.toPaymentTransaction(transaction),
                paymentContextMapper.toProviderConfig(payment.getPaymentProvider()),
                request);
        final PaymentWebhookResult result = paymentProvider.handleWebhook(context);

        if (isTerminal(transaction.getStatus())) {
            return handleAlreadyTerminalTransaction(transaction, result);
        }

        transaction.setStatus(result.status());
        transaction.setStatusDetails(toStatusDetails(result.statusDetails()));
        transaction.setPspData(result.pspData());

        if (PaymentTransactionStatus.SUCCESS.equals(result.status())) {
            if (PaymentType.ONE_TIME.equals(payment.getType())) {
                payment.setStatus(PaymentStatus.CLOSED);
            } else {
                payment.setStatus(PaymentStatus.READY);
            }
            paymentRepository.save(payment);
        }

        if (isTerminal(result.status())) {
            paymentEventPublisher.publishFinalized(payment, transaction);
        }

        if (PaymentTransactionStatus.SUCCESS.equals(result.status()) && PaymentType.ONE_TIME.equals(payment.getType())) {
            paymentEventPublisher.publishClosed(payment, transaction);
        }

        log.info("Webhook processed: provider={}, paymentTransactionId={}, status={}",
                request.provider(), transaction.getId(), result.status());

        return result;
    }

    private PaymentWebhookResult handleAlreadyTerminalTransaction(
            final PaymentTransactionEntity transaction,
            final PaymentWebhookResult result) {
        if (transaction.getStatus().equals(result.status())) {
            log.info("Ignoring duplicate terminal webhook: paymentTransactionId={}, status={}",
                    transaction.getId(), transaction.getStatus());
            return result;
        }

        throw new ConflictException("Payment transaction already has terminal status: " + transaction.getStatus());
    }

    private boolean isTerminal(final PaymentTransactionStatus status) {
        return PaymentTransactionStatus.SUCCESS.equals(status) || PaymentTransactionStatus.FAILED.equals(status);
    }

    private StatusDetails toStatusDetails(final io.labs64.paymentgateway.psp.spi.StatusDetails source) {
        if (source == null) {
            return null;
        }
        return new StatusDetails(source.code(), source.message());
    }

    private void ensureWebhookProviderMatchesPaymentProvider(final WebhookRequest request, final PaymentEntity payment) {
        if (payment.getPaymentProvider() == null) {
            throw new ValidationException("Payment provider is not available for payment: " + payment.getId());
        }

        final String expectedProvider = payment.getPaymentProvider().getProvider();
        if (!request.provider().equals(expectedProvider)) {
            throw new ValidationException("Webhook provider does not match payment provider.");
        }
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
