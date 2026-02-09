package io.labs64.paymentgateway.dto;

import java.util.Map;

public record PaymentCreateRequest(
		String paymentMethodId,
		Map<String, Object> purchaseOrder,
		boolean recurring,
		Map<String, Object> billingInfo,
		Map<String, Object> shippingInfo,
		Map<String, Object> extra) {
}
