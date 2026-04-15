package io.labs64.paymentgateway.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * Configuration properties for the Payment Gateway module.
 * Binds to the {@code payment-gateway} prefix in application YAML.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "payment-gateway")
public class PaymentGatewayProperties {

    private List<PaymentMethodConfig> paymentMethods = List.of();
    private RetryConfig retry = new RetryConfig();
    private RedisConfig redis = new RedisConfig();

    @Getter
    @Setter
    public static class PaymentMethodConfig {
        private String id;
        private boolean enabled;
        private String name;
        private String description;
        private boolean recurring;
        private List<String> supportedCurrencies = List.of();
        private List<String> supportedCountries = List.of();
    }

    @Getter
    @Setter
    public static class RetryConfig {
        private int maxRetries = 3;
        private int retryIntervalSeconds = 60;
        private double backoffMultiplier = 2.0;
    }

    @Getter
    @Setter
    public static class RedisConfig {
        private int idempotencyKeyTtlSeconds = 86400;
        private int distributedLockTtlSeconds = 30;
    }
}
