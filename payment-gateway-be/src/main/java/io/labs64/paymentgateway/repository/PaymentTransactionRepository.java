package io.labs64.paymentgateway.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import io.labs64.paymentgateway.entity.PaymentTransactionEntity;
import io.labs64.paymentgateway.model.PaymentTransactionStatus;

/**
 * Repository for {@link PaymentTransactionEntity}.
 * All queries are scoped by tenantId for multi-tenancy.
 */
@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransactionEntity, UUID> {

    Optional<PaymentTransactionEntity> findByIdAndTenantId(UUID id, String tenantId);

    @Query("""
            SELECT pt
            FROM PaymentTransactionEntity pt
            WHERE pt.tenantId = :tenantId
              AND (:paymentId IS NULL OR pt.paymentId = :paymentId)
              AND (:status IS NULL OR pt.status = :status)
            """)
    Page<PaymentTransactionEntity> searchByTenantId(
            @Param("tenantId") String tenantId,
            @Param("paymentId") UUID paymentId,
            @Param("status") PaymentTransactionStatus status,
            Pageable pageable);

}
