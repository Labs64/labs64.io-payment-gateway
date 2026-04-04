package io.labs64.paymentgateway.controller;

import io.labs64.paymentgateway.v1.api.TransactionsApi;
import io.labs64.paymentgateway.v1.model.TransactionResponse;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class TransactionsController implements TransactionsApi {

    private static final Logger log = LoggerFactory.getLogger(TransactionsController.class);

    @Override
    public ResponseEntity<TransactionResponse> getTransaction(
            UUID transactionId,
            @Nullable String xCorrelationID) {
        log.debug("GET /transactions/{} - Retrieving transaction details | correlationId={}",
                transactionId, xCorrelationID);

        // TODO: Implement - load transaction from DB by transactionId scoped by tenantId from JWT
        log.debug("GET /transactions/{} - Stub: returning 404 (not found)", transactionId);

        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }
}
