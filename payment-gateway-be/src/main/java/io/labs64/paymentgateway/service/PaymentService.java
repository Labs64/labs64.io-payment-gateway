package io.labs64.paymentgateway.service;

import io.labs64.paymentgateway.config.PaymentMethodProperties;
import io.labs64.paymentgateway.entity.IdempotencyKey;
import io.labs64.paymentgateway.entity.Payment;
import io.labs64.paymentgateway.entity.PaymentStatus;
import io.labs64.paymentgateway.entity.PaymentTransaction;
import io.labs64.paymentgateway.entity.TransactionStatus;
import io.labs64.paymentgateway.dto.PaymentCreateRequest;
import io.labs64.paymentgateway.dto.PaymentResponse;
import io.labs64.paymentgateway.dto.TransactionResponse;
import io.labs64.paymentgateway.exception.NotFoundException;
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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentService {
	private static final Logger logger = LoggerFactory.getLogger(PaymentService.class);

	private final PaymentRepository paymentRepository;
	private final PaymentTransactionRepository transactionRepository;
	private final IdempotencyKeyRepository idempotencyKeyRepository;
	private final PaymentMethodService paymentMethodService;
	private final PaymentProviderRegistry providerRegistry;
	private final TenantPspConfigService tenantPspConfigService;
	private final PaymentEventPublisher eventPublisher;
	private final PaymentMapper paymentMapper;
	private final JsonService jsonService;
	private final TenantResolver tenantResolver;

	@Transactional
	public PaymentResponse createPayment(PaymentCreateRequest request) {
		if (request == null || request.paymentMethodId() == null) {
			throw new IllegalArgumentException("paymentMethodId is required");
		}
		PaymentMethodProperties.PaymentMethodConfig method = paymentMethodService
				.getMethodById(request.paymentMethodId());
		String tenantId = tenantResolver.resolveTenantId();

		Payment payment = new Payment();
		payment.setTenantId(tenantId);
		payment.setPaymentMethodId(method.getId());
		payment.setProvider(method.getProvider());
		payment.setRecurring(request.recurring());
		payment.setStatus(PaymentStatus.INCOMPLETE);
		payment.setPurchaseOrder(jsonService.toJson(request.purchaseOrder()));
		payment.setBillingInfo(jsonService.toJson(request.billingInfo()));
		payment.setShippingInfo(jsonService.toJson(request.shippingInfo()));
		payment.setExtra(jsonService.toJson(request.extra()));
		paymentRepository.save(payment);

		Map<String, Object> tenantConfig = tenantPspConfigService
				.getConfigOrEmpty(tenantId, method.getProvider());
		Map<String, Object> requestData = new HashMap<>();
		requestData.put("purchaseOrder", request.purchaseOrder());
		requestData.put("billingInfo", request.billingInfo());
		requestData.put("shippingInfo", request.shippingInfo());
		requestData.put("extra", request.extra());

		PaymentProvider provider = providerRegistry.getProvider(method.getProvider());
		PaymentProviderResult result = provider.initiate(new PaymentProviderContext(payment, tenantConfig, requestData));

		if (result != null) {
			if (result.pspData() != null) {
				payment.setPspData(jsonService.toJson(result.pspData()));
				payment.setPspReference(extractPspReference(result.pspData()));
			}
			if (result.nextAction() != null) {
				payment.setNextActionType(result.nextAction().type());
				payment.setNextActionDetails(jsonService.toJson(result.nextAction().details()));
			}
		}

		if (payment.getNextActionType() == null || "none".equalsIgnoreCase(payment.getNextActionType())) {
			payment.setStatus(PaymentStatus.ACTIVE);
		} else {
			payment.setStatus(PaymentStatus.INCOMPLETE);
		}

		paymentRepository.save(payment);
		logger.info("Payment created id={} tenant={} method={}", payment.getId(), tenantId, method.getId());
		return new PaymentResponse(paymentMapper.toDto(payment), paymentMapper.toNextAction(payment));
	}

	@Transactional
	public TransactionResponse executePayment(UUID paymentId, String idempotencyKey) {
		if (idempotencyKey == null || idempotencyKey.isBlank()) {
			throw new IllegalArgumentException("Idempotency-Key header is required");
		}
		String tenantId = tenantResolver.resolveTenantId();
		Payment payment = paymentRepository.findById(paymentId)
				.orElseThrow(() -> new NotFoundException("Payment not found: " + paymentId));
		if (!tenantId.equals(payment.getTenantId())) {
			throw new NotFoundException("Payment not found: " + paymentId);
		}
		if (payment.getStatus() == PaymentStatus.CLOSED) {
			throw new IllegalStateException("Payment is closed");
		}

		IdempotencyKey existing = idempotencyKeyRepository
				.findByPaymentIdAndTenantIdAndIdempotencyKey(paymentId, tenantId, idempotencyKey)
				.orElse(null);
		if (existing != null) {
			PaymentTransaction stored = transactionRepository.findById(existing.getTransactionId())
					.orElseThrow(() -> new NotFoundException("Transaction not found: " + existing.getTransactionId()));
			return new TransactionResponse(paymentMapper.toDto(stored));
		}

		Map<String, Object> tenantConfig = tenantPspConfigService
				.getConfigOrEmpty(tenantId, payment.getProvider());
		Map<String, Object> requestData = new HashMap<>();
		requestData.put("pspData", jsonService.toMap(payment.getPspData()));
		requestData.put("purchaseOrder", jsonService.toMap(payment.getPurchaseOrder()));
		requestData.put("billingInfo", jsonService.toMap(payment.getBillingInfo()));
		requestData.put("shippingInfo", jsonService.toMap(payment.getShippingInfo()));
		requestData.put("extra", jsonService.toMap(payment.getExtra()));

		PaymentProvider provider = providerRegistry.getProvider(payment.getProvider());
		PaymentProviderResult result = provider.execute(new PaymentProviderContext(payment, tenantConfig, requestData));
		TransactionStatus status = result != null && result.transactionStatus() != null
				? result.transactionStatus()
				: TransactionStatus.PENDING;

		PaymentTransaction transaction = new PaymentTransaction();
		transaction.setPaymentId(paymentId);
		transaction.setStatus(status);
		transaction.setPspData(jsonService.toJson(result != null ? result.pspData() : Map.of()));
		transaction.setPspReference(extractPspReference(result != null ? result.pspData() : Map.of()));
		transactionRepository.save(transaction);

		IdempotencyKey key = new IdempotencyKey();
		key.setPaymentId(paymentId);
		key.setTenantId(tenantId);
		key.setIdempotencyKey(idempotencyKey);
		key.setTransactionId(transaction.getId());
		idempotencyKeyRepository.save(key);

		if (result != null && result.nextAction() != null) {
			payment.setNextActionType(result.nextAction().type());
			payment.setNextActionDetails(jsonService.toJson(result.nextAction().details()));
		}

		if (status == TransactionStatus.SUCCESS && !payment.isRecurring()) {
			payment.setStatus(PaymentStatus.CLOSED);
		} else if (status == TransactionStatus.SUCCESS) {
			payment.setStatus(PaymentStatus.ACTIVE);
		}

		paymentRepository.save(payment);

		if (status == TransactionStatus.SUCCESS || status == TransactionStatus.FAILED) {
			eventPublisher.publishFinalized(payment, transaction);
		}

		logger.info("Payment executed id={} transaction={} status={}", paymentId, transaction.getId(), status);
		return new TransactionResponse(paymentMapper.toDto(transaction));
	}

	private String extractPspReference(Map<String, Object> pspData) {
		if (pspData == null) {
			return null;
		}
		Object paymentIntentId = pspData.get("paymentIntentId");
		if (paymentIntentId != null) {
			return paymentIntentId.toString();
		}
		Object orderId = pspData.get("orderId");
		if (orderId != null) {
			return orderId.toString();
		}
		Object reference = pspData.get("reference");
		return reference != null ? reference.toString() : null;
	}

	@Transactional(readOnly = true)
	public PaymentResponse getPayment(UUID paymentId) {
		Payment payment = paymentRepository.findById(paymentId)
				.orElseThrow(() -> new NotFoundException("Payment not found: " + paymentId));
		String tenantId = tenantResolver.resolveTenantId();
		if (!tenantId.equals(payment.getTenantId())) {
			throw new NotFoundException("Payment not found: " + paymentId);
		}
		return new PaymentResponse(paymentMapper.toDto(payment), paymentMapper.toNextAction(payment));
	}

	@Transactional(readOnly = true)
	public TransactionResponse getTransaction(UUID transactionId) {
		PaymentTransaction transaction = transactionRepository.findById(transactionId)
				.orElseThrow(() -> new NotFoundException("Transaction not found: " + transactionId));
		Payment payment = paymentRepository.findById(transaction.getPaymentId())
				.orElseThrow(() -> new NotFoundException("Payment not found: " + transaction.getPaymentId()));
		String tenantId = tenantResolver.resolveTenantId();
		if (!tenantId.equals(payment.getTenantId())) {
			throw new NotFoundException("Transaction not found: " + transactionId);
		}
		return new TransactionResponse(paymentMapper.toDto(transaction));
	}

	@Transactional
	public void closePayment(UUID paymentId) {
		Payment payment = paymentRepository.findById(paymentId)
				.orElseThrow(() -> new NotFoundException("Payment not found: " + paymentId));
		String tenantId = tenantResolver.resolveTenantId();
		if (!tenantId.equals(payment.getTenantId())) {
			throw new NotFoundException("Payment not found: " + paymentId);
		}
		payment.setStatus(PaymentStatus.CLOSED);
		paymentRepository.save(payment);
		logger.info("Payment closed id={}", paymentId);
	}
}
