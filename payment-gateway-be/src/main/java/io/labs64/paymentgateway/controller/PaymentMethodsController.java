package io.labs64.paymentgateway.controller;

import java.util.List;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.labs64.paymentgateway.exception.TenantRequiredException;
import io.labs64.paymentgateway.service.PaymentMethodService;
import io.labs64.paymentgateway.v1.api.PaymentMethodsApi;
import io.labs64.paymentgateway.v1.model.PaymentMethod;
import io.labs64.paymentgateway.v1.model.PspConfigRequest;
import io.labs64.paymentgateway.v1.model.PspConfigResponse;
import io.labs64.paymentgateway.web.TenantContext;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PaymentMethodsController implements PaymentMethodsApi {

    private static final Logger log = LoggerFactory.getLogger(PaymentMethodsController.class);

    private final PaymentMethodService paymentMethodService;

    @Override
    public ResponseEntity<List<PaymentMethod>> getPaymentMethods(
            @Nullable String xCorrelationID,
            @Nullable String currency,
            @Nullable String country) {
        final String tenantId = requireTenantId();
        log.info("GET /payment-methods | correlationId={}, tenantId={}, currency={}, country={}",
                xCorrelationID, tenantId, currency, country);

        final List<PaymentMethod> methods = paymentMethodService.getPaymentMethods(tenantId, currency, country);
        return ResponseEntity.ok(methods);
    }

    @Override
    public ResponseEntity<Void> configurePsp(
            String paymentMethodId,
            PspConfigRequest pspConfigRequest,
            @Nullable String xCorrelationID) {
        final String tenantId = requireTenantId();
        log.info("PUT /payment-methods/{}/config | correlationId={}, tenantId={}, configKeys={}",
                paymentMethodId, xCorrelationID, tenantId,
                pspConfigRequest.getPspConfig() != null ? pspConfigRequest.getPspConfig().keySet() : "null");

        paymentMethodService.configurePsp(tenantId, paymentMethodId, pspConfigRequest);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<PspConfigResponse> getPspConfig(
            String paymentMethodId,
            @Nullable String xCorrelationID) {
        final String tenantId = requireTenantId();
        log.info("GET /payment-methods/{}/config | correlationId={}, tenantId={}",
                paymentMethodId, xCorrelationID, tenantId);

        final PspConfigResponse config = paymentMethodService.getPspConfig(tenantId, paymentMethodId);
        return ResponseEntity.ok(config);
    }

    private String requireTenantId() {
        final String tenantId = TenantContext.getTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            throw new TenantRequiredException();
        }
        return tenantId;
    }
}
