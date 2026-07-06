package io.labs64.paymentgateway.service;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.labs64.paymentgateway.entity.PaymentEntity;
import io.labs64.paymentgateway.entity.CheckoutSessionEntity;
import io.labs64.paymentgateway.entity.PaymentTransactionEntity;
import io.labs64.paymentgateway.exception.NotFoundException;
import io.labs64.paymentgateway.exception.ValidationException;
import io.labs64.paymentgateway.message.CheckoutSessionMessages;
import io.labs64.paymentgateway.repository.CheckoutSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Default {@link CheckoutSessionService} implementation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CheckoutSessionServiceImpl implements CheckoutSessionService {

    private final CheckoutSessionRepository repository;
    private final CheckoutSessionMessages msg;

    @Override
    @Transactional(readOnly = true)
    public Optional<CheckoutSessionEntity> find(final String tenantId, final UUID id) {
        log.debug("Find checkout session for tenantId={}, id={}", tenantId, id);
        return repository.findByIdAndTenantId(id, tenantId);
    }

    @Override
    @Transactional(readOnly = true)
    public CheckoutSessionEntity get(final String tenantId, final UUID id) {
        log.debug("Get checkout session for tenantId={}, id={}", tenantId, id);
        return find(tenantId, id)
                .orElseThrow(() -> new NotFoundException(msg.notFound(id)));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CheckoutSessionEntity> findByPaymentTransactionId(
            final String tenantId,
            final UUID paymentTransactionId) {
        log.debug("Find checkout session for tenantId={}, paymentTransactionId={}", tenantId, paymentTransactionId);
        return repository.findByTenantIdAndPaymentTransactionId(tenantId, paymentTransactionId);
    }

    @Override
    @Transactional
    public CheckoutSessionEntity create(
            final PaymentTransactionEntity transaction,
            final Map<String, Object> payload) {
        return create(transaction, payload, null);
    }

    @Override
    @Transactional
    public CheckoutSessionEntity create(
            final PaymentTransactionEntity transaction,
            final Map<String, Object> payload,
            final OffsetDateTime expiresAt) {
        if (transaction == null) {
            throw new ValidationException(msg.transactionRequired());
        }

        final PaymentEntity payment = transaction.getPayment();
        if (payment == null) {
            throw new ValidationException(msg.paymentRequired());
        }

        final CheckoutSessionEntity entity = CheckoutSessionEntity.builder()
                .tenantId(transaction.getTenantId())
                .payload(payload)
                .expiresAt(expiresAt)
                .build();
        entity.setPayment(payment);
        entity.setPaymentTransaction(transaction);

        final CheckoutSessionEntity saved = repository.save(entity);

        log.debug("Created checkout session for tenantId={}, paymentTransactionId={}",
                saved.getTenantId(), saved.getPaymentTransactionId());
        return saved;
    }

    @Override
    @Transactional
    public CheckoutSessionEntity updateNextAction(
            final String tenantId,
            final UUID id,
            final Map<String, Object> nextAction) {
        final CheckoutSessionEntity session = get(tenantId, id);
        session.setNextAction(nextAction);
        log.debug("Updated checkout session next action for tenantId={}, id={}", tenantId, id);
        return session;
    }
}
