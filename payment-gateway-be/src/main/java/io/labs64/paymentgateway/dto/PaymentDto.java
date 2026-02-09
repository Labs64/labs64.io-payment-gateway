package io.labs64.paymentgateway.dto;

import java.time.Instant;
import java.util.Map;

public record PaymentDto(
		String id,
		String status,
		String paymentMethodId,
		String tenantId,
		boolean recurring,
		Map<String, Object> purchaseOrder,
		Map<String, Object> billingInfo,
		Map<String, Object> shippingInfo,
		Map<String, Object> extra,
		Instant createdAt) {
}
