package io.labs64.paymentgateway.domain;

import io.labs64.paymentgateway.model.PaymentTransactionStatus;

/**
 * Shared rules for payment transaction statuses.
 */
public final class PaymentTransactionStatuses {

    private PaymentTransactionStatuses() {
    }

    public static boolean isTerminal(final PaymentTransactionStatus status) {
        return PaymentTransactionStatus.SUCCESS.equals(status) || PaymentTransactionStatus.FAILED.equals(status);
    }
}
