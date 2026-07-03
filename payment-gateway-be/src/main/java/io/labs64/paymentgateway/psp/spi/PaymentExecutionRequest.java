package io.labs64.paymentgateway.psp.spi;

import java.util.Map;

/**
 * Provider-facing snapshot of request data supplied for a payment execution attempt.
 */
public record PaymentExecutionRequest(
        Map<String, Object> checkout) {

    private static final PaymentExecutionRequest EMPTY = new PaymentExecutionRequest(Map.of());

    public static PaymentExecutionRequest empty() {
        return EMPTY;
    }
}
