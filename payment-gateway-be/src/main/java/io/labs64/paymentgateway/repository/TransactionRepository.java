package io.labs64.paymentgateway.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.labs64.paymentgateway.entity.TransactionEntity;

/**
 * Repository for {@link TransactionEntity}.
 * All queries are scoped by tenantId for multi-tenancy.
 */
@Repository
public interface TransactionRepository extends JpaRepository<TransactionEntity, UUID> {

    Optional<TransactionEntity> findByIdAndTenantId(UUID id, String tenantId);

    boolean existsByTenantIdAndPaymentIdAndIdempotencyKey(String tenantId, UUID paymentId, String idempotencyKey);

    Optional<TransactionEntity> findFirstByPaymentIdOrderByCreatedAtDesc(UUID paymentId);
}
