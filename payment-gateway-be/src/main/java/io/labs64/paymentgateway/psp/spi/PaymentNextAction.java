package io.labs64.paymentgateway.psp.spi;

import io.labs64.paymentgateway.model.NextAction;

import java.util.Map;

public record PaymentNextAction(
        NextAction.TypeEnum type,
        Map<String, Object> details) {
}
