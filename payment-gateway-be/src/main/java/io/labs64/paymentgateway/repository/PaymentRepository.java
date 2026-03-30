package io.labs64.paymentgateway.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.labs64.paymentgateway.entity.PaymentEntity;

/**
 * Repository for {@link PaymentEntity}.
 * All queries are scoped by tenantId for multi-tenancy.
 */
@Repository
public interface PaymentRepository extends JpaRepository<PaymentEntity, UUID> {

    Optional<PaymentEntity> findByIdAndTenantId(UUID id, String tenantId);
}
