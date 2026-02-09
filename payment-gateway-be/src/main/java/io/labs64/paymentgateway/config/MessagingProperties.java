package io.labs64.paymentgateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "messaging.payment")
public class MessagingProperties {
	private String exchange;
	private String routingKey;
	private String queue;
}
