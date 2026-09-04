package io.labs64.paymentgateway.service;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import io.labs64.paymentgateway.entity.PaymentTransactionEntity;
import io.labs64.paymentgateway.psp.spi.ProviderExecutionFailure;
import io.labs64.paymentgateway.psp.spi.ProviderResult;
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

    /**
     * Updates a non-terminal payment transaction under a database write lock.
     * The updater is not invoked when the transaction already has a terminal status.
     *
     * @param tenantId tenant identifier
     * @param id payment transaction identifier
     * @param updater mutation callback applied only to a non-terminal managed entity
     * @return current payment transaction, updated or left unchanged when terminal
     * @throws io.labs64.paymentgateway.exception.NotFoundException when transaction does not exist for the tenant
     */
    PaymentTransactionEntity updateIfNonTerminal(
            String tenantId,
            UUID id,
            Consumer<PaymentTransactionEntity> updater);

    /**
     * Records why a provider invocation did not produce a definitive result.
     * The transaction remains non-terminal and terminal transactions are left
     * unchanged.
     *
     * @param transaction payment transaction used to identify the locked entity
     * @param failure normalized provider execution failure
     * @return current payment transaction with localized status details when it
     *         was non-terminal
     */
    PaymentTransactionEntity recordProviderExecutionFailure(
            PaymentTransactionEntity transaction,
            ProviderExecutionFailure failure);

    /**
     * Applies a normalized provider result to a payment transaction under a
     * database write lock. Terminal transactions are left unchanged.
     *
     * @param transaction payment transaction used to identify the locked entity
     * @param result normalized provider result
     * @return current payment transaction after applying or ignoring the result
     */
    PaymentTransactionEntity applyResult(
            PaymentTransactionEntity transaction,
            ProviderResult result);
}
