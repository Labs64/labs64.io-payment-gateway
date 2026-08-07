package io.labs64.paymentgateway.psp.spi;

import java.util.List;
import java.util.Map;

/**
 * Transport-level PSP webhook request data visible to provider implementations.
 * The provider owns signature verification and parsing of {@code body}.
 */
public record WebhookRequest(
        String provider,
        String body,
        Map<String, List<String>> headers,
        Map<String, List<String>> queryParams) {
}
