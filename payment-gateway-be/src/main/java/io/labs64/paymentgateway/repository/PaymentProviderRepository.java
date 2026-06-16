package io.labs64.paymentgateway.repository;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import io.labs64.paymentgateway.entity.PaymentProviderEntity;

/**
 * Repository for tenant-owned payment provider state and PSP configuration.
 */
@Repository
public interface PaymentProviderRepository extends JpaRepository<PaymentProviderEntity, UUID> {

    Optional<PaymentProviderEntity> findByTenantIdAndProvider(String tenantId, String provider);

    @Query("""
            SELECT pm
            FROM PaymentProviderEntity pm
            WHERE pm.tenantId = :tenantId
              AND pm.provider IN :providers
              AND (:active IS NULL OR pm.active = :active)
            """)
    Page<PaymentProviderEntity> searchByTenantIdAndProviders(
            @Param("tenantId") String tenantId,
            @Param("providers") Set<String> providers,
            @Param("active") Boolean active,
            Pageable pageable);

    boolean existsByTenantIdAndProvider(String tenantId, String provider);

    int deleteByTenantIdAndProvider(String tenantId, String provider);
}
