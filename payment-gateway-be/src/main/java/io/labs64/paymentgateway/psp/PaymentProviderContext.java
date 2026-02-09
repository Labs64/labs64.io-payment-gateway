package io.labs64.paymentgateway.psp;

import io.labs64.paymentgateway.entity.Payment;
import java.util.Map;

public record PaymentProviderContext(
		Payment payment,
		Map<String, Object> tenantConfig,
		Map<String, Object> requestData) {
}
