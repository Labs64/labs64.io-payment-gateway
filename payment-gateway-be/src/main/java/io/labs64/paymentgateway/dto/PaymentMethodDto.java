package io.labs64.paymentgateway.dto;

import java.util.List;

public record PaymentMethodDto(
		String id,
		String name,
		String description,
		String icon,
		boolean recurring,
		boolean configured,
		String provider,
		List<String> currencies,
		List<String> countries) {
}
