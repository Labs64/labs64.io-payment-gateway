package io.labs64.paymentgateway.repository;

import io.labs64.paymentgateway.entity.PaymentTransaction;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, UUID> {
	Optional<PaymentTransaction> findTopByPaymentIdOrderByCreatedAtDesc(UUID paymentId);
}
