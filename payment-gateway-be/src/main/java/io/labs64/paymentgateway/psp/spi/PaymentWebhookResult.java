package io.labs64.paymentgateway.psp.spi;

import io.labs64.paymentgateway.model.PaymentTransactionStatus;

import java.util.Map;


public record PaymentWebhookResult(
        String provider,
        PaymentTransactionStatus status,
        Map<String, Object> pspData,
        StatusDetails statusDetails) {
}
