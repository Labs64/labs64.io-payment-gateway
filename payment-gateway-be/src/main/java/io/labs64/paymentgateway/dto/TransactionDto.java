package io.labs64.paymentgateway.dto;

import java.time.Instant;
import java.util.Map;

public record TransactionDto(
		String id,
		String paymentId,
		String status,
		Map<String, Object> pspData,
		Instant createdAt) {
}
