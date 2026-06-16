package io.labs64.paymentgateway.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import io.labs64.paymentgateway.entity.IdempotencyRequestEntity;
import jakarta.persistence.LockModeType;

@Repository
public interface IdempotencyRequestRepository extends JpaRepository<IdempotencyRequestEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<IdempotencyRequestEntity> findByTenantIdAndOperationAndKey(
            String tenantId,
            String operation,
            String key);

    @Modifying
    @Query(value = """
            INSERT INTO idempotency_request (tenant_id, operation, key, request_hash, status, expires_at)
            VALUES (:tenantId, :operation, :key, :requestHash, :status, :expiresAt)
            ON CONFLICT (tenant_id, operation, key) DO NOTHING
            """, nativeQuery = true)
    int insertProcessingIfAbsent(
            @Param("tenantId") String tenantId,
            @Param("operation") String operation,
            @Param("key") String key,
            @Param("requestHash") String requestHash,
            @Param("status") String status,
            @Param("expiresAt") java.time.OffsetDateTime expiresAt);

    @Modifying
    @Query(value = """
            DELETE FROM idempotency_request
            WHERE id IN (
                SELECT id
                FROM idempotency_request
                WHERE expires_at < :expiresAt
                ORDER BY expires_at ASC
                LIMIT :batchSize
            )
            """, nativeQuery = true)
    int deleteExpired(
            @Param("expiresAt") java.time.OffsetDateTime expiresAt,
            @Param("batchSize") int batchSize);
}
