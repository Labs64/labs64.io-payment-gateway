package io.labs64.paymentgateway.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({PaymentMethodProperties.class, MessagingProperties.class})
public class AppConfig {
}
