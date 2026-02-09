package io.labs64.paymentgateway.dto;

import java.time.Instant;

public record ApiError(
		Instant timestamp,
		int status,
		String error,
		String message,
		String correlationId) {
}
