package io.labs64.paymentgateway.psp.spi;

import java.util.Map;
import java.util.Set;

/**
 * Optional capability for payment providers that accept tenant PSP configuration.
 * <p>
 * The payment gateway owns filtering and persistence. Implementations receive
 * only declared configuration fields and validate provider-specific rules.
 * Providers that do not implement this interface cannot be configured by a
 * tenant; attempts to save non-empty config for such providers are rejected by
 * the gateway.
 */
public interface ProviderConfigSupport {

    /**
     * Declares the exact tenant configuration keys accepted by this provider.
     * <p>
     * The gateway uses this declaration to drop unknown keys and to check
     * required fields before invoking {@link #validateConfig(Map)}.
     *
     * @return supported configuration field declarations
     */
    Set<ProviderConfigField> configFields();

    /**
     * Validates sanitized tenant configuration.
     * <p>
     * The provided map contains only keys declared by {@link #configFields()}.
     * Required field presence is already checked by the gateway; this method
     * should enforce provider-specific rules such as enum values, URL formats,
     * key compatibility, or cross-field constraints.
     *
     * @param config sanitized tenant configuration
     * @throws io.labs64.paymentgateway.exception.ValidationException when config is invalid
     */
    void validateConfig(Map<String, String> config);
}
