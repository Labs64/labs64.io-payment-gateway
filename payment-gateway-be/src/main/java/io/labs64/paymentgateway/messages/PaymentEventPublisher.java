package io.labs64.paymentgateway.messages;

import io.labs64.paymentgateway.config.CorrelationIdFilter;
import io.labs64.paymentgateway.config.MessagingProperties;
import io.labs64.paymentgateway.entity.Payment;
import io.labs64.paymentgateway.entity.PaymentTransaction;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentEventPublisher {
	private static final Logger logger = LoggerFactory.getLogger(PaymentEventPublisher.class);

	private final RabbitTemplate rabbitTemplate;
	private final MessagingProperties properties;

	public void publishFinalized(Payment payment, PaymentTransaction transaction) {
		Map<String, Object> payload = new HashMap<>();
		payload.put("paymentId", payment.getId().toString());
		payload.put("transactionId", transaction.getId().toString());
		payload.put("tenantId", payment.getTenantId());
		payload.put("status", transaction.getStatus().name());
		payload.put("provider", payment.getProvider());

		MessageProperties messageProperties = new MessageProperties();
		String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
		if (correlationId != null) {
			messageProperties.setHeader(CorrelationIdFilter.HEADER_NAME, correlationId);
		}
		try {
			Message message = rabbitTemplate.getMessageConverter().toMessage(payload, messageProperties);
			rabbitTemplate.send(properties.getExchange(), properties.getRoutingKey(), message);
		} catch (Exception ex) {
			logger.warn("Failed to publish payment.finalized (RabbitMQ unavailable?): paymentId={} transactionId={}",
					payment.getId(), transaction.getId(), ex);
		}
	}
}
