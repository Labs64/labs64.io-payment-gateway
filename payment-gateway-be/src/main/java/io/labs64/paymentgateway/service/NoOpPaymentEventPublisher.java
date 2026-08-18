package io.labs64.paymentgateway.service;

import io.labs64.paymentgateway.entity.PaymentEntity;
import io.labs64.paymentgateway.entity.PaymentTransactionEntity;
import io.labs64.paymentgateway.integration.auditflow.AuditFlowProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** Keeps payment workflows independent of the optional AuditFlow integration. */
@Service
@ConditionalOnProperty(
        prefix = AuditFlowProperties.PREFIX,
        name = "enabled",
        havingValue = "false",
        matchIfMissing = true)
public class NoOpPaymentEventPublisher implements PaymentEventPublisher {

    @Override
    public void publishCreated(final PaymentEntity payment) {
    }

    @Override
    public void publishFinalized(
            final PaymentEntity payment,
            final PaymentTransactionEntity transaction) {
    }

    @Override
    public void publishClosed(
            final PaymentEntity payment,
            final PaymentTransactionEntity transaction) {
    }
}
