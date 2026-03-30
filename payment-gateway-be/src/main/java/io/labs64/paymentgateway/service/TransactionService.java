package io.labs64.paymentgateway.service;

import java.util.UUID;

import io.labs64.paymentgateway.v1.model.TransactionResponse;

/**
 * Service for retrieving transaction details.
 */
public interface TransactionService {

    /**
     * Retrieve transaction details by ID, scoped by tenant.
     *
     * @param tenantId      tenant identifier
     * @param transactionId transaction identifier
     * @return transaction details
     */
    TransactionResponse getTransaction(String tenantId, UUID transactionId);
}
