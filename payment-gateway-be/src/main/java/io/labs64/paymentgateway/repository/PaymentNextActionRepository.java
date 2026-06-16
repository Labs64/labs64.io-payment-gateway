package io.labs64.paymentgateway.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.labs64.paymentgateway.entity.PaymentNextActionEntity;

/**
 * Repository for next actions tied to payment transaction attempts.
 */
@Repository
public interface PaymentNextActionRepository extends JpaRepository<PaymentNextActionEntity, UUID> {

    Optional<PaymentNextActionEntity> findByTransaction_Id(UUID transactionId);
}
