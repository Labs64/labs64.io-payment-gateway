package io.labs64.paymentgateway.service;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import io.labs64.paymentgateway.correlation.CorrelationEntityType;
import io.labs64.paymentgateway.domain.PaymentTransactionFailureCode;
import io.labs64.paymentgateway.entity.PaymentTransactionEntity;
import io.labs64.paymentgateway.exception.ConflictException;
import io.labs64.paymentgateway.mapper.PaymentContextMapper;
import io.labs64.paymentgateway.message.PaymentMessages;
import io.labs64.paymentgateway.model.PaymentStatus;
import io.labs64.paymentgateway.model.PaymentTransactionStatus;
import io.labs64.paymentgateway.model.PaymentType;
import io.labs64.paymentgateway.model.StatusDetails;
import io.labs64.paymentgateway.psp.spi.PaymentContext;
import io.labs64.paymentgateway.psp.spi.PaymentProvider;
import io.labs64.paymentgateway.service.filter.PaymentFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.labs64.paymentgateway.entity.PaymentEntity;
import io.labs64.paymentgateway.entity.PaymentProviderEntity;
import io.labs64.paymentgateway.exception.NotFoundException;
import io.labs64.paymentgateway.exception.PaymentNotPayableException;
import io.labs64.paymentgateway.psp.internal.PaymentProviderRegistry;
import io.labs64.paymentgateway.psp.spi.PaymentResult;
import io.labs64.paymentgateway.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;

import static io.labs64.paymentgateway.domain.PaymentTransactionFailureCode.PAYMENT_PROVIDER_NOT_FOUND;
import static io.labs64.paymentgateway.domain.PaymentTransactionFailureCode.PAYMENT_PROVIDER_DISABLED;

