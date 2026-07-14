package io.labs64.paymentgateway.psp.spi;

import java.util.Map;
import java.util.UUID;

public record Payment(
        UUID id,
        PaymentType type,
        String description,
        Map<String, Object> recurrence,
        Map<String, Object> purchaseOrder,
        Map<String, Object> billingInfo,
        Map<String, Object> shippingInfo,
        Map<String, Object> extra) {
}
