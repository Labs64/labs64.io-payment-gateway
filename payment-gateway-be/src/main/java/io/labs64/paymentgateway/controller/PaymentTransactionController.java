package io.labs64.paymentgateway.controller;

import java.util.UUID;

import io.labs64.authcontext.core.AuthContextHolder;
import io.labs64.paymentgateway.entity.PaymentTransactionEntity;
import io.labs64.paymentgateway.mapper.PaymentTransactionMapper;
import io.labs64.paymentgateway.model.PaymentTransaction;
import io.labs64.paymentgateway.model.PaymentTransactionStatus;
import io.labs64.paymentgateway.service.PaymentTransactionService;
import io.labs64.paymentgateway.service.filter.PaymentTransactionFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import io.labs64.paymentgateway.api.PaymentTransactionsApi;
import io.labs64.paymentgateway.model.PaymentTransactionsResponse;

@Slf4j
@RestController
@RequiredArgsConstructor
public class PaymentTransactionController implements PaymentTransactionsApi {
    private final PaymentTransactionService service;
    private final PaymentTransactionMapper mapper;

    @Override
    public ResponseEntity<PaymentTransactionsResponse> listPaymentTransactions(
            @Nullable final UUID paymentId,
            @Nullable final PaymentTransactionStatus status,
            final Pageable pageable) {
        final String tenantId = AuthContextHolder.require().tenantId();

        log.info("Payment transactions list requested | tenantId={}, paymentId={}, status={}, pageable={}",
                tenantId, paymentId, status, pageable);

        final PaymentTransactionFilter filter = new PaymentTransactionFilter(paymentId, status);
        final Page<PaymentTransactionEntity> entities = service.list(
                tenantId,
                filter,
                pageable);
        final PaymentTransactionsResponse response = mapper.toPaymentTransactionsResponse(entities);

        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<PaymentTransaction> getPaymentTransaction(final UUID paymentTransactionId) {
        final String tenantId = AuthContextHolder.require().tenantId();

        log.info("Payment transaction get requested | tenantId={}, paymentTransactionId={}",
                tenantId, paymentTransactionId);

        final PaymentTransactionEntity entity = service.get(tenantId, paymentTransactionId);
        final PaymentTransaction response = mapper.toDto(entity);

        return ResponseEntity.ok(response);
    }
}
