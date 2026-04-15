package io.labs64.paymentgateway.controller;

import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.labs64.paymentgateway.exception.TenantRequiredException;
import io.labs64.paymentgateway.service.PaymentService;
import io.labs64.paymentgateway.v1.api.PaymentsApi;
import io.labs64.paymentgateway.v1.model.CreatePaymentRequest;
import io.labs64.paymentgateway.v1.model.CreatePaymentResponse;
import io.labs64.paymentgateway.v1.model.ExecutePaymentResponse;
import io.labs64.paymentgateway.v1.model.PaymentDetailResponse;
import io.labs64.paymentgateway.web.CorrelationIdFilter;
import io.labs64.paymentgateway.web.TenantContext;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PaymentsController implements PaymentsApi {

    private static final Logger log = LoggerFactory.getLogger(PaymentsController.class);

    private final PaymentService paymentService;

    @Override
    public ResponseEntity<CreatePaymentResponse> createPayment(
            CreatePaymentRequest createPaymentRequest,
            @Nullable String xCorrelationID) {
        final String tenantId = requireTenantId();
        final String correlationId = MDC.get(CorrelationIdFilter.MDC_CORRELATION_ID);
        log.info("POST /payments | correlationId={}, tenantId={}, paymentMethodId={}",
                correlationId, tenantId, createPaymentRequest.getPaymentMethodId());

        final CreatePaymentResponse response = paymentService.createPayment(
                tenantId, correlationId, createPaymentRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Override
    public ResponseEntity<PaymentDetailResponse> getPayment(
            UUID paymentId,
            @Nullable String xCorrelationID) {
        final String tenantId = requireTenantId();
        log.info("GET /payments/{} | correlationId={}, tenantId={}", paymentId, xCorrelationID, tenantId);

        final PaymentDetailResponse response = paymentService.getPayment(tenantId, paymentId);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<ExecutePaymentResponse> executePayment(
            UUID paymentId,
            String idempotencyKey,
            @Nullable String xCorrelationID) {
        final String tenantId = requireTenantId();
        final String correlationId = MDC.get(CorrelationIdFilter.MDC_CORRELATION_ID);
        log.info("POST /payments/{}/pay | correlationId={}, tenantId={}", paymentId, correlationId, tenantId);

        final ExecutePaymentResponse response = paymentService.executePayment(
                tenantId, correlationId, paymentId, idempotencyKey);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<PaymentDetailResponse> closePayment(
            UUID paymentId,
            @Nullable String xCorrelationID) {
        final String tenantId = requireTenantId();
        log.info("POST /payments/{}/close | correlationId={}, tenantId={}", paymentId, xCorrelationID, tenantId);

        final PaymentDetailResponse response = paymentService.closePayment(tenantId, paymentId);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<ExecutePaymentResponse> retryPayment(
            UUID paymentId,
            String idempotencyKey,
            @Nullable String xCorrelationID) {
        final String tenantId = requireTenantId();
        final String correlationId = MDC.get(CorrelationIdFilter.MDC_CORRELATION_ID);
        log.info("POST /payments/{}/retry | correlationId={}, tenantId={}", paymentId, correlationId, tenantId);

        final ExecutePaymentResponse response = paymentService.retryPayment(
                tenantId, correlationId, paymentId, idempotencyKey);
        return ResponseEntity.ok(response);
    }

    private String requireTenantId() {
        final String tenantId = TenantContext.getTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            throw new TenantRequiredException();
        }
        return tenantId;
    }
}
