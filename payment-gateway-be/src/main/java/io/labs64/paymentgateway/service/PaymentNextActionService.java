package io.labs64.paymentgateway.service;

import java.util.UUID;

import io.labs64.paymentgateway.entity.PaymentNextActionEntity;
import io.labs64.paymentgateway.entity.PaymentTransactionEntity;
import io.labs64.paymentgateway.psp.spi.PaymentNextAction;

/**
 * Service for managing next actions tied to payment transaction attempts.
 */
public interface PaymentNextActionService {

    /**
     * Creates a next action for a payment transaction.
     *
     * @param transaction payment transaction requiring a next action
     * @param nextAction PSP next action payload
     * @return persisted next action
     */
    PaymentNextActionEntity create(PaymentTransactionEntity transaction, PaymentNextAction nextAction);

    /**
     * Deletes the next action linked to a payment transaction when it exists.
     *
     * @param transactionId payment transaction identifier
     */
    void deleteByTransactionId(UUID transactionId);
}
