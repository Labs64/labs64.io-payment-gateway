package io.labs64.paymentgateway.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import io.labs64.paymentgateway.correlation.CorrelationEntityType;
import io.labs64.paymentgateway.entity.CheckoutSessionEntity;
import io.labs64.paymentgateway.entity.PaymentTransactionEntity;
import io.labs64.paymentgateway.exception.ConflictException;
import io.labs64.paymentgateway.mapper.PaymentContextMapper;
import io.labs64.paymentgateway.message.PaymentMessages;
import io.labs64.paymentgateway.model.PaymentStatus;
import io.labs64.paymentgateway.model.PaymentTransactionStatus;
import io.labs64.paymentgateway.psp.spi.CheckoutPreparationContext;
import io.labs64.paymentgateway.psp.spi.PaymentExecutionRequest;
import io.labs64.paymentgateway.psp.spi.PaymentNextAction;
import io.labs64.paymentgateway.psp.spi.PaymentContext;
import io.labs64.paymentgateway.psp.spi.PaymentProvider;
import io.labs64.paymentgateway.psp.spi.ProviderExecutionException;
import io.labs64.paymentgateway.psp.spi.CheckoutSessionDraft;
import io.labs64.paymentgateway.psp.spi.ProviderCheckoutSupport;
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
import io.labs64.paymentgateway.psp.spi.ProviderCheckout;
import io.labs64.paymentgateway.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;


