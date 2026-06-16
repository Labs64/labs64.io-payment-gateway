package io.labs64.paymentgateway.entity;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import io.labs64.paymentgateway.model.PaymentStatus;
import io.labs64.paymentgateway.model.PaymentType;
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
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.util.CollectionUtils;

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

    @Column(name = "payment_provider_id", nullable = false)
    private UUID paymentProviderId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns({
            @JoinColumn(name = "tenant_id", referencedColumnName = "tenant_id", nullable = false, insertable = false, updatable = false),
            @JoinColumn(name = "payment_provider_id", referencedColumnName = "id", nullable = false, insertable = false, updatable = false)
    })
    @ToString.Exclude
    private PaymentProviderEntity paymentProvider;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaymentStatus status;

    @Column(name = "description")
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "purchase_order", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> purchaseOrder;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "billing_info", columnDefinition = "jsonb")
    private Map<String, Object> billingInfo;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "shipping_info", columnDefinition = "jsonb")
    private Map<String, Object> shippingInfo;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "recurrence", columnDefinition = "jsonb")
    private Map<String, Object> recurrence;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "extra", columnDefinition = "jsonb")
    private Map<String, Object> extra;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public PaymentType getType() {
        Map<String, Object> recurrence = getRecurrence();
        return CollectionUtils.isEmpty(recurrence) ? PaymentType.ONE_TIME : PaymentType.RECURRING;
    }

    public void setPaymentProvider(final PaymentProviderEntity paymentProvider) {
        this.paymentProvider = paymentProvider;
        this.paymentProviderId = paymentProvider != null ? paymentProvider.getId() : null;
    }
}
