package io.labs64.paymentgateway.service;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import io.labs64.paymentgateway.entity.CheckoutSessionEntity;
import io.labs64.paymentgateway.entity.PaymentTransactionEntity;

/**
 * Service for managing user-facing checkout sessions.
 */
public interface CheckoutSessionService {

    /**
     * Finds a checkout session by tenant and session id.
     *
     * @param tenantId tenant identifier
     * @param id checkout session identifier
     * @return checkout session when it exists for the tenant
     */
    Optional<CheckoutSessionEntity> find(String tenantId, UUID id);

    /**
     * Gets a checkout session by tenant and session id.
     *
     * @param tenantId tenant identifier
     * @param id checkout session identifier
     * @return checkout session
     * @throws io.labs64.paymentgateway.exception.NotFoundException when session does not exist for the tenant
     */
    CheckoutSessionEntity get(String tenantId, UUID id);

    /**
     * Finds a checkout session linked to a payment transaction.
     *
     * @param tenantId tenant identifier
     * @param paymentTransactionId payment transaction identifier
     * @return checkout session when it exists for the tenant transaction
     */
    Optional<CheckoutSessionEntity> findByPaymentTransactionId(String tenantId, UUID paymentTransactionId);

    /**
     * Creates a checkout session for a transaction attempt.
     *
     * @param transaction transaction linked to the user-facing session
     * @param payload provider-owned session payload
     * @return persisted checkout session
     */
    CheckoutSessionEntity create(PaymentTransactionEntity transaction, Map<String, Object> payload);

    /**
     * Creates a checkout session for a transaction attempt.
     *
     * @param transaction transaction linked to the user-facing session
     * @param payload provider-owned session payload
     * @param expiresAt optional expiration timestamp
     * @return persisted checkout session
     */
    CheckoutSessionEntity create(PaymentTransactionEntity transaction, Map<String, Object> payload, OffsetDateTime expiresAt);

    /**
     * Updates stored next action for a checkout session.
     *
     * @param tenantId tenant identifier
     * @param id checkout session identifier
     * @param nextAction next action payload
     * @return updated checkout session
     */
    CheckoutSessionEntity updateNextAction(String tenantId, UUID id, Map<String, Object> nextAction);
}
