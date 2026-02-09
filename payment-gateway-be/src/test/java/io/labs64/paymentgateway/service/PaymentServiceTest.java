package io.labs64.paymentgateway.service;

import io.labs64.paymentgateway.config.PaymentMethodProperties;
import io.labs64.paymentgateway.entity.IdempotencyKey;
import io.labs64.paymentgateway.entity.Payment;
import io.labs64.paymentgateway.entity.PaymentStatus;
import io.labs64.paymentgateway.entity.PaymentTransaction;
import io.labs64.paymentgateway.entity.TransactionStatus;
import io.labs64.paymentgateway.dto.NextActionDto;
import io.labs64.paymentgateway.dto.PaymentCreateRequest;
import io.labs64.paymentgateway.mapper.PaymentMapper;
import io.labs64.paymentgateway.messages.PaymentEventPublisher;
import io.labs64.paymentgateway.psp.PaymentProvider;
import io.labs64.paymentgateway.psp.PaymentProviderContext;
import io.labs64.paymentgateway.psp.PaymentProviderRegistry;
import io.labs64.paymentgateway.psp.PaymentProviderResult;
import io.labs64.paymentgateway.repository.IdempotencyKeyRepository;
import io.labs64.paymentgateway.repository.PaymentRepository;
import io.labs64.paymentgateway.repository.PaymentTransactionRepository;
import io.labs64.paymentgateway.security.TenantResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {
	@Mock
	private PaymentRepository paymentRepository;
	@Mock
	private PaymentTransactionRepository transactionRepository;
	@Mock
	private IdempotencyKeyRepository idempotencyKeyRepository;
	@Mock
	private PaymentMethodService paymentMethodService;
	@Mock
	private PaymentProviderRegistry providerRegistry;
	@Mock
	private TenantPspConfigService tenantPspConfigService;
	@Mock
	private PaymentEventPublisher eventPublisher;
	@Mock
	private TenantResolver tenantResolver;
	@Mock
	private PaymentProvider provider;

	private PaymentMapper paymentMapper;
	private JsonService jsonService;

	@InjectMocks
	private PaymentService paymentService;

	@Captor
	private ArgumentCaptor<PaymentProviderContext> contextCaptor;

	@BeforeEach
	void setup() {
		jsonService = new JsonService(new ObjectMapper());
		paymentMapper = new PaymentMapper(jsonService);
		paymentService = new PaymentService(paymentRepository, transactionRepository, idempotencyKeyRepository,
				paymentMethodService, providerRegistry, tenantPspConfigService, eventPublisher, paymentMapper,
				jsonService, tenantResolver);
	}

	@Test
	void createPaymentStoresNextActionAndReturnsResponse() {
		PaymentMethodProperties.PaymentMethodConfig method = new PaymentMethodProperties.PaymentMethodConfig();
		method.setId("card");
		method.setProvider("noop");
		method.setName("Card");
		method.setDescription("Card");
		method.setRecurring(true);

		when(paymentMethodService.getMethodById("card")).thenReturn(method);
		when(tenantResolver.resolveTenantId()).thenReturn("tenant-1");
		when(tenantPspConfigService.getConfigOrEmpty("tenant-1", "noop")).thenReturn(Map.of());
		when(providerRegistry.getProvider("noop")).thenReturn(provider);
		when(provider.initiate(any()))
				.thenReturn(new PaymentProviderResult(null, new NextActionDto("none", Map.of()),
						Map.of("noop", true, "reference", "ref-1")));
		when(paymentRepository.save(any())).thenAnswer(persistPayment());

		PaymentCreateRequest request = new PaymentCreateRequest("card", Map.of("amount", 1000, "currency", "USD"),
				false, Map.of(), Map.of(), Map.of());
		var response = paymentService.createPayment(request);

		Assertions.assertNotNull(response.payment());
		Assertions.assertEquals("ACTIVE", response.payment().status());
		verify(provider).initiate(contextCaptor.capture());
		Assertions.assertEquals("tenant-1", contextCaptor.getValue().payment().getTenantId());
	}

	@Test
	void executePaymentUsesIdempotencyRecord() {
		UUID paymentId = UUID.randomUUID();
		UUID transactionId = UUID.randomUUID();

		Payment payment = new Payment();
		payment.setId(paymentId);
		payment.setTenantId("tenant-1");
		payment.setPaymentMethodId("card");
		payment.setProvider("noop");
		payment.setStatus(PaymentStatus.ACTIVE);
		payment.setRecurring(false);
		payment.setCreatedAt(Instant.now());
		payment.setUpdatedAt(Instant.now());

		IdempotencyKey key = new IdempotencyKey();
		key.setPaymentId(paymentId);
		key.setTenantId("tenant-1");
		key.setIdempotencyKey("abc");
		key.setTransactionId(transactionId);

		PaymentTransaction tx = new PaymentTransaction();
		tx.setId(transactionId);
		tx.setPaymentId(paymentId);
		tx.setStatus(TransactionStatus.SUCCESS);
		tx.setCreatedAt(Instant.now());
		tx.setUpdatedAt(Instant.now());

		when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));
		when(tenantResolver.resolveTenantId()).thenReturn("tenant-1");
		when(idempotencyKeyRepository.findByPaymentIdAndTenantIdAndIdempotencyKey(paymentId, "tenant-1", "abc"))
				.thenReturn(Optional.of(key));
		when(transactionRepository.findById(transactionId)).thenReturn(Optional.of(tx));

		var response = paymentService.executePayment(paymentId, "abc");
		Assertions.assertEquals(transactionId.toString(), response.transaction().id());
	}

	private Answer<Payment> persistPayment() {
		return invocation -> {
			Payment payment = invocation.getArgument(0);
			if (payment.getId() == null) {
				payment.setId(UUID.randomUUID());
			}
			if (payment.getCreatedAt() == null) {
				payment.setCreatedAt(Instant.now());
			}
			if (payment.getUpdatedAt() == null) {
				payment.setUpdatedAt(Instant.now());
			}
			return payment;
		};
	}
}
