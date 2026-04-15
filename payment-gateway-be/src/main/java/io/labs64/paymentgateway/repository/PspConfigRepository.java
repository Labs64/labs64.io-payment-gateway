package io.labs64.paymentgateway.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.labs64.paymentgateway.entity.PspConfigEntity;

/**
 * Repository for {@link PspConfigEntity}.
 * All queries are scoped by tenantId for multi-tenancy.
 */
@Repository
public interface PspConfigRepository extends JpaRepository<PspConfigEntity, UUID> {

    Optional<PspConfigEntity> findByTenantIdAndPaymentMethodId(String tenantId, String paymentMethodId);

    boolean existsByTenantIdAndPaymentMethodId(String tenantId, String paymentMethodId);
}