/**
 * Implementation of {@link PaymentService}.
 * Handles the full payment lifecycle including creation, execution, closing,
 * and retry.
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
    private final CheckoutSessionService checkoutSessionService;
    private final CheckoutCallbackUrlFactory checkoutCallbackUrlFactory;
    private final PaymentContextMapper paymentContextMapper;
    private final PaymentDefinitionService paymentDefinitionService;
    private final PaymentProviderRegistry providerRegistry;
    private final PaymentEventPublisher paymentEventPublisher;
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
    public PaymentEntity create(final String tenantId, final UUID paymentProviderId, final PaymentEntity entity) {
        entity.setTenantId(tenantId);
        entity.setPaymentProvider(getActivePaymentProvider(tenantId, paymentProviderId));
        ensureRecurringAllowed(entity);
        entity.setStatus(PaymentStatus.READY);

        final PaymentEntity saved = paymentRepository.save(entity);
        if (saved.getId() != null) {
            correlationTraceService.attach(CorrelationEntityType.PAYMENT, saved.getId());
        }
        paymentEventPublisher.publishCreated(saved);
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
        ensureRecurringAllowed(entity);

        return entity;
    }

    @Override
    @Transactional
    public PaymentEntity close(final String tenantId, final UUID id) {
        log.info("Closing payment for tenantId={}, paymentId={}", tenantId, id);

        final PaymentEntity entity = paymentRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new NotFoundException(msg.notFound(id)));

        if (PaymentStatus.CLOSED.equals(entity.getStatus())) {
            throw new ConflictException(msg.cannotUpdateClosed(id));
        }

        entity.setStatus(PaymentStatus.CLOSED);
        paymentEventPublisher.publishClosed(entity, null);

        return entity;
    }

    @Override
    @Transactional
    public PayPaymentResponse pay(final String tenantId, final UUID id) {
        return pay(tenantId, id, PaymentExecutionRequest.empty());
    }

    @Override
    @Transactional
    public PayPaymentResponse pay(final String tenantId, final UUID id, final PaymentExecutionRequest request) {
        log.info("Pay the payment for tenantId={}, id={}", tenantId, id);
        final PaymentEntity payment = getPayablePayment(tenantId, id);
        final PaymentExecutionRequest executionRequest = request != null ? request : PaymentExecutionRequest.empty();

        final PaymentProviderEntity paymentProvider = requirePayableProvider(payment);
        return executePaymentAttempt(tenantId, payment, paymentProvider, executionRequest);
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
        final PaymentTransactionEntity transaction = transactionService.create(tenantId,
                PaymentTransactionEntity.builder()
                        .payment(payment)
                        .tenantId(tenantId)
                        .status(PaymentTransactionStatus.PENDING)
                        .build());
        correlationTraceService.attach(CorrelationEntityType.PAYMENT_TRANSACTION, transaction.getId());
        return transaction;
    }

    private PaymentProviderEntity requirePayableProvider(final PaymentEntity payment) {
        final PaymentProviderEntity paymentProvider = payment.getPaymentProvider();

        if (paymentProvider == null) {
            throw new PaymentNotPayableException(msg.notPayable(payment.getId()));
        }

        if (!paymentProvider.isActive()) {
            throw new PaymentNotPayableException(msg.inactivePaymentProvider(paymentProvider.getProvider()));
        }

        final String provider = paymentProvider.getProvider();

        if (paymentDefinitionService.findEnabled(provider).isEmpty()) {
            throw new PaymentNotPayableException(msg.providerDisabled(provider));
        }

        return paymentProvider;
    }

    private PayPaymentResponse executePaymentAttempt(
            final String tenantId,
            final PaymentEntity payment,
            final PaymentProviderEntity paymentProvider,
            final PaymentExecutionRequest request) {
        final PaymentProvider provider = providerRegistry.getProvider(paymentProvider.getProvider());
        final CheckoutSessionDraft checkoutDraft = prepareCheckoutSessionDraft(payment, paymentProvider, provider, request)
                .orElse(null);
        final PaymentTransactionEntity transaction = createPendingTransaction(tenantId, payment);
        final CheckoutSessionEntity session = checkoutDraft != null
                ? createCheckoutSession(transaction, checkoutDraft)
                : null;
        final PaymentResult result;
        try {
            result = executeProvider(payment, transaction, paymentProvider, provider, session, request);
        } catch (ProviderExecutionException ex) {
            log.warn(
                    "Payment provider execution failed: paymentId={}, paymentTransactionId={}, provider={}, message={}",
                    payment.getId(), transaction.getId(), paymentProvider.getProvider(), ex.getMessage(), ex);

            recordProviderExecutionFailure(transaction, ex);
            return new PayPaymentResponse(payment, transaction, null);
        }

        applyExecutionResult(transaction, session, result);

        log.info("Payment executed: paymentId={}, paymentTransactionId={}, status={}",
                payment.getId(), transaction.getId(), transaction.getStatus());

        return new PayPaymentResponse(payment, transaction, result.nextAction());
    }

    private PaymentResult executeProvider(
            final PaymentEntity payment,
            final PaymentTransactionEntity transaction,
            final PaymentProviderEntity paymentProvider,
            final PaymentProvider provider,
            final CheckoutSessionEntity session,
            final PaymentExecutionRequest request) {
        final ProviderCheckout checkout = session == null
                ? null
                : new ProviderCheckout(
                        paymentContextMapper.toCheckoutSession(session),
                        checkoutCallbackUrlFactory.create(paymentProvider.getProvider(), session.getId()));
        final PaymentContext context = paymentContextMapper.toContext(
                payment, transaction, paymentProvider, request, checkout);
        return provider.execute(context);
    }

    private Optional<CheckoutSessionDraft> prepareCheckoutSessionDraft(
            final PaymentEntity payment,
            final PaymentProviderEntity paymentProvider,
            final PaymentProvider provider,
            final PaymentExecutionRequest request) {
        if (!(provider instanceof ProviderCheckoutSupport checkoutSupport)) {
            return Optional.empty();
        }

        final CheckoutPreparationContext context = paymentContextMapper
                .toCheckoutPreparationContext(payment, paymentProvider, request);

        return checkoutSupport.prepareCheckoutSession(context);
    }

    private CheckoutSessionEntity createCheckoutSession(
            final PaymentTransactionEntity transaction,
            final CheckoutSessionDraft draft) {
        return checkoutSessionService.create(transaction, draft.payload(), draft.expiresAt());
    }

    private void recordProviderExecutionFailure(
            final PaymentTransactionEntity transaction,
            final ProviderExecutionException exception) {
        transactionService.recordProviderExecutionFailure(transaction, exception.failure());
    }

    private void applyExecutionResult(
            final PaymentTransactionEntity transaction,
            final CheckoutSessionEntity session,
            final PaymentResult result) {
        transactionService.applyResult(transaction, result);

        if (result.nextAction() != null && session != null) {
            checkoutSessionService.updateNextAction(
                    session.getTenantId(),
                    session.getId(),
                    toNextActionData(result.nextAction()));
        } else if (result.nextAction() != null) {
            paymentNextActionService.create(transaction, result.nextAction());
        }
    }

    private Map<String, Object> toNextActionData(final PaymentNextAction nextAction) {
        final Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", nextAction.type().name());
        data.put("details", nextAction.details());
        return data;
    }

    private void ensureRecurringAllowed(final PaymentEntity payment) {
        if (!io.labs64.paymentgateway.model.PaymentType.RECURRING.equals(payment.getType())) {
            return;
        }

        final String provider = payment.getPaymentProvider().getProvider();
        final boolean recurringSupported = paymentDefinitionService.findEnabled(provider)
                .map(definition -> definition.isRecurring())
                .orElse(false);
        if (!recurringSupported) {
            throw new ConflictException(msg.recurringNotAllowed(provider));
        }
    }

    private PaymentProviderEntity getActivePaymentProvider(final String tenantId, final UUID paymentProviderId) {
        final PaymentProviderEntity paymentProvider = paymentProviderService.get(tenantId, paymentProviderId);
        if (!paymentProvider.isActive()) {
            throw new ConflictException(msg.inactivePaymentProvider(paymentProvider.getProvider()));
        }
        return paymentProvider;
    }
}
