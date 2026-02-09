package io.labs64.paymentgateway.service;

import io.labs64.paymentgateway.entity.Payment;
import io.labs64.paymentgateway.entity.PaymentStatus;
import io.labs64.paymentgateway.entity.PaymentTransaction;
import io.labs64.paymentgateway.entity.TransactionStatus;
import io.labs64.paymentgateway.messages.PaymentEventPublisher;
import io.labs64.paymentgateway.repository.PaymentRepository;
import io.labs64.paymentgateway.repository.PaymentTransactionRepository;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebhookServiceTest {
	private PaymentRepository paymentRepository;
	private PaymentTransactionRepository transactionRepository;
	private PaymentEventPublisher eventPublisher;
	private WebhookService webhookService;

	@BeforeEach
	void setup() {
		paymentRepository = Mockito.mock(PaymentRepository.class);
		transactionRepository = Mockito.mock(PaymentTransactionRepository.class);
		eventPublisher = Mockito.mock(PaymentEventPublisher.class);
		webhookService = new WebhookService(paymentRepository, transactionRepository, eventPublisher,
				new JsonService(new com.fasterxml.jackson.databind.ObjectMapper()));
	}

	@Test
	void stripeWebhookMarksSuccessAndPublishes() {
		Payment payment = new Payment();
		payment.setId(UUID.randomUUID());
		payment.setTenantId("tenant-1");
		payment.setProvider("stripe");
		payment.setRecurring(false);
		payment.setStatus(PaymentStatus.ACTIVE);
		payment.setCreatedAt(Instant.now());
		payment.setUpdatedAt(Instant.now());

		PaymentTransaction transaction = new PaymentTransaction();
		transaction.setId(UUID.randomUUID());
		transaction.setPaymentId(payment.getId());
		transaction.setStatus(TransactionStatus.PENDING);
		transaction.setCreatedAt(Instant.now());
		transaction.setUpdatedAt(Instant.now());

		when(paymentRepository.findByProviderAndPspReference("stripe", "pi_123"))
				.thenReturn(Optional.of(payment));
		when(transactionRepository.findTopByPaymentIdOrderByCreatedAtDesc(payment.getId()))
				.thenReturn(Optional.of(transaction));

		webhookService.handleStripeWebhook("payment_intent.succeeded",
				Map.of("object", Map.of("id", "pi_123", "status", "succeeded")));

		Assertions.assertEquals(PaymentStatus.CLOSED, payment.getStatus());
		verify(eventPublisher).publishFinalized(payment, transaction);
	}
}
