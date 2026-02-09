package io.labs64.paymentgateway.repository;

import io.labs64.paymentgateway.entity.Payment;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
	Optional<Payment> findByProviderAndPspReference(String provider, String pspReference);
}
