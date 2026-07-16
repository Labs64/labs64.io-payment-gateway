package io.labs64.paymentgateway.psp.spi;

/**
 * Action required from a caller to continue a payment attempt.
 */
public enum PaymentNextActionType {
    REDIRECT,
    THREE_DS_CHALLENGE
}
