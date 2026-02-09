package io.labs64.paymentgateway.dto;

import java.util.Map;

public record ConfigurePspRequest(Map<String, Object> pspConfig) {
}
