package io.labs64.paymentgateway.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.labs64.paymentgateway.entity.CorrelationTraceEntity;

/**
 * Repository for correlation ID bindings.
 */
@Repository
public interface CorrelationTraceRepository extends JpaRepository<CorrelationTraceEntity, UUID> {

    Optional<CorrelationTraceEntity> findFirstByEntityTypeAndEntityIdOrderByCreatedAtDesc(
            String entityType,
            UUID entityId);
}
