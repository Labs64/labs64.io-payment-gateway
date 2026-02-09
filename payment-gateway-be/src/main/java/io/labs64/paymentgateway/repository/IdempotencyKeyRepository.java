package io.labs64.paymentgateway.repository;

import io.labs64.paymentgateway.entity.IdempotencyKey;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, UUID> {
	Optional<IdempotencyKey> findByPaymentIdAndTenantIdAndIdempotencyKey(UUID paymentId, String tenantId,
			String idempotencyKey);
}
