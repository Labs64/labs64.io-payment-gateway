package io.labs64.paymentgateway.service;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.labs64.paymentgateway.entity.PaymentTransactionEntity;
import io.labs64.paymentgateway.exception.NotFoundException;
import io.labs64.paymentgateway.exception.ValidationException;
import io.labs64.paymentgateway.message.PaymentTransactionMessages;
import io.labs64.paymentgateway.model.PaymentTransactionStatus;
import io.labs64.paymentgateway.repository.PaymentTransactionRepository;
import io.labs64.paymentgateway.service.filter.PaymentTransactionFilter;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;

/**
 * Implementation of {@link PaymentTransactionService}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentTransactionServiceImpl implements PaymentTransactionService {
    private final PaymentTransactionRepository repository;
    private final PaymentTransactionMessages msg;

    @Override
    @Transactional(readOnly = true)
    public Optional<PaymentTransactionEntity> find(final String tenantId, final UUID id) {
        log.debug("Find payment transaction for tenantId={}, id={}", tenantId, id);
        return repository.findByIdAndTenantId(id, tenantId);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentTransactionEntity get(final String tenantId, final UUID id) {
        log.debug("Get payment transaction for tenantId={}, id={}", tenantId, id);
        return find(tenantId, id).orElseThrow(() -> new NotFoundException(msg.notFound(id)));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PaymentTransactionEntity> list(
            final String tenantId,
            final PaymentTransactionFilter filter,
            final Pageable pageable) {
        log.debug("Loading payment transactions for tenantId={}, filter={}",
                tenantId, filter);

        final UUID paymentId = filter != null ? filter.paymentId() : null;
        final PaymentTransactionStatus status = filter != null ? filter.status() : null;
        final Page<PaymentTransactionEntity> result = repository.searchByTenantId(tenantId, paymentId, status, pageable);

        log.debug("Found {} payment transactions for tenantId={}, filter={}", result.getTotalElements(), tenantId, filter);

        return result;
    }

    @Override
    @Transactional
    public PaymentTransactionEntity create(final String tenantId, final PaymentTransactionEntity entity) {
        log.info("Creating payment transaction for tenantId={}, PaymentTransaction={}", tenantId, entity);

        if (entity == null) {
            throw new ValidationException(msg.required());
        }

        if (entity.getPayment() == null) {
            throw new ValidationException(msg.paymentRequired());
        }

        entity.setTenantId(tenantId);
        entity.setPaymentId(entity.getPayment().getId());

        final PaymentTransactionEntity saved = repository.save(entity);

        log.info("Payment transaction created for tenantId={}, PaymentTransaction={}", tenantId, saved);

        return saved;
    }

    @Override
    @Transactional
    public PaymentTransactionEntity update(
            final String tenantId,
            final UUID id,
            final Consumer<PaymentTransactionEntity> updater) {
        log.info("Updating payment transaction for tenantId={}, id={}", tenantId, id);

        return find(tenantId, id)
                .map((pt) -> {
                    updater.accept(pt);
                    log.debug("Update payment transaction: {}", pt);
                    return pt;
                })
                .orElseThrow(() -> new NotFoundException(msg.notFound(id)));
    }
}
