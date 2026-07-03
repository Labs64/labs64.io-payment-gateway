package io.labs64.paymentgateway.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for the Payment Gateway module.
 * Binds to the {@code payment-gateway} prefix in application YAML.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "payment-gateway")
public class PaymentGatewayProperties {

    private String publicBaseUrl = "http://localhost:8080/api/v1";
    private List<PaymentDefinition> paymentDefinitions = List.of();
    private RetryConfig retry = new RetryConfig();
    private RedisConfig redis = new RedisConfig();
    private IdempotencyConfig idempotency = new IdempotencyConfig();

    @Getter
    @Setter
    public static class PaymentDefinition {
        private String provider;
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
        private int distributedLockTtlSeconds = 30;
    }

    @Getter
    @Setter
    public static class IdempotencyConfig {
        private Duration redisTtl = Duration.ofHours(3);
        private Duration databaseTtl = Duration.ofDays(1);
        private Duration processingTimeout = Duration.ofMinutes(5);
        private Duration cleanupInterval = Duration.ofHours(1);
        private int cleanupBatchSize = 1000;
    }
}
