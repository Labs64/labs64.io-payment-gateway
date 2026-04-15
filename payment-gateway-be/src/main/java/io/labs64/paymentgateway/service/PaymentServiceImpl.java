package io.labs64.paymentgateway.service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.labs64.paymentgateway.entity.PaymentEntity;
import io.labs64.paymentgateway.entity.PaymentEntity.PaymentStatus;
import io.labs64.paymentgateway.entity.PaymentEntity.PaymentType;
import io.labs64.paymentgateway.entity.PspConfigEntity;
import io.labs64.paymentgateway.entity.TransactionEntity;
import io.labs64.paymentgateway.entity.TransactionEntity.TransactionStatus;
import io.labs64.paymentgateway.exception.IdempotencyConflictException;
import io.labs64.paymentgateway.exception.NotFoundException;
import io.labs64.paymentgateway.exception.PaymentNotPayableException;
import io.labs64.paymentgateway.exception.ValidationException;
import io.labs64.paymentgateway.mapper.PaymentMapper;
import io.labs64.paymentgateway.mapper.TransactionMapper;
import io.labs64.paymentgateway.psp.PaymentProvider;
import io.labs64.paymentgateway.psp.PaymentProviderRegistry;
import io.labs64.paymentgateway.psp.PspPaymentResponse;
import io.labs64.paymentgateway.repository.PaymentRepository;
import io.labs64.paymentgateway.repository.PspConfigRepository;
import io.labs64.paymentgateway.repository.TransactionRepository;
import io.labs64.paymentgateway.v1.model.CreatePaymentRequest;
import io.labs64.paymentgateway.v1.model.CreatePaymentResponse;
import io.labs64.paymentgateway.v1.model.ExecutePaymentResponse;
import io.labs64.paymentgateway.v1.model.PaymentDetailResponse;
import lombok.RequiredArgsConstructor;

