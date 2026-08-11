package io.labs64.paymentgateway.psp.spi;

import java.util.Map;
import java.util.UUID;

import io.labs64.paymentgateway.model.BillingInfo;
import io.labs64.paymentgateway.model.PurchaseOrder;
import io.labs64.paymentgateway.model.Recurrence;
import io.labs64.paymentgateway.model.ShippingInfo;

public record Payment(
        UUID id,
        PaymentType type,
        String description,
        Recurrence recurrence,
        PurchaseOrder purchaseOrder,
        BillingInfo billingInfo,
        ShippingInfo shippingInfo,
        Map<String, Object> extra) {
}
