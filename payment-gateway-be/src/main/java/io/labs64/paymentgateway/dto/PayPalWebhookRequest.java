package io.labs64.paymentgateway.dto;

import java.util.Map;

public record PayPalWebhookRequest(String eventType, Map<String, Object> resource) {
}
