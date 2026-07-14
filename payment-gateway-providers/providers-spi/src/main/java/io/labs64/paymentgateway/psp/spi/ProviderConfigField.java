package io.labs64.paymentgateway.psp.spi;

/**
 * Declares one PSP configuration field accepted by a payment provider.
 *
 * @param name configuration key visible in tenant payment-provider config
 * @param required whether the gateway must reject missing or blank values
 */
public record ProviderConfigField(
        String name,
        boolean required) {

    /**
     * Creates a required provider configuration field declaration.
     *
     * @param name configuration key
     * @return required field declaration
     */
    public static ProviderConfigField required(final String name) {
        return new ProviderConfigField(name, true);
    }

    /**
     * Creates an optional provider configuration field declaration.
     *
     * @param name configuration key
     * @return optional field declaration
     */
    public static ProviderConfigField optional(final String name) {
        return new ProviderConfigField(name, false);
    }
}
