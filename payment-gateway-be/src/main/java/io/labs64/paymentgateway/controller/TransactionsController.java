package io.labs64.paymentgateway.controller;

import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.labs64.paymentgateway.exception.TenantRequiredException;
import io.labs64.paymentgateway.service.TransactionService;
import io.labs64.paymentgateway.v1.api.TransactionsApi;
import io.labs64.paymentgateway.v1.model.TransactionResponse;
import io.labs64.paymentgateway.web.TenantContext;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class TransactionsController implements TransactionsApi {

    private static final Logger log = LoggerFactory.getLogger(TransactionsController.class);

    private final TransactionService transactionService;

    @Override
    public ResponseEntity<TransactionResponse> getTransaction(
            UUID transactionId,
            @Nullable String xCorrelationID) {
        final String tenantId = requireTenantId();
        log.info("GET /transactions/{} | correlationId={}, tenantId={}", transactionId, xCorrelationID, tenantId);

        final TransactionResponse response = transactionService.getTransaction(tenantId, transactionId);
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
