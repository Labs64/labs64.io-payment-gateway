package io.labs64.paymentgateway.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import io.labs64.paymentgateway.entity.CorrelationTraceEntity;

/**
 * Repository for correlation ID bindings.
 */
public interface CorrelationTraceRepository extends JpaRepository<CorrelationTraceEntity, UUID> {

    Optional<CorrelationTraceEntity> findFirstByEntityTypeAndEntityIdOrderByCreatedAtDesc(
            String entityType,
            UUID entityId);
}
