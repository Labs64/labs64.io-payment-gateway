package io.labs64.paymentgateway.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import io.labs64.paymentgateway.entity.CheckoutSessionEntity;

/**
 * Repository for user-facing checkout sessions.
 */
@Repository
public interface CheckoutSessionRepository extends JpaRepository<CheckoutSessionEntity, UUID> {

    @EntityGraph(attributePaths = {"payment", "payment.paymentProvider", "paymentTransaction"})
    @Query("SELECT cs FROM CheckoutSessionEntity cs WHERE cs.id = :id")
    Optional<CheckoutSessionEntity> findConfirmationById(@Param("id") UUID id);

    Optional<CheckoutSessionEntity> findByIdAndTenantId(UUID id, String tenantId);

    Optional<CheckoutSessionEntity> findByTenantIdAndPaymentTransactionId(String tenantId, UUID paymentTransactionId);
}
