package io.labs64.paymentgateway.service;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.labs64.paymentgateway.entity.PaymentEntity;
import io.labs64.paymentgateway.entity.PaymentTransactionEntity;
import io.labs64.paymentgateway.exception.NotFoundException;
import io.labs64.paymentgateway.exception.ValidationException;
import io.labs64.paymentgateway.mapper.PaymentContextMapper;
import io.labs64.paymentgateway.message.PaymentTransactionMessages;
import io.labs64.paymentgateway.message.ProviderExecutionMessages;
import io.labs64.paymentgateway.model.PaymentStatus;
import io.labs64.paymentgateway.model.PaymentTransactionStatus;
import io.labs64.paymentgateway.model.PaymentType;
import io.labs64.paymentgateway.model.StatusDetails;
import io.labs64.paymentgateway.psp.spi.ProviderExecutionFailure;
import io.labs64.paymentgateway.psp.spi.ProviderResult;
import io.labs64.paymentgateway.repository.PaymentTransactionRepository;
import io.labs64.paymentgateway.service.filter.PaymentTransactionFilter;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;

import static io.labs64.paymentgateway.domain.PaymentTransactionStatuses.isTerminal;
import static io.labs64.paymentgateway.psp.spi.PaymentStatusDetailCodes.PROVIDER_AUTHENTICATION_FAILED;
import static io.labs64.paymentgateway.psp.spi.PaymentStatusDetailCodes.PROVIDER_RESPONSE_INVALID;
import static io.labs64.paymentgateway.psp.spi.PaymentStatusDetailCodes.PROVIDER_UNAVAILABLE;

/**
 * Implementation of {@link PaymentTransactionService}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentTransactionServiceImpl implements PaymentTransactionService {
    private final PaymentTransactionRepository repository;
    private final PaymentTransactionMessages msg;
    private final ProviderExecutionMessages providerExecutionMessages;
    private final EntityManager entityManager;
    private final PaymentEventPublisher paymentEventPublisher;

    @Override
    @Transactional(readOnly = true)
    public Optional<PaymentTransactionEntity> find(final String tenantId, final UUID id) {
        log.debug("Find payment transaction for id={}", id);
        return repository.findByIdAndTenantId(id, tenantId);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentTransactionEntity get(final String tenantId, final UUID id) {
        log.debug("Get payment transaction for id={}", id);
        return find(tenantId, id).orElseThrow(() -> new NotFoundException(msg.notFound(id)));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PaymentTransactionEntity> list(
            final String tenantId,
            final PaymentTransactionFilter filter,
            final Pageable pageable) {
        log.debug("Loading payment transactions for filter={}", filter);

        final UUID paymentId = filter != null ? filter.paymentId() : null;
        final PaymentTransactionStatus status = filter != null ? filter.status() : null;
        final Page<PaymentTransactionEntity> result = repository.searchByTenantId(tenantId, paymentId, status, pageable);

        log.debug("Found {} payment transactions for filter={}", result.getTotalElements(), filter);

        return result;
    }

    @Override
    @Transactional
    public PaymentTransactionEntity create(final String tenantId, final PaymentTransactionEntity entity) {
        log.info("Creating payment transaction");

        if (entity == null) {
            throw new ValidationException(msg.required());
        }

        if (entity.getPayment() == null) {
            throw new ValidationException(msg.paymentRequired());
        }

        entity.setTenantId(tenantId);
        entity.setPaymentId(entity.getPayment().getId());

        final PaymentTransactionEntity saved = repository.save(entity);

        log.info("Payment transaction created | id={}", saved.getId());

        return saved;
    }

    @Override
    @Transactional
    public PaymentTransactionEntity update(
            final String tenantId,
            final UUID id,
            final Consumer<PaymentTransactionEntity> updater) {
        log.info("Updating payment transaction | id={}", id);

        return find(tenantId, id)
                .map((pt) -> {
                    updater.accept(pt);
                    log.debug("Update payment transaction | id={}", pt.getId());
                    return pt;
                })
                .orElseThrow(() -> new NotFoundException(msg.notFound(id)));
    }

    @Override
    @Transactional
    public PaymentTransactionEntity updateIfNonTerminal(
            final String tenantId,
            final UUID id,
            final Consumer<PaymentTransactionEntity> updater) {
        log.info("Updating non-terminal payment transaction | id={}", id);

        final PaymentTransactionEntity transaction = repository
                .findByIdAndTenantIdForUpdate(id, tenantId)
                .orElseThrow(() -> new NotFoundException(msg.notFound(id)));
        entityManager.refresh(transaction);

        if (isTerminal(transaction.getStatus())) {
            log.info("Ignoring update of terminal payment transaction | id={}, status={}",
                    id, transaction.getStatus());
            return transaction;
        }

        updater.accept(transaction);
        log.debug("Update non-terminal payment transaction | id={}", transaction.getId());
        return transaction;
    }

    @Override
    @Transactional
    public PaymentTransactionEntity recordProviderExecutionFailure(
            final PaymentTransactionEntity transaction,
            final ProviderExecutionFailure failure) {
        return updateIfNonTerminal(
                transaction.getTenantId(),
                transaction.getId(),
                lockedTransaction -> lockedTransaction.setStatusDetails(StatusDetails.builder()
                        .code(statusDetailCode(failure))
                        .message(providerExecutionMessages.message(failure))
                        .build()));
    }

    @Override
    @Transactional
    public PaymentTransactionEntity applyResult(
            final PaymentTransactionEntity transaction,
            final ProviderResult result) {
        final PaymentTransactionStatus resultStatus = PaymentContextMapper.toModelTransactionStatus(result.status());
        return updateIfNonTerminal(
                transaction.getTenantId(),
                transaction.getId(),
                lockedTransaction -> {
                    final PaymentEntity payment = lockedTransaction.getPayment();
                    lockedTransaction.setStatus(resultStatus);
                    lockedTransaction.setStatusDetails(toStatusDetails(result.statusDetails()));
                    lockedTransaction.setPspData(result.pspData());

                    if (PaymentTransactionStatus.SUCCESS.equals(resultStatus)) {
                        payment.setStatus(PaymentType.ONE_TIME.equals(payment.getType())
                                ? PaymentStatus.CLOSED
                                : PaymentStatus.READY);
                    }

                    if (isTerminal(resultStatus)) {
                        paymentEventPublisher.publishFinalized(payment, lockedTransaction);
                    }

                    if (PaymentTransactionStatus.SUCCESS.equals(resultStatus)
                            && PaymentType.ONE_TIME.equals(payment.getType())) {
                        paymentEventPublisher.publishClosed(payment, lockedTransaction);
                    }
                });
    }

    private static String statusDetailCode(final ProviderExecutionFailure failure) {
        return switch (failure) {
            case UNAVAILABLE -> PROVIDER_UNAVAILABLE;
            case AUTHENTICATION_FAILED -> PROVIDER_AUTHENTICATION_FAILED;
            case INVALID_RESPONSE -> PROVIDER_RESPONSE_INVALID;
        };
    }

    private static StatusDetails toStatusDetails(
            final io.labs64.paymentgateway.psp.spi.StatusDetails source) {
        if (source == null) {
            return null;
        }
        return StatusDetails.builder().code(source.code()).message(source.message()).build();
    }
}
