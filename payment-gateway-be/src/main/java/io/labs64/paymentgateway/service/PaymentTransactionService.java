package io.labs64.paymentgateway.service;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import io.labs64.paymentgateway.entity.PaymentTransactionEntity;
import io.labs64.paymentgateway.service.filter.PaymentTransactionFilter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Service for managing payment transaction attempts.
 */
public interface PaymentTransactionService {

    /**
     * Finds a payment transaction by tenant and transaction id.
     *
     * @param tenantId tenant identifier
     * @param id payment transaction identifier
     * @return payment transaction when it exists for the tenant
     */
    Optional<PaymentTransactionEntity> find(String tenantId, UUID id);

    /**
     * Gets a payment transaction by tenant and transaction id.
     *
     * @param tenantId tenant identifier
     * @param id payment transaction identifier
     * @return payment transaction
     * @throws io.labs64.paymentgateway.exception.NotFoundException when transaction does not exist for the tenant
     */
    PaymentTransactionEntity get(String tenantId, UUID id);

    /**
     * Lists payment transactions for a tenant using optional filters.
     *
     * @param tenantId tenant identifier
     * @param filter optional payment transaction filters
     * @param pageable page request
     * @return page of payment transactions
     */
    Page<PaymentTransactionEntity> list(
            String tenantId,
            PaymentTransactionFilter filter,
            Pageable pageable);

    /**
     * Creates a payment transaction for a tenant.
     *
     * @param tenantId tenant identifier
     * @param entity payment transaction entity to persist
     * @return persisted payment transaction
     * @throws io.labs64.paymentgateway.exception.ValidationException when required transaction data is missing
     */
    PaymentTransactionEntity create(String tenantId, PaymentTransactionEntity entity);

    /**
     * Updates an existing payment transaction through the provided mutator.
     *
     * @param tenantId tenant identifier
     * @param id payment transaction identifier
     * @param updater mutation callback applied to the managed entity
     * @return updated payment transaction
     * @throws io.labs64.paymentgateway.exception.NotFoundException when transaction does not exist for the tenant
     */
    PaymentTransactionEntity update(String tenantId, UUID id, Consumer<PaymentTransactionEntity> updater);
}
