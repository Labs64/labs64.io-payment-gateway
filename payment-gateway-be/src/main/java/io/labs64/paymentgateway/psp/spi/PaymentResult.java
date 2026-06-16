package io.labs64.paymentgateway.psp.spi;

import java.util.Map;

import io.labs64.paymentgateway.model.PaymentTransactionStatus;

public record PaymentResult(
        String provider,
        PaymentTransactionStatus status,
        Map<String, Object> pspData,
        StatusDetails statusDetails,
        PaymentNextAction nextAction) {
}
