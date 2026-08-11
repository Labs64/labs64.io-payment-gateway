package io.labs64.paymentgateway.service;

import java.util.UUID;

import io.labs64.paymentgateway.correlation.CorrelationConstants;
import io.labs64.paymentgateway.correlation.CorrelationEntityType;
import io.labs64.paymentgateway.entity.PaymentEntity;
import io.labs64.paymentgateway.entity.PaymentTransactionEntity;
import io.labs64.paymentgateway.exception.NotFoundException;
import io.labs64.paymentgateway.exception.ValidationException;
import io.labs64.paymentgateway.mapper.PaymentContextMapper;
import io.labs64.paymentgateway.psp.internal.PaymentProviderRegistry;
import io.labs64.paymentgateway.psp.spi.PaymentProvider;
import io.labs64.paymentgateway.psp.spi.ProviderWebhookSupport;
import io.labs64.paymentgateway.psp.spi.PaymentWebhookContext;
import io.labs64.paymentgateway.psp.spi.PaymentWebhookResult;
import io.labs64.paymentgateway.psp.spi.WebhookRequest;
import io.labs64.paymentgateway.repository.CorrelationTraceRepository;
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
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final PaymentTransactionService transactionService;
    private final CorrelationTraceRepository correlationTraceRepository;
    private final PaymentContextMapper paymentContextMapper;

    @Override
    @Transactional
    public PaymentWebhookResult processWebhook(final WebhookRequest request) {
        log.info("Processing webhook from provider={}", request.provider());

        final PaymentProvider paymentProvider = providerRegistry.getProvider(request.provider());
        if (!(paymentProvider instanceof ProviderWebhookSupport webhookSupport)) {
            throw new ValidationException("Payment provider does not support webhooks: " + request.provider());
        }

        final UUID transactionId = webhookSupport.extractPaymentTransactionId(request);
        final PaymentTransactionEntity transaction = paymentTransactionRepository.findById(transactionId)
                .orElseThrow(() -> new NotFoundException("Payment transaction not found for ID: " + transactionId));
        final PaymentEntity payment = transaction.getPayment();

        restoreCorrelationId(transaction, payment);
        ensureWebhookProviderMatchesPaymentProvider(request, payment);

        final PaymentWebhookContext context = new PaymentWebhookContext(
                paymentContextMapper.toPaymentTransaction(transaction),
                paymentContextMapper.toProviderConfig(payment.getPaymentProvider()),
                request);
        final PaymentWebhookResult result = webhookSupport.handleWebhook(context);
        transactionService.applyResult(transaction, result);

        log.info("Webhook processed: provider={}, paymentTransactionId={}, status={}",
                request.provider(), transaction.getId(), result.status());

        return result;
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
                        CorrelationEntityType.PAYMENT_TRANSACTION, transaction.getId())
                .or(() -> correlationTraceRepository.findFirstByEntityTypeAndEntityIdOrderByCreatedAtDesc(
                        CorrelationEntityType.PAYMENT, payment.getId()))
                .ifPresent(trace -> MDC.put(CorrelationConstants.MDC_CORRELATION_ID, trace.getCorrelationId()));
    }
}
