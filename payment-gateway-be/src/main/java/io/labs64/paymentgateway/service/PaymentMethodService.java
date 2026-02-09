package io.labs64.paymentgateway.service;

import io.labs64.paymentgateway.config.PaymentMethodProperties;
import io.labs64.paymentgateway.dto.PaymentMethodDto;
import io.labs64.paymentgateway.exception.NotFoundException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentMethodService {
	private final PaymentMethodProperties paymentMethodProperties;
	private final TenantPspConfigService tenantPspConfigService;

	public List<PaymentMethodDto> getPaymentMethods(String tenantId, Optional<String> currency,
			Optional<String> country) {
		Predicate<PaymentMethodProperties.PaymentMethodConfig> currencyFilter = method ->
				currency.map(value -> method.getCurrencies().stream()
								.anyMatch(item -> item.equalsIgnoreCase(value)))
						.orElse(true);
		Predicate<PaymentMethodProperties.PaymentMethodConfig> countryFilter = method ->
				country.map(value -> method.getCountries().stream()
								.anyMatch(item -> item.equalsIgnoreCase(value)))
						.orElse(true);
		return paymentMethodProperties.getMethods().stream()
				.filter(currencyFilter.and(countryFilter))
				.map(method -> new PaymentMethodDto(
						method.getId(),
						method.getName(),
						method.getDescription(),
						method.getIcon(),
						Boolean.TRUE.equals(method.getRecurring()),
						tenantPspConfigService.hasConfig(tenantId, method.getProvider()),
						method.getProvider().toLowerCase(Locale.ROOT),
						method.getCurrencies(),
						method.getCountries()))
				.collect(Collectors.toList());
	}

	public PaymentMethodProperties.PaymentMethodConfig getMethodById(String methodId) {
		return paymentMethodProperties.getMethods().stream()
				.filter(method -> method.getId().equalsIgnoreCase(methodId))
				.findFirst()
				.orElseThrow(() -> new NotFoundException("Payment method not found: " + methodId));
	}
}