/**
 * Implementation of {@link PaymentService}.
 * Handles the full payment lifecycle including creation, execution, closing, and retry.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final CorrelationTraceService correlationTraceService;
    private final PaymentProviderService paymentProviderService;
    private final PaymentTransactionService transactionService;
    private final PaymentNextActionService paymentNextActionService;
    private final PaymentContextMapper paymentContextMapper;
    private final PaymentDefinitionService paymentDefinitionService;
    private final PaymentProviderRegistry providerRegistry;
    private final PaymentMessages msg;

    @Override
    @Transactional(readOnly = true)
    public Optional<PaymentEntity> find(final String tenantId, final UUID id) {
        log.debug("Find payment for tenantId={}, paymentId={}", tenantId, id);
        return paymentRepository.findByIdAndTenantId(id, tenantId);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentEntity get(final String tenantId, final UUID id) {
        log.debug("Get payment for tenantId={}, id={}", tenantId, id);
        return find(tenantId, id).orElseThrow(() -> new NotFoundException(msg.notFound(id)));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PaymentEntity> list(final String tenantId, final PaymentFilter filter, final Pageable pageable) {
        final PaymentStatus status = (filter != null) ? filter.status() : null;
        final Page<PaymentEntity> result = paymentRepository.searchByTenantId(tenantId, status, pageable);
        log.debug("Found {} payments for tenantId={}", result.getTotalElements(), tenantId);

        return result;
    }

    @Override
    @Transactional
    public PaymentEntity create(final String tenantId, final String provider, final PaymentEntity entity) {
        entity.setTenantId(tenantId);
        entity.setPaymentProvider(getActivePaymentProvider(tenantId, provider));
        entity.setStatus(PaymentStatus.READY);

        final PaymentEntity saved = paymentRepository.save(entity);
        log.info("Creating payment for tenantId={}, paymentId={}", tenantId, saved.getId());

        return saved;
    }

    @Override
    @Transactional
    public PaymentEntity update(final String tenantId, final UUID id, final Consumer<PaymentEntity> updater) {
        log.info("Updating payment for tenantId={}, paymentId={}", tenantId, id);

        final PaymentEntity entity = paymentRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new NotFoundException(msg.notFound(id)));

        if (PaymentStatus.CLOSED.equals(entity.getStatus())) {
            throw new ConflictException(msg.cannotUpdateClosed(id));
        }

        updater.accept(entity);

        return entity;
    }

    @Override
    @Transactional
    public PayPaymentResponse pay(final String tenantId, final UUID id) {
        log.info("Pay the payment for tenantId={}, id={}", tenantId, id);
        final PaymentEntity payment = getPayablePayment(tenantId, id);
        final PaymentTransactionEntity transaction = createPendingTransaction(tenantId, payment);

        return switch (preparePaymentAttempt(payment)) {
            case PaymentAttemptRejected rejected -> failPaymentAttempt(payment, transaction, rejected);
            case PaymentAttemptReady ready -> executePaymentAttempt(payment, transaction, ready);
        };
    }

    private PaymentEntity getPayablePayment(final String tenantId, final UUID id) {
        final PaymentEntity payment = paymentRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new NotFoundException(msg.notFound(id)));
        ensurePayable(payment);
        return payment;
    }

    private void ensurePayable(final PaymentEntity payment) {
        if (!PaymentStatus.READY.equals(payment.getStatus())) {
            throw new PaymentNotPayableException(msg.notPayable(payment.getId()));
        }
    }

    private PaymentTransactionEntity createPendingTransaction(final String tenantId, final PaymentEntity payment) {
        final PaymentTransactionEntity transaction = transactionService.create(tenantId, PaymentTransactionEntity.builder()
                .payment(payment)
                .tenantId(tenantId)
                .status(PaymentTransactionStatus.PENDING)
                .build());
        correlationTraceService.attach(CorrelationEntityType.PAYMENT_TRANSACTION, transaction.getId());
        return transaction;
    }

    private PaymentAttemptPreparation preparePaymentAttempt(final PaymentEntity payment) {
        final PaymentProviderEntity paymentProvider = payment.getPaymentProvider();

        if (paymentProvider == null) {
            return new PaymentAttemptRejected(PAYMENT_PROVIDER_NOT_FOUND, msg.notPayable(payment.getId()));
        }

        final String provider = paymentProvider.getProvider();

        if (paymentDefinitionService.findEnabled(provider).isEmpty()) {
            return new PaymentAttemptRejected(PAYMENT_PROVIDER_DISABLED, msg.providerDisabled(provider));
        }

        return new PaymentAttemptReady(paymentProvider);
    }

    private PayPaymentResponse executePaymentAttempt(
            final PaymentEntity payment,
            final PaymentTransactionEntity transaction,
            final PaymentAttemptReady attempt) {
        final PaymentResult result = executeProvider(payment, transaction, attempt);

        applyExecutionResult(payment, transaction, result);

        log.info("Payment executed: paymentId={}, paymentTransactionId={}, status={}",
                payment.getId(), transaction.getId(), transaction.getStatus());

        return new PayPaymentResponse(payment, transaction, result.nextAction());
    }

    private PaymentResult executeProvider(
            final PaymentEntity payment,
            final PaymentTransactionEntity transaction,
            final PaymentAttemptReady attempt) {
        final PaymentProvider provider = providerRegistry.getProvider(attempt.paymentProvider().getProvider());
        final PaymentContext context = paymentContextMapper.toContext(
                payment,
                transaction,
                attempt.paymentProvider());
        return provider.execute(context);
    }

    private PayPaymentResponse failPaymentAttempt(
            final PaymentEntity payment,
            final PaymentTransactionEntity transaction,
            final PaymentAttemptRejected failure) {
        return new PayPaymentResponse(payment, failTransaction(transaction, failure.code(), failure.message()), null);
    }

    private PaymentTransactionEntity failTransaction(
            final PaymentTransactionEntity transaction,
            final PaymentTransactionFailureCode code,
            final String message) {
        return transactionService.update(transaction.getTenantId(), transaction.getId(), (pt) -> {
            pt.setStatus(PaymentTransactionStatus.FAILED);
            pt.setStatusDetails(new StatusDetails(code.name(), message));
            pt.setPspData(null);
        });
    }

    private void applyExecutionResult(
            final PaymentEntity payment,
            final PaymentTransactionEntity transaction,
            final PaymentResult result) {
        transactionService.update(transaction.getTenantId(), transaction.getId(), (pt) -> {
            pt.setStatus(result.status());
            pt.setStatusDetails(toStatusDetails(result.statusDetails()));
            pt.setPspData(result.pspData());
        });

        if (PaymentTransactionStatus.SUCCESS.equals(result.status()) && PaymentType.ONE_TIME.equals(payment.getType())) {
            payment.setStatus(PaymentStatus.CLOSED);
        }

        if (result.nextAction() != null) {
            paymentNextActionService.create(transaction, result.nextAction());
        }
    }

    private StatusDetails toStatusDetails(final io.labs64.paymentgateway.psp.spi.StatusDetails source) {
        if (source == null) {
            return null;
        }
        return new StatusDetails(source.code(), source.message());
    }

    private PaymentProviderEntity getActivePaymentProvider(final String tenantId, final String provider) {
        final PaymentProviderEntity paymentProvider = paymentProviderService.get(tenantId, provider);
        if (!paymentProvider.isActive()) {
            throw new ConflictException(msg.inactivePaymentProvider(provider));
        }
        return paymentProvider;
    }

    private sealed interface PaymentAttemptPreparation permits PaymentAttemptReady, PaymentAttemptRejected {
    }

    private record PaymentAttemptReady(
            PaymentProviderEntity paymentProvider) implements PaymentAttemptPreparation {
    }

    private record PaymentAttemptRejected(
            PaymentTransactionFailureCode code,
            String message) implements PaymentAttemptPreparation {
    }
}
