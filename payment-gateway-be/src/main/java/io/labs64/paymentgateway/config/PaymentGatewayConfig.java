package io.labs64.paymentgateway.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class that enables {@link PaymentGatewayProperties} binding.
 */
@Configuration
@EnableConfigurationProperties(PaymentGatewayProperties.class)
public class PaymentGatewayConfig {
}
