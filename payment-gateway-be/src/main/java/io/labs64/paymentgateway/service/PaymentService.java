package io.labs64.paymentgateway.service;

import java.util.UUID;

import io.labs64.paymentgateway.v1.model.CreatePaymentRequest;
import io.labs64.paymentgateway.v1.model.CreatePaymentResponse;
import io.labs64.paymentgateway.v1.model.ExecutePaymentResponse;
import io.labs64.paymentgateway.v1.model.PaymentDetailResponse;

/**
 * Service for managing payment instances and executing payments.
 */
public interface PaymentService {

    /**
     * Create a new payment instance. No actual charge is performed.
     *
     * @param tenantId      tenant identifier
     * @param correlationId correlation ID for tracing
     * @param request       payment creation request
     * @return created payment with optional next action
     */
    CreatePaymentResponse createPayment(String tenantId, String correlationId, CreatePaymentRequest request);

    /**
     * Retrieve payment details by ID, scoped by tenant.
     *
     * @param tenantId  tenant identifier
     * @param paymentId payment identifier
     * @return payment details with optional next action
     */
    PaymentDetailResponse getPayment(String tenantId, UUID paymentId);

    /**
     * Execute a payment via the PSP adapter.
     *
     * @param tenantId       tenant identifier
     * @param correlationId  correlation ID for tracing
     * @param paymentId      payment identifier
     * @param idempotencyKey unique key per tenant + payment to prevent duplicates
     * @return execution result with transaction details
     */
    ExecutePaymentResponse executePayment(String tenantId, String correlationId, UUID paymentId,
            String idempotencyKey);

    /**
     * Close a payment. No further pay operations can be executed.
     *
     * @param tenantId  tenant identifier
     * @param paymentId payment identifier
     * @return updated payment details
     */
    PaymentDetailResponse closePayment(String tenantId, UUID paymentId);

    /**
     * Retry a failed payment.
     *
     * @param tenantId       tenant identifier
     * @param correlationId  correlation ID for tracing
     * @param paymentId      payment identifier
     * @param idempotencyKey unique key per tenant + payment to prevent duplicate retries
     * @return execution result with transaction details
     */
    ExecutePaymentResponse retryPayment(String tenantId, String correlationId, UUID paymentId,
            String idempotencyKey);
}
