package io.labs64.paymentgateway.service;

import io.labs64.paymentgateway.entity.PaymentEntity;
import io.labs64.paymentgateway.entity.PaymentTransactionEntity;

/**
 * Publishes payment lifecycle events to downstream consumers.
 */
public interface PaymentEventPublisher {

    void publishCreated(PaymentEntity payment);

    void publishFinalized(PaymentEntity payment, PaymentTransactionEntity transaction);

    void publishClosed(PaymentEntity payment, PaymentTransactionEntity transaction);
}
