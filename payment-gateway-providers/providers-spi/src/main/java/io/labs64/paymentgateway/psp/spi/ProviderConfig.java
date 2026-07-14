package io.labs64.paymentgateway.psp.spi;

import java.util.Map;

public record ProviderConfig(
        String provider,
        Map<String, String> config,
        String name,
        String description) {
}
