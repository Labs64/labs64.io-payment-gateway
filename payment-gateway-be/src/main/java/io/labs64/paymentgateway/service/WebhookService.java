package io.labs64.paymentgateway.service;

import io.labs64.paymentgateway.entity.Payment;
import io.labs64.paymentgateway.entity.PaymentStatus;
import io.labs64.paymentgateway.entity.PaymentTransaction;
import io.labs64.paymentgateway.entity.TransactionStatus;
import io.labs64.paymentgateway.exception.NotFoundException;
import io.labs64.paymentgateway.messages.PaymentEventPublisher;
import io.labs64.paymentgateway.repository.PaymentRepository;
import io.labs64.paymentgateway.repository.PaymentTransactionRepository;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WebhookService {
	private final PaymentRepository paymentRepository;
	private final PaymentTransactionRepository transactionRepository;
	private final PaymentEventPublisher eventPublisher;
	private final JsonService jsonService;

	@Transactional
	public void handleStripeWebhook(String eventType, Map<String, Object> data) {
		Map<String, Object> object = getNestedMap(data, "object");
		String paymentIntentId = stringValue(object.get("id"));
		String status = stringValue(object.get("status"));
		if (paymentIntentId.isBlank()) {
			throw new IllegalArgumentException("Stripe paymentIntentId missing");
		}
		updateByReference("stripe", paymentIntentId, mapStripeStatus(status), object);
	}

	@Transactional
	public void handlePayPalWebhook(String eventType, Map<String, Object> resource) {
		String orderId = stringValue(resource.get("id"));
		String status = stringValue(resource.get("status"));
		if (orderId.isBlank()) {
			throw new IllegalArgumentException("PayPal orderId missing");
		}
		updateByReference("paypal", orderId, mapPayPalStatus(status), resource);
	}

	private void updateByReference(String provider, String reference, TransactionStatus status,
			Map<String, Object> pspData) {
		Payment payment = paymentRepository.findByProviderAndPspReference(provider, reference)
				.orElseThrow(() -> new NotFoundException("Payment not found for reference: " + reference));
		PaymentTransaction transaction = transactionRepository.findTopByPaymentIdOrderByCreatedAtDesc(payment.getId())
				.orElseThrow(() -> new NotFoundException("Transaction not found for payment: " + payment.getId()));

		transaction.setStatus(status);
		transaction.setPspData(jsonService.toJson(pspData));
		transaction.setPspReference(reference);
		transactionRepository.save(transaction);

		if (status == TransactionStatus.SUCCESS) {
			payment.setStatus(payment.isRecurring() ? PaymentStatus.ACTIVE : PaymentStatus.CLOSED);
			paymentRepository.save(payment);
		}

		if (status == TransactionStatus.SUCCESS || status == TransactionStatus.FAILED) {
			eventPublisher.publishFinalized(payment, transaction);
		}
	}

	private TransactionStatus mapStripeStatus(String status) {
		return switch (status) {
			case "succeeded" -> TransactionStatus.SUCCESS;
			case "requires_action", "processing" -> TransactionStatus.PENDING;
			default -> TransactionStatus.FAILED;
		};
	}

	private TransactionStatus mapPayPalStatus(String status) {
		if ("COMPLETED".equalsIgnoreCase(status)) {
			return TransactionStatus.SUCCESS;
		}
		if ("PAYER_ACTION_REQUIRED".equalsIgnoreCase(status)) {
			return TransactionStatus.PENDING;
		}
		return TransactionStatus.FAILED;
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> getNestedMap(Map<String, Object> source, String key) {
		if (source == null) {
			return Map.of();
		}
		Object value = source.get(key);
		if (value instanceof Map<?, ?> map) {
			return (Map<String, Object>) map;
		}
		return Map.of();
	}

	private String stringValue(Object value) {
		return value == null ? "" : value.toString();
	}
}
