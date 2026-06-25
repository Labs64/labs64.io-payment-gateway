package io.labs64.paymentgateway.psp.spi;

import java.util.List;
import java.util.Map;

/**
 * PSP webhook request data visible to provider implementations.
 * Raw body may be empty when the request was produced by a parsed OpenAPI controller contract.
 */
public record WebhookRequest(
        String provider,
        byte[] rawBody,
        Map<String, Object> payload,
        Map<String, List<String>> headers,
        Map<String, List<String>> queryParams) {
}
