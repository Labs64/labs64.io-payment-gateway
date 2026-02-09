package io.labs64.paymentgateway.dto;

import java.util.Map;

public record NextActionDto(String type, Map<String, Object> details) {
}
