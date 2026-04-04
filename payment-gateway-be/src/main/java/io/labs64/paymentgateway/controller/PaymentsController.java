package io.labs64.paymentgateway.controller;

import io.labs64.paymentgateway.v1.api.PaymentsApi;
import io.labs64.paymentgateway.v1.model.CreatePaymentRequest;
import io.labs64.paymentgateway.v1.model.CreatePaymentResponse;
import io.labs64.paymentgateway.v1.model.ExecutePaymentResponse;
import io.labs64.paymentgateway.v1.model.PaymentDetailResponse;
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
public class PaymentsController implements PaymentsApi {

    private static final Logger log = LoggerFactory.getLogger(PaymentsController.class);

    @Override
    public ResponseEntity<CreatePaymentResponse> createPayment(
            CreatePaymentRequest createPaymentRequest,
            @Nullable String xCorrelationID) {
        log.debug("POST /payments - Creating payment instance | correlationId={}, paymentMethodId={}, amount={}, currency={}, recurring={}",
                xCorrelationID,
                createPaymentRequest.getPaymentMethodId(),
                createPaymentRequest.getPurchaseOrder() != null ? createPaymentRequest.getPurchaseOrder().getTotalAmount() : "null",
                createPaymentRequest.getPurchaseOrder() != null ? createPaymentRequest.getPurchaseOrder().getCurrency() : "null",
                createPaymentRequest.getPurchaseOrder() != null ? createPaymentRequest.getPurchaseOrder().getRecurring() : "null");

        // TODO: Implement - validate request, load PSP config, create Payment entity (INCOMPLETE), publish payment.created event
        log.debug("POST /payments - Stub: returning 501 Not Implemented");

        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @Override
    public ResponseEntity<PaymentDetailResponse> getPayment(
            UUID paymentId,
            @Nullable String xCorrelationID) {
        log.debug("GET /payments/{} - Retrieving payment details | correlationId={}",
                paymentId, xCorrelationID);

        // TODO: Implement - load payment from DB by paymentId scoped by tenantId from JWT
        log.debug("GET /payments/{} - Stub: returning 404 (not found)", paymentId);

        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    @Override
    public ResponseEntity<ExecutePaymentResponse> executePayment(
            UUID paymentId,
            String idempotencyKey,
            @Nullable String xCorrelationID) {
        log.debug("POST /payments/{}/pay - Executing payment | correlationId={}",
                paymentId, xCorrelationID);
        log.debug("POST /payments/{}/pay - idempotencyKey={}", paymentId, idempotencyKey);

        // TODO: Implement - check idempotency key, create Transaction (PENDING), execute via PSP adapter,
        //       update Transaction status, update Payment status, publish payment.finalized event
        log.debug("POST /payments/{}/pay - Stub: returning 501 Not Implemented", paymentId);

        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @Override
    public ResponseEntity<PaymentDetailResponse> closePayment(
            UUID paymentId,
            @Nullable String xCorrelationID) {
        log.debug("POST /payments/{}/close - Closing payment | correlationId={}",
                paymentId, xCorrelationID);

        // TODO: Implement - validate payment state, update to CLOSED, publish payment.closed event
        log.debug("POST /payments/{}/close - Stub: returning 404 (not found)", paymentId);

        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    @Override
    public ResponseEntity<ExecutePaymentResponse> retryPayment(
            UUID paymentId,
            String idempotencyKey,
            @Nullable String xCorrelationID) {
        log.debug("POST /payments/{}/retry - Retrying payment | correlationId={}",
                paymentId, xCorrelationID);
        log.debug("POST /payments/{}/retry - idempotencyKey={}", paymentId, idempotencyKey);

        // TODO: Implement - validate payment is in retryable state, create new Transaction, execute via PSP
        log.debug("POST /payments/{}/retry - Stub: returning 501 Not Implemented", paymentId);

        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }
}
