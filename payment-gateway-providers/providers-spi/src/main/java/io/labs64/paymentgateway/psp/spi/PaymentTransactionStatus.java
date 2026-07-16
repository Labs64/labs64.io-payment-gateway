package io.labs64.paymentgateway.psp.spi;

/**
 * Provider-facing status of a payment transaction attempt.
 */
public enum PaymentTransactionStatus {
    PENDING,
    SUCCESS,
    FAILED
}
