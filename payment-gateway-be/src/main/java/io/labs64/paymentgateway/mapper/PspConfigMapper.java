package io.labs64.paymentgateway.mapper;

import java.util.LinkedHashMap;
import java.util.Map;

import org.mapstruct.Mapper;

import io.labs64.paymentgateway.entity.PspConfigEntity;
import io.labs64.paymentgateway.v1.model.PspConfigResponse;

/**
 * MapStruct mapper for converting between {@link PspConfigEntity} and API DTOs.
 * Sensitive config values are masked in the response.
 */
@Mapper(componentModel = "spring")
public interface PspConfigMapper {

    /**
     * Sensitive key patterns that should be masked in API responses.
     */
    String[] SENSITIVE_KEYS = {"apiKey", "api_key", "secret", "webhookSecret", "webhook_secret", "password", "token"};

    default PspConfigResponse toResponse(PspConfigEntity entity) {
        if (entity == null) {
            return null;
        }
        final PspConfigResponse response = new PspConfigResponse();
        response.setPaymentMethodId(entity.getPaymentMethodId());
        response.setTenantId(entity.getTenantId());
        response.setEnabled(entity.isEnabled());
        response.setConfig(maskSensitiveValues(entity.getConfig()));
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }

    /**
     * Masks sensitive configuration values. Shows the first few characters
     * then replaces the rest with asterisks.
     */
    default Map<String, String> maskSensitiveValues(Map<String, String> config) {
        if (config == null) {
            return null;
        }
        final Map<String, String> masked = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : config.entrySet()) {
            if (isSensitiveKey(entry.getKey())) {
                masked.put(entry.getKey(), maskValue(entry.getValue()));
            } else {
                masked.put(entry.getKey(), entry.getValue());
            }
        }
        return masked;
    }

    private static boolean isSensitiveKey(String key) {
        final String lower = key.toLowerCase();
        for (String sensitiveKey : SENSITIVE_KEYS) {
            if (lower.contains(sensitiveKey.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private static String maskValue(String value) {
        if (value == null || value.length() <= 8) {
            return "****";
        }
        return value.substring(0, Math.min(value.length(), 8)) + "****";
    }
}
