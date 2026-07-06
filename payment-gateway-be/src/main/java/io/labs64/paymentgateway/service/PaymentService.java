package io.labs64.paymentgateway.service;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import io.labs64.paymentgateway.entity.PaymentEntity;
import io.labs64.paymentgateway.psp.spi.PaymentExecutionRequest;
import io.labs64.paymentgateway.service.filter.PaymentFilter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Service for managing payment instances and executing payments.
 */
public interface PaymentService {
    Optional<PaymentEntity> find(String tenantId, UUID id);

    PaymentEntity get(String tenantId, UUID id);

    Page<PaymentEntity> list(
            final String tenantId,
            final PaymentFilter filter,
            final Pageable pageable);

    PaymentEntity create(String tenantId, String provider, PaymentEntity entity);

    PaymentEntity update(String tenantId, UUID id, Consumer<PaymentEntity> updater);

    PaymentEntity close(String tenantId, UUID id);

    PayPaymentResponse pay(String tenantId, UUID id);

    PayPaymentResponse pay(String tenantId, UUID id, PaymentExecutionRequest request);
}
