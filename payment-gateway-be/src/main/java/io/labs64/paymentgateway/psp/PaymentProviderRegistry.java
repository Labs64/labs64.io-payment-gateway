package io.labs64.paymentgateway.psp;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import io.labs64.paymentgateway.exception.ValidationException;

/**
 * Registry for PSP providers using the Strategy Pattern.
 * Automatically discovers all {@link PaymentProvider} implementations
 * via Spring dependency injection.
 */
@Component
public class PaymentProviderRegistry {

    private static final Logger log = LoggerFactory.getLogger(PaymentProviderRegistry.class);

    private final Map<String, PaymentProvider> providers;

    public PaymentProviderRegistry(final List<PaymentProvider> providerList) {
        this.providers = providerList.stream()
                .collect(Collectors.toMap(PaymentProvider::getProviderId, Function.identity()));
        log.info("Registered PSP providers: {}", providers.keySet());
    }

    /**
     * Resolve a payment provider by its ID.
     *
     * @param providerId the provider identifier (e.g., "stripe", "paypal", "noop")
     * @return the matching {@link PaymentProvider}
     * @throws ValidationException if the provider is not found
     */
    public PaymentProvider getProvider(final String providerId) {
        final PaymentProvider provider = providers.get(providerId);
        if (provider == null) {
            throw new ValidationException("Unknown payment provider: " + providerId);
        }
        return provider;
    }

    /**
     * Check if a provider with the given ID exists.
     *
     * @param providerId the provider identifier
     * @return true if registered
     */
    public boolean hasProvider(final String providerId) {
        return providers.containsKey(providerId);
    }
}
