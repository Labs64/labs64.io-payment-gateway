package io.labs64.paymentgateway.repository;

import java.util.Optional;
import java.util.UUID;

import io.labs64.paymentgateway.model.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import io.labs64.paymentgateway.entity.PaymentEntity;

/**
 * Repository for {@link PaymentEntity}.
 * All queries are scoped by tenantId for multi-tenancy.
 */
@Repository
public interface PaymentRepository extends JpaRepository<PaymentEntity, UUID> {

    Optional<PaymentEntity> findByIdAndTenantId(UUID id, String tenantId);

    @Query("""
            SELECT p
            FROM PaymentEntity p
            WHERE p.tenantId = :tenantId
              AND (:status IS NULL OR p.status = :status)
            """)
    Page<PaymentEntity> searchByTenantId(@Param("tenantId") String tenantId, @Param("status") PaymentStatus status, Pageable pageable);

    @Query("""
            SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END
            FROM PaymentEntity p
            WHERE p.tenantId = :tenantId
              AND p.paymentProviderId = :paymentProviderId
            """)
    boolean existsByTenantIdAndPaymentProviderId(
            @Param("tenantId") String tenantId,
            @Param("paymentProviderId") UUID paymentProviderId);
}