/**
 * Implementation of {@link PaymentService}.
 * Handles the full payment lifecycle including creation, execution, closing, and retry.
 */
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentServiceImpl.class);

    private final PaymentRepository paymentRepository;
    private final TransactionRepository transactionRepository;
    private final PspConfigRepository pspConfigRepository;
    private final PaymentProviderRegistry providerRegistry;
    private final PaymentMapper paymentMapper;
    private final TransactionMapper transactionMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public CreatePaymentResponse createPayment(final String tenantId, final String correlationId,
            final CreatePaymentRequest request) {
        log.info("Creating payment for tenantId={}, paymentMethodId={}", tenantId, request.getPaymentMethodId());

        // Validate payment method exists
        if (!providerRegistry.hasProvider(request.getPaymentMethodId())) {
            throw new ValidationException(
                    "Unknown payment method: " + request.getPaymentMethodId());
        }

        final boolean isRecurring = request.getPurchaseOrder().getRecurring() != null
                && request.getPurchaseOrder().getRecurring();

        final PaymentEntity payment = PaymentEntity.builder()
                .tenantId(tenantId)
                .paymentMethodId(request.getPaymentMethodId())
                .status(PaymentStatus.INCOMPLETE)
                .type(isRecurring ? PaymentType.RECURRING : PaymentType.ONE_TIME)
                .amount(request.getPurchaseOrder().getTotalAmount())
                .currency(request.getPurchaseOrder().getCurrency())
                .description("Payment for " + request.getPurchaseOrder().getReferenceId())
                .purchaseOrderRef(request.getPurchaseOrder().getReferenceId())
                .correlationId(correlationId)
                .billingInfo(toMap(request.getBillingInfo()))
                .shippingInfo(toMap(request.getShippingInfo()))
                .extra(toMap(request.getExtra()))
                .build();

        final PaymentEntity saved = paymentRepository.save(payment);
        log.info("Payment created: id={}, status={}", saved.getId(), saved.getStatus());

        return paymentMapper.toCreateResponse(saved, null);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentDetailResponse getPayment(final String tenantId, final UUID paymentId) {
        log.debug("Loading payment for tenantId={}, paymentId={}", tenantId, paymentId);

        final PaymentEntity payment = paymentRepository.findByIdAndTenantId(paymentId, tenantId)
                .orElseThrow(() -> new NotFoundException("Payment with ID '" + paymentId + "' was not found."));

        return paymentMapper.toDetailResponse(payment);
    }

    @Override
    @Transactional
    public ExecutePaymentResponse executePayment(final String tenantId, final String correlationId,
            final UUID paymentId, final String idempotencyKey) {
        log.info("Executing payment for tenantId={}, paymentId={}", tenantId, paymentId);

        final PaymentEntity payment = paymentRepository.findByIdAndTenantId(paymentId, tenantId)
                .orElseThrow(() -> new NotFoundException("Payment with ID '" + paymentId + "' was not found."));

        // Check payability
        if (payment.getStatus() == PaymentStatus.CLOSED) {
            throw new PaymentNotPayableException(
                    "Payment is in " + payment.getStatus() + " state and cannot be executed.");
        }

        // Check idempotency
        if (transactionRepository.existsByTenantIdAndPaymentIdAndIdempotencyKey(
                tenantId, paymentId, idempotencyKey)) {
            throw new IdempotencyConflictException(
                    "Idempotency-Key '" + idempotencyKey + "' already used for this payment.");
        }

        return doExecute(payment, tenantId, correlationId, idempotencyKey);
    }

    @Override
    @Transactional
    public PaymentDetailResponse closePayment(final String tenantId, final UUID paymentId) {
        log.info("Closing payment for tenantId={}, paymentId={}", tenantId, paymentId);

        final PaymentEntity payment = paymentRepository.findByIdAndTenantId(paymentId, tenantId)
                .orElseThrow(() -> new NotFoundException("Payment with ID '" + paymentId + "' was not found."));

        if (payment.getStatus() == PaymentStatus.CLOSED) {
            throw new PaymentNotPayableException("Payment is already closed.");
        }

        payment.setStatus(PaymentStatus.CLOSED);
        final PaymentEntity saved = paymentRepository.save(payment);

        log.info("Payment closed: id={}", saved.getId());
        return paymentMapper.toDetailResponse(saved);
    }

    @Override
    @Transactional
    public ExecutePaymentResponse retryPayment(final String tenantId, final String correlationId,
            final UUID paymentId, final String idempotencyKey) {
        log.info("Retrying payment for tenantId={}, paymentId={}", tenantId, paymentId);

        final PaymentEntity payment = paymentRepository.findByIdAndTenantId(paymentId, tenantId)
                .orElseThrow(() -> new NotFoundException("Payment with ID '" + paymentId + "' was not found."));

        if (payment.getStatus() == PaymentStatus.CLOSED) {
            throw new PaymentNotPayableException("Payment is closed and cannot be retried.");
        }

        // Check idempotency
        if (transactionRepository.existsByTenantIdAndPaymentIdAndIdempotencyKey(
                tenantId, paymentId, idempotencyKey)) {
            throw new IdempotencyConflictException(
                    "Idempotency-Key '" + idempotencyKey + "' already used for this payment retry.");
        }

        return doExecute(payment, tenantId, correlationId, idempotencyKey);
    }

    /**
     * Core execution logic shared by executePayment and retryPayment.
     */
    private ExecutePaymentResponse doExecute(final PaymentEntity payment, final String tenantId,
            final String correlationId, final String idempotencyKey) {
        // Load PSP config
        final Map<String, String> pspConfig = pspConfigRepository
                .findByTenantIdAndPaymentMethodId(tenantId, payment.getPaymentMethodId())
                .map(PspConfigEntity::getConfig)
                .orElse(Map.of());

        // Get provider
        final PaymentProvider provider = providerRegistry.getProvider(payment.getPaymentMethodId());

        // Create pending transaction
        final TransactionEntity transaction = TransactionEntity.builder()
                .payment(payment)
                .tenantId(tenantId)
                .idempotencyKey(idempotencyKey)
                .status(TransactionStatus.PENDING)
                .build();
        transactionRepository.save(transaction);

        // Execute via PSP
        final PspPaymentResponse pspResponse = provider.executePayment(payment, pspConfig);

        // Update transaction
        transaction.setStatus(pspResponse.getStatus());
        transaction.setPspData(pspResponse.getPspData());
        transaction.setFailureCode(pspResponse.getFailureCode());
        transaction.setFailureMessage(pspResponse.getFailureMessage());
        transactionRepository.save(transaction);

        // Update payment status
        if (pspResponse.getStatus() == TransactionStatus.SUCCESS
                && payment.getType() == PaymentType.ONE_TIME) {
            payment.setStatus(PaymentStatus.CLOSED);
        } else if (pspResponse.getStatus() == TransactionStatus.SUCCESS) {
            payment.setStatus(PaymentStatus.ACTIVE);
        } else if (pspResponse.isAsyncCompletion()) {
            payment.setStatus(PaymentStatus.INCOMPLETE);
        }

        // Store next action if present
        if (pspResponse.getNextAction() != null) {
            final Map<String, Object> nextActionMap = new HashMap<>();
            nextActionMap.put("type", pspResponse.getNextAction().getType());
            nextActionMap.put("details", pspResponse.getNextAction().getDetails());
            payment.setNextAction(nextActionMap);
        }

        paymentRepository.save(payment);

        log.info("Payment executed: paymentId={}, transactionId={}, status={}",
                payment.getId(), transaction.getId(), pspResponse.getStatus());

        return transactionMapper.toExecuteResponse(transaction, pspResponse.getNextAction());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(final Object obj) {
        if (obj == null) {
            return null;
        }
        return objectMapper.convertValue(obj, Map.class);
    }
}
