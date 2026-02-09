package io.labs64.paymentgateway.psp;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class PaymentProviderRegistry {
	private final Map<String, PaymentProvider> providers;

	public PaymentProviderRegistry(List<PaymentProvider> providers) {
		this.providers = providers.stream()
				.collect(Collectors.toUnmodifiableMap(
						provider -> provider.getProviderKey().toLowerCase(),
						Function.identity()));
	}

	public PaymentProvider getProvider(String providerKey) {
		PaymentProvider provider = providers.get(providerKey.toLowerCase());
		if (provider == null) {
			throw new IllegalArgumentException("Unsupported PSP provider: " + providerKey);
		}
		return provider;
	}
}
