package io.labs64.paymentgateway.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

	@Bean
	public DirectExchange paymentExchange(MessagingProperties properties) {
		return new DirectExchange(properties.getExchange());
	}

	@Bean
	public Queue paymentQueue(MessagingProperties properties) {
		return new Queue(properties.getQueue(), true);
	}

	@Bean
	public Binding paymentBinding(DirectExchange paymentExchange, Queue paymentQueue,
			MessagingProperties properties) {
		return BindingBuilder.bind(paymentQueue).to(paymentExchange).with(properties.getRoutingKey());
	}
}
