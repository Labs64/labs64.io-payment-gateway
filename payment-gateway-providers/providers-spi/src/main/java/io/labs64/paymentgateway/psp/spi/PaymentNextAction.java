package io.labs64.paymentgateway.psp.spi;

import java.util.Map;

public record PaymentNextAction(
        PaymentNextActionType type,
        Map<String, Object> details) {
}
