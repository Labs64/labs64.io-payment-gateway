package io.labs64.paymentgateway.mapper;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * Masks sensitive configuration values before returning config through API.
 */
@Component
public class ConfigMaskingMapper {

    private static final String[] SENSITIVE_KEYS = {
            "apiKey", "api_key", "secret", "webhookSecret", "webhook_secret", "password", "token"
    };

    /**
     * Shows non-sensitive config values as-is and masks sensitive ones.
     */
    public Map<String, String> maskSensitiveValues(Map<String, String> config) {
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
