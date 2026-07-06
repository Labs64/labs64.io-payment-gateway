package io.labs64.paymentgateway.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.labs64.paymentgateway.entity.PaymentEntity;
import io.labs64.paymentgateway.idempotency.IdempotentOperation;
import io.labs64.paymentgateway.mapper.PaymentMapper;
import io.labs64.paymentgateway.mapper.PaymentTransactionMapper;
import io.labs64.paymentgateway.model.CreatePaymentRequest;
import io.labs64.paymentgateway.model.ExecutePaymentResponse;
import io.labs64.paymentgateway.model.NextAction;
import io.labs64.paymentgateway.model.PayPaymentRequest;
import io.labs64.paymentgateway.model.Payment;
import io.labs64.paymentgateway.model.PaymentListResponse;
import io.labs64.paymentgateway.model.PaymentStatus;
import io.labs64.paymentgateway.model.PaymentTransaction;
import io.labs64.paymentgateway.psp.spi.PaymentExecutionRequest;
import io.labs64.paymentgateway.psp.spi.PaymentNextAction;
import io.labs64.paymentgateway.security.AuthContextHolder;
import io.labs64.paymentgateway.service.PayPaymentResponse;
import io.labs64.paymentgateway.service.PaymentService;
import io.labs64.paymentgateway.service.filter.PaymentFilter;
import io.labs64.paymentgateway.api.PaymentsApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
public class PaymentController implements PaymentsApi {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final PaymentService service;
    private final PaymentMapper paymentMapper;
    private final PaymentTransactionMapper paymentTransactionMapper;
    private final ObjectMapper objectMapper;

    @Override
    public ResponseEntity<Payment> getPayment(final UUID paymentId) {
        final String tenantId = AuthContextHolder.require().tenantId();

        log.info("Payment get requested | tenantId={}, id={}", tenantId, paymentId);

        final PaymentEntity entity = service.get(tenantId, paymentId);
        final Payment response = paymentMapper.toDto(entity);

        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<PaymentListResponse> listPayments(
            @Nullable final PaymentStatus status,
            final Pageable pageable) {
        final String tenantId = AuthContextHolder.require().tenantId();

        log.info("Payment list requested | tenantId={}, status={}, pageable={}",
                tenantId, status, pageable);

        final Page<PaymentEntity> entities = service.list(
                tenantId,
                new PaymentFilter(status),
                pageable);
        final PaymentListResponse response = paymentMapper.toPage(entities);

        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<Payment> createPayment(final CreatePaymentRequest request) {
        final String tenantId = AuthContextHolder.require().tenantId();

        log.info("Payment create requested | tenantId={}, request={}", tenantId, request);

        final PaymentEntity entity = service.create(tenantId, request.getProvider(), paymentMapper.toEntity(request));
        final Payment response = paymentMapper.toDto(entity);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }


    @Override
    @IdempotentOperation
    public ResponseEntity<ExecutePaymentResponse> payPayment(final UUID paymentId, final PayPaymentRequest request) {
        final String tenantId = AuthContextHolder.require().tenantId();

        log.info("Payment attempt requested | tenantId={}, paymentId={}", tenantId, paymentId);

        final PayPaymentResponse paymentResponse = service.pay(tenantId, paymentId, toPaymentExecutionRequest(request));
        final Payment payment = paymentMapper.toDto(paymentResponse.payment());
        final PaymentTransaction transaction = paymentTransactionMapper.toDto(paymentResponse.transaction());

        final ExecutePaymentResponse response = new ExecutePaymentResponse();
        response.setPayment(payment);
        response.setPaymentTransaction(transaction);

        final PaymentNextAction paymentNextAction = paymentResponse.nextAction();

        if (paymentNextAction != null) {
            final NextAction nextAction = new NextAction(paymentNextAction.type());
            nextAction.setDetails(paymentNextAction.details());
            response.setNextAction(nextAction);
        }

        return ResponseEntity.ok(response);
    }

    private PaymentExecutionRequest toPaymentExecutionRequest(final PayPaymentRequest request) {
        if (request == null || request.getCheckout() == null) {
            return PaymentExecutionRequest.empty();
        }
        return new PaymentExecutionRequest(objectMapper.convertValue(request.getCheckout(), MAP_TYPE));
    }

    @Override
    public ResponseEntity<Payment> closePayment(final UUID paymentId) {
        final String tenantId = AuthContextHolder.require().tenantId();

        log.info("Payment close requested | tenantId={}, paymentId={}", tenantId, paymentId);

        final PaymentEntity payment = service.close(tenantId, paymentId);
        final Payment response = paymentMapper.toDto(payment);

        return ResponseEntity.ok(response);
    }

}
