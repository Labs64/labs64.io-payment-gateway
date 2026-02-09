package io.labs64.paymentgateway.repository;

import io.labs64.paymentgateway.entity.TenantPspConfig;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantPspConfigRepository extends JpaRepository<TenantPspConfig, UUID> {
	Optional<TenantPspConfig> findByTenantIdAndProvider(String tenantId, String provider);
}
