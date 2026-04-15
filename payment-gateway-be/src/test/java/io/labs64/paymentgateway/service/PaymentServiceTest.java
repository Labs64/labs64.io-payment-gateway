package io.labs64.paymentgateway.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.labs64.paymentgateway.entity.PaymentEntity;
import io.labs64.paymentgateway.entity.PaymentEntity.PaymentStatus;
import io.labs64.paymentgateway.entity.TransactionEntity;
import io.labs64.paymentgateway.entity.TransactionEntity.TransactionStatus;
import io.labs64.paymentgateway.exception.IdempotencyConflictException;
import io.labs64.paymentgateway.exception.NotFoundException;
import io.labs64.paymentgateway.exception.PaymentNotPayableException;
import io.labs64.paymentgateway.mapper.PaymentMapper;
import io.labs64.paymentgateway.mapper.TransactionMapper;
import io.labs64.paymentgateway.psp.PaymentProviderRegistry;
import io.labs64.paymentgateway.psp.PspNextAction;
import io.labs64.paymentgateway.psp.PspPaymentResponse;
import io.labs64.paymentgateway.psp.noop.NoOpPaymentProvider;
import io.labs64.paymentgateway.repository.PaymentRepository;
import io.labs64.paymentgateway.repository.PspConfigRepository;
import io.labs64.paymentgateway.repository.TransactionRepository;
import io.labs64.paymentgateway.v1.model.CreatePaymentRequest;
import io.labs64.paymentgateway.v1.model.CreatePaymentResponse;
import io.labs64.paymentgateway.v1.model.ExecutePaymentResponse;
import io.labs64.paymentgateway.v1.model.Payment;
import io.labs64.paymentgateway.v1.model.PaymentDetailResponse;
import io.labs64.paymentgateway.v1.model.PurchaseOrder;
import io.labs64.paymentgateway.v1.model.Transaction;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private PspConfigRepository pspConfigRepository;
    @Mock private PaymentProviderRegistry providerRegistry;
    @Mock private PaymentMapper paymentMapper;
    @Mock private TransactionMapper transactionMapper;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        // inject real ObjectMapper via field
        try {
            var field = PaymentServiceImpl.class.getDeclaredField("objectMapper");
            field.setAccessible(true);
            field.set(paymentService, objectMapper);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ── createPayment ────────────────────────────────────────────────

    @Test
    void createPayment_unknownMethod_throwsValidation() {
        when(providerRegistry.hasProvider("unknown")).thenReturn(false);

        final CreatePaymentRequest req = buildCreateRequest("unknown");
        assertThrows(io.labs64.paymentgateway.exception.ValidationException.class,
                () -> paymentService.createPayment("tenant1", "corr1", req));
    }

    @Test
    void createPayment_success_returnsResponse() {
        when(providerRegistry.hasProvider("noop")).thenReturn(true);

        final PaymentEntity saved = buildPaymentEntity(PaymentStatus.INCOMPLETE);
        when(paymentRepository.save(any())).thenReturn(saved);

        final CreatePaymentResponse expected = new CreatePaymentResponse();
        expected.setPayment(new Payment());
        when(paymentMapper.toCreateResponse(any(), any())).thenReturn(expected);

        final CreatePaymentResponse result = paymentService.createPayment("tenant1", "corr1", buildCreateRequest("noop"));

        assertNotNull(result);
        assertEquals(expected, result);
    }

    // ── getPayment ───────────────────────────────────────────────────

    @Test
    void getPayment_notFound_throwsNotFoundException() {
        when(paymentRepository.findByIdAndTenantId(any(), anyString())).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
                () -> paymentService.getPayment("tenant1", UUID.randomUUID()));
    }

    @Test
    void getPayment_found_returnsDetail() {
        final PaymentEntity entity = buildPaymentEntity(PaymentStatus.INCOMPLETE);
        when(paymentRepository.findByIdAndTenantId(any(), anyString())).thenReturn(Optional.of(entity));

        final PaymentDetailResponse expected = new PaymentDetailResponse();
        when(paymentMapper.toDetailResponse(entity)).thenReturn(expected);

        final PaymentDetailResponse result = paymentService.getPayment("tenant1", entity.getId());
        assertEquals(expected, result);
    }

    // ── executePayment ───────────────────────────────────────────────

    @Test
    void executePayment_closedPayment_throwsNotPayable() {
        final PaymentEntity entity = buildPaymentEntity(PaymentStatus.CLOSED);
        when(paymentRepository.findByIdAndTenantId(any(), anyString())).thenReturn(Optional.of(entity));

        assertThrows(PaymentNotPayableException.class,
                () -> paymentService.executePayment("tenant1", "corr1", entity.getId(), "idem-1"));
    }

    @Test
    void executePayment_duplicateIdempotencyKey_throwsConflict() {
        final PaymentEntity entity = buildPaymentEntity(PaymentStatus.INCOMPLETE);
        when(paymentRepository.findByIdAndTenantId(any(), anyString())).thenReturn(Optional.of(entity));
        when(transactionRepository.existsByTenantIdAndPaymentIdAndIdempotencyKey(anyString(), any(), anyString()))
                .thenReturn(true);

        assertThrows(IdempotencyConflictException.class,
                () -> paymentService.executePayment("tenant1", "corr1", entity.getId(), "idem-dup"));
    }

    @Test
    void executePayment_success_closesOneTimePayment() {
        final PaymentEntity entity = buildPaymentEntity(PaymentStatus.INCOMPLETE);
        entity.setType(PaymentEntity.PaymentType.ONE_TIME);

        when(paymentRepository.findByIdAndTenantId(any(), anyString())).thenReturn(Optional.of(entity));
        when(transactionRepository.existsByTenantIdAndPaymentIdAndIdempotencyKey(anyString(), any(), anyString()))
                .thenReturn(false);
        when(pspConfigRepository.findByTenantIdAndPaymentMethodId(anyString(), anyString()))
                .thenReturn(Optional.empty());

        final NoOpPaymentProvider noOp = new NoOpPaymentProvider();
        when(providerRegistry.getProvider("noop")).thenReturn(noOp);

        final TransactionEntity savedTx = buildTransactionEntity(entity, TransactionStatus.SUCCESS);
        when(transactionRepository.save(any())).thenReturn(savedTx);
        when(paymentRepository.save(any())).thenReturn(entity);

        final ExecutePaymentResponse expected = new ExecutePaymentResponse();
        expected.setTransaction(new Transaction());
        when(transactionMapper.toExecuteResponse(any(), any())).thenReturn(expected);

        final ExecutePaymentResponse result = paymentService.executePayment("tenant1", "corr1", entity.getId(), "idem-1");

        assertNotNull(result);
        assertEquals(PaymentStatus.CLOSED, entity.getStatus());
    }

    // ── closePayment ─────────────────────────────────────────────────

    @Test
    void closePayment_alreadyClosed_throwsNotPayable() {
        final PaymentEntity entity = buildPaymentEntity(PaymentStatus.CLOSED);
        when(paymentRepository.findByIdAndTenantId(any(), anyString())).thenReturn(Optional.of(entity));

        assertThrows(PaymentNotPayableException.class,
                () -> paymentService.closePayment("tenant1", entity.getId()));
    }

    @Test
    void closePayment_active_setsClosedStatus() {
        final PaymentEntity entity = buildPaymentEntity(PaymentStatus.ACTIVE);
        when(paymentRepository.findByIdAndTenantId(any(), anyString())).thenReturn(Optional.of(entity));
        when(paymentRepository.save(any())).thenReturn(entity);

        final PaymentDetailResponse expected = new PaymentDetailResponse();
        when(paymentMapper.toDetailResponse(entity)).thenReturn(expected);

        paymentService.closePayment("tenant1", entity.getId());

        assertEquals(PaymentStatus.CLOSED, entity.getStatus());
    }

    // ── retryPayment ─────────────────────────────────────────────────

    @Test
    void retryPayment_closedPayment_throwsNotPayable() {
        final PaymentEntity entity = buildPaymentEntity(PaymentStatus.CLOSED);
        when(paymentRepository.findByIdAndTenantId(any(), anyString())).thenReturn(Optional.of(entity));

        assertThrows(PaymentNotPayableException.class,
                () -> paymentService.retryPayment("tenant1", "corr1", entity.getId(), "idem-retry"));
    }

    // ── helpers ──────────────────────────────────────────────────────

    private PaymentEntity buildPaymentEntity(final PaymentStatus status) {
        return PaymentEntity.builder()
                .id(UUID.randomUUID())
                .tenantId("tenant1")
                .paymentMethodId("noop")
                .status(status)
                .type(PaymentEntity.PaymentType.ONE_TIME)
                .amount(3000L)
                .currency("USD")
                .build();
    }

    private TransactionEntity buildTransactionEntity(final PaymentEntity payment, final TransactionStatus status) {
        return TransactionEntity.builder()
                .id(UUID.randomUUID())
                .payment(payment)
                .tenantId("tenant1")
                .idempotencyKey("idem-1")
                .status(status)
                .build();
    }

    private CreatePaymentRequest buildCreateRequest(final String paymentMethodId) {
        final PurchaseOrder po = new PurchaseOrder();
        po.setReferenceId("order-1");
        po.setTotalAmount(3000L);
        po.setCurrency("USD");
        po.setRecurring(false);

        final CreatePaymentRequest req = new CreatePaymentRequest();
        req.setPaymentMethodId(paymentMethodId);
        req.setPurchaseOrder(po);
        return req;
    }
}
