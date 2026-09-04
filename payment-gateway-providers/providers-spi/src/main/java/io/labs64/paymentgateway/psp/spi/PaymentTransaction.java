package io.labs64.paymentgateway.psp.spi;

import java.util.Map;
import java.util.UUID;

public record PaymentTransaction(
        UUID id,
        PaymentTransactionStatus status,
        Map<String, Object> pspData) {

    public PaymentTransaction(final UUID id, final PaymentTransactionStatus status) {
        this(id, status, Map.of());
    }

}
