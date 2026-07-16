package io.labs64.paymentgateway.psp.spi;

import java.util.Map;


public record PaymentWebhookResult(
        String provider,
        PaymentTransactionStatus status,
        Map<String, Object> pspData,
        StatusDetails statusDetails) {
}
