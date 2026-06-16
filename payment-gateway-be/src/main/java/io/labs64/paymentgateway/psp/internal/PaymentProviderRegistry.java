package io.labs64.paymentgateway.psp.internal;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.labs64.paymentgateway.psp.spi.PaymentProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import io.labs64.paymentgateway.exception.ValidationException;

/**
 * Registry for PSP providers using the Strategy Pattern.
 * Automatically discovers all {@link PaymentProvider} implementations
 * via Spring dependency injection.
 */
@Slf4j
@Component
public class PaymentProviderRegistry {
    private final Map<String, PaymentProvider> providers;

    public PaymentProviderRegistry(final List<PaymentProvider> providers) {
        this.providers = providers.stream()
                .collect(Collectors.toMap(PaymentProvider::provider, Function.identity()));
        log.info("Registered PSP providers: {}", this.providers.keySet());
    }

    /**
     * Resolve a payment provider by its ID.
     *
     * @param provider the provider identifier (e.g., "stripe", "paypal", "noop")
     * @return the matching {@link PaymentProvider}
     * @throws ValidationException if the provider is not found
     */
    public PaymentProvider getProvider(final String provider) {
        final PaymentProvider paymentProvider = providers.get(provider);
        if (paymentProvider == null) {
            throw new ValidationException("Unknown payment provider: " + provider);
        }
        return paymentProvider;
    }

    /**
     * Check if a provider with the given ID exists.
     *
     * @param provider the provider identifier
     * @return true if registered
     */
    public boolean hasProvider(final String provider) {
        return providers.containsKey(provider);
    }
}
