package io.labs64.paymentgateway.entity;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * JPA entity representing a payment instance.
 */
@Entity
@Table(name = "payment")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
@ToString
public class PaymentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "payment_method_id", nullable = false)
    private String paymentMethodId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaymentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private PaymentType type;

    @Column(name = "amount", nullable = false)
    private Long amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "purchase_order_ref")
    private String purchaseOrderRef;

    @Column(name = "correlation_id")
    private String correlationId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "billing_info", columnDefinition = "jsonb")
    private Map<String, Object> billingInfo;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "shipping_info", columnDefinition = "jsonb")
    private Map<String, Object> shippingInfo;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "extra", columnDefinition = "jsonb")
    private Map<String, Object> extra;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "next_action", columnDefinition = "jsonb")
    private Map<String, Object> nextAction;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    @PreUpdate
    public void normalize() {
        if (currency != null) {
            currency = currency.trim().toUpperCase();
        }
    }

    /**
     * Payment status enum matching the OpenAPI specification.
     */
    public enum PaymentStatus {
        ACTIVE, INCOMPLETE, PAUSED, CLOSED
    }

    /**
     * Payment type enum matching the OpenAPI specification.
     */
    public enum PaymentType {
        ONE_TIME, RECURRING
    }
}
