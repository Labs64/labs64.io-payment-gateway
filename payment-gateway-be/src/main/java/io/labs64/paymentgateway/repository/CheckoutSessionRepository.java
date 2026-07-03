package io.labs64.paymentgateway.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.labs64.paymentgateway.entity.CheckoutSessionEntity;

/**
 * Repository for user-facing checkout sessions.
 */
@Repository
public interface CheckoutSessionRepository extends JpaRepository<CheckoutSessionEntity, UUID> {

    Optional<CheckoutSessionEntity> findByIdAndTenantId(UUID id, String tenantId);

    Optional<CheckoutSessionEntity> findByTenantIdAndPaymentTransactionId(String tenantId, UUID paymentTransactionId);
}
