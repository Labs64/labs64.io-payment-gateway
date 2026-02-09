package io.labs64.paymentgateway.dto;

import java.util.Map;

public record StripeWebhookRequest(String type, Map<String, Object> data) {
}
