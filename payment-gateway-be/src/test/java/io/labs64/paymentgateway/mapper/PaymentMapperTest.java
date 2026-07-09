package io.labs64.paymentgateway.mapper;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.labs64.paymentgateway.entity.PaymentEntity;
import io.labs64.paymentgateway.entity.PaymentProviderEntity;
import io.labs64.paymentgateway.model.BillingInfo;
import io.labs64.paymentgateway.model.CreatePaymentRequest;
import io.labs64.paymentgateway.model.OrderItem;
import io.labs64.paymentgateway.model.Payment;
import io.labs64.paymentgateway.model.PaymentStatus;
import io.labs64.paymentgateway.model.PaymentType;
import io.labs64.paymentgateway.model.PurchaseOrder;
import io.labs64.paymentgateway.model.Recurrence;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentMapperTest {
    private static final UUID PAYMENT_PROVIDER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440010");

    private final PaymentMapper mapper = new PaymentMapperImpl(
            new PaymentJsonMapper(new ObjectMapper().registerModule(new JavaTimeModule())));

    @Test
    void toEntityDoesNotMapProviderOrStatusFromCreateRequest() {
        final CreatePaymentRequest request = createPaymentRequest();

        final PaymentEntity entity = mapper.toEntity(request);

        assertThat(entity.getTenantId()).isNull();
        assertThat(entity.getPaymentProvider()).isNull();
        assertThat(entity.getStatus()).isNull();
        assertThat(entity.getPurchaseOrder())
                .containsEntry("currency", "USD")
                .containsEntry("grossAmount", 3000L);
        assertThat(entity.getBillingInfo()).containsEntry("email", "customer@example.com");
        assertThat(entity.getRecurrence())
                .containsEntry("type", "DURATION")
                .containsEntry("expression", "P1M")
                .containsEntry("timezone", "Europe/Kiev");
        assertThat(entity.getExtra()).containsEntry("source", "test");
    }

    @Test
    void toDtoMapsPaymentProviderToPublicProviderAndDerivesRecurringType() {
        final PaymentEntity entity = paymentEntity();
        entity.setRecurrence(Map.of(
                "type", "DURATION",
                "expression", "P1M",
                "timezone", "Europe/Kiev"));

        final Payment dto = mapper.toDto(entity);

        final PurchaseOrder purchaseOrder = Objects.requireNonNull(dto.getPurchaseOrder());
        final BillingInfo billingInfo = Objects.requireNonNull(dto.getBillingInfo());
        final Recurrence recurrence = Objects.requireNonNull(dto.getRecurrence());

        assertThat(dto.getId()).isEqualTo(entity.getId());
        assertThat(dto.getPaymentProviderId()).isEqualTo(PAYMENT_PROVIDER_ID);
        assertThat(dto.getProvider()).isEqualTo("stripe");
        assertThat(dto.getStatus()).isEqualTo(PaymentStatus.READY);
        assertThat(dto.getType()).isEqualTo(PaymentType.RECURRING);
        assertThat(purchaseOrder.getCurrency()).isEqualTo("USD");
        assertThat(purchaseOrder.getGrossAmount()).isEqualTo(3000L);
        assertThat(billingInfo.getEmail()).isEqualTo("customer@example.com");
        assertThat(recurrence.getType()).isEqualTo(Recurrence.TypeEnum.DURATION);
        assertThat(dto.getExtra()).containsEntry("source", "test");
    }

    @Test
    void toDtoDerivesOneTimeTypeWhenRecurrenceIsMissing() {
        final PaymentEntity entity = paymentEntity();
        entity.setRecurrence(null);

        final Payment dto = mapper.toDto(entity);

        assertThat(dto.getType()).isEqualTo(PaymentType.ONE_TIME);
        assertThat(dto.getRecurrence()).isNull();
    }

    @Test
    void toPageMapsPaymentItems() {
        final PaymentEntity entity = paymentEntity();

        final var response = mapper.toPage(new PageImpl<>(List.of(entity), PageRequest.of(1, 20), 21));

        assertThat(response.getItems())
                .hasSize(1)
                .first()
                .extracting(Payment::getProvider)
                .isEqualTo("stripe");
        assertThat(response.getPage()).isEqualTo(1);
        assertThat(response.getPageSize()).isEqualTo(20);
        assertThat(response.getTotalItems()).isEqualTo(21);
        assertThat(response.getTotalPages()).isEqualTo(2);
        assertThat(response.getHasPrev()).isTrue();
        assertThat(response.getHasNext()).isFalse();
    }

    private static CreatePaymentRequest createPaymentRequest() {
        return new CreatePaymentRequest(
                PAYMENT_PROVIDER_ID,
                purchaseOrder(),
                new BillingInfo("customer@example.com"))
                .recurrence(new Recurrence(Recurrence.TypeEnum.DURATION, "P1M", "Europe/Kiev"))
                .extra(Map.of("source", "test"));
    }

    private static PaymentEntity paymentEntity() {
        return PaymentEntity.builder()
                .id(UUID.randomUUID())
                .tenantId("tenant-a")
                .paymentProviderId(PAYMENT_PROVIDER_ID)
                .paymentProvider(PaymentProviderEntity.builder()
                        .id(PAYMENT_PROVIDER_ID)
                        .provider("stripe")
                        .build())
                .status(PaymentStatus.READY)
                .purchaseOrder(Map.of(
                        "currency", "USD",
                        "items", List.of(Map.of("name", "Widget", "price", 3000L, "quantity", 1)),
                        "grossAmount", 3000L))
                .billingInfo(Map.of("email", "customer@example.com"))
                .extra(Map.of("source", "test"))
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
    }

    private static PurchaseOrder purchaseOrder() {
        return new PurchaseOrder(
                "USD",
                List.of(new OrderItem("Widget", 3000L, 1)),
                3000L);
    }
}
