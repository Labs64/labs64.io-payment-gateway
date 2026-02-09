package io.labs64.paymentgateway.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "idempotency_keys",
		uniqueConstraints = @UniqueConstraint(columnNames = {"paymentId", "idempotencyKey"}))
public class IdempotencyKey {
	@Id
	private UUID id;

	@Column(name = "payment_id", nullable = false)
	private UUID paymentId;

	@Column(name = "tenant_id", nullable = false)
	private String tenantId;

	@Column(name = "idempotency_key", nullable = false)
	private String idempotencyKey;

	@Column(name = "transaction_id", nullable = false)
	private UUID transactionId;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@PrePersist
	void onCreate() {
		if (id == null) {
			id = UUID.randomUUID();
		}
		createdAt = Instant.now();
	}
}
