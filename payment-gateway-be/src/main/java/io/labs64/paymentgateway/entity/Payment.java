package io.labs64.paymentgateway.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "payments")
public class Payment {
	@Id
	private UUID id;

	@Column(name = "tenant_id", nullable = false)
	private String tenantId;

	@Column(name = "payment_method_id", nullable = false)
	private String paymentMethodId;

	@Column(nullable = false)
	private String provider;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private PaymentStatus status;

	@Column(nullable = false)
	private boolean recurring;

	@Column(name = "purchase_order", columnDefinition = "text")
	private String purchaseOrder;

	@Column(name = "billing_info", columnDefinition = "text")
	private String billingInfo;

	@Column(name = "shipping_info", columnDefinition = "text")
	private String shippingInfo;

	@Column(columnDefinition = "text")
	private String extra;

	@Column(name = "psp_data", columnDefinition = "text")
	private String pspData;

	@Column(name = "psp_reference")
	private String pspReference;

	@Column(name = "next_action_details", columnDefinition = "text")
	private String nextActionDetails;

	@Column(name = "next_action_type")
	private String nextActionType;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	private Long version;

	@PrePersist
	void onCreate() {
		if (id == null) {
			id = UUID.randomUUID();
		}
		Instant now = Instant.now();
		createdAt = now;
		updatedAt = now;
	}

	@PreUpdate
	void onUpdate() {
		updatedAt = Instant.now();
	}
}
