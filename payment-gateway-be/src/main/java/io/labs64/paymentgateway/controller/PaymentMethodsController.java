package io.labs64.paymentgateway.controller;

import java.util.List;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.labs64.paymentgateway.v1.api.PaymentMethodsApi;
import io.labs64.paymentgateway.v1.model.PaymentMethod;
import io.labs64.paymentgateway.v1.model.PspConfigRequest;
import io.labs64.paymentgateway.v1.model.PspConfigResponse;

@RestController
@RequestMapping("/api/v1")
public class PaymentMethodsController implements PaymentMethodsApi {

    private static final Logger log = LoggerFactory.getLogger(PaymentMethodsController.class);

    @Override
    public ResponseEntity<List<PaymentMethod>> getPaymentMethods(
            @Nullable String xCorrelationID,
            @Nullable String currency,
            @Nullable String country) {
        log.debug("GET /payment-methods - Retrieving payment methods | correlationId={}, currency={}, country={}",
                xCorrelationID, currency, country);

        // TODO: Implement - load payment methods from YAML config + tenant PSP config from DB
        log.debug("GET /payment-methods - Stub: returning empty list");

        return ResponseEntity.ok(List.of());
    }

    @Override
    public ResponseEntity<Void> configurePsp(
            String paymentMethodId,
            PspConfigRequest pspConfigRequest,
            @Nullable String xCorrelationID) {
        log.debug("PUT /payment-methods/{}/config - Configuring PSP | correlationId={}, configKeys={}",
                paymentMethodId, xCorrelationID,
                pspConfigRequest.getPspConfig() != null ? pspConfigRequest.getPspConfig().keySet() : "null");

        // TODO: Implement - validate paymentMethodId, store tenant-specific PSP config in DB
        log.debug("PUT /payment-methods/{}/config - Stub: configuration accepted", paymentMethodId);

        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<PspConfigResponse> getPspConfig(
            String paymentMethodId,
            @Nullable String xCorrelationID) {
        log.debug("GET /payment-methods/{}/config - Retrieving PSP config | correlationId={}",
                paymentMethodId, xCorrelationID);

        // TODO: Implement - load tenant-specific PSP config from DB, mask sensitive fields
        log.debug("GET /payment-methods/{}/config - Stub: returning 404 (not configured)", paymentMethodId);

        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }
}
