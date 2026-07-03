package io.labs64.paymentgateway.mapper;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.labs64.paymentgateway.entity.CheckoutSessionEntity;
import io.labs64.paymentgateway.entity.PaymentEntity;
import io.labs64.paymentgateway.entity.PaymentProviderEntity;
import io.labs64.paymentgateway.entity.PaymentTransactionEntity;
import io.labs64.paymentgateway.model.CheckoutSessionConfirmation;
import io.labs64.paymentgateway.model.PaymentStatus;
import io.labs64.paymentgateway.model.PaymentTransactionStatus;
import io.labs64.paymentgateway.model.PaymentType;
import io.labs64.paymentgateway.model.StatusDetails;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CheckoutSessionConfirmationMapperTest {

    private final CheckoutSessionConfirmationMapper mapper = new CheckoutSessionConfirmationMapper();

    @Test
    void toDtoMapsPublicSafeConfirmationFields() {
        final CheckoutSessionEntity entity = checkoutSession();

        final CheckoutSessionConfirmation dto = mapper.toDto(entity);

        assertThat(dto.getSessionId()).isEqualTo(entity.getId());
        assertThat(dto.getPayment().getId()).isEqualTo(entity.getPaymentId());
        assertThat(dto.getPayment().getProvider()).isEqualTo("paypal");
        assertThat(dto.getPayment().getStatus()).isEqualTo(PaymentStatus.CLOSED);
        assertThat(dto.getPayment().getType()).isEqualTo(PaymentType.ONE_TIME);
        assertThat(dto.getPayment().getDescription()).isEqualTo("Order #10001");
        assertThat(dto.getPayment().getAmount()).isEqualTo(3000L);
        assertThat(dto.getPayment().getCurrency()).isEqualTo("USD");
        assertThat(dto.getPaymentTransaction().getId()).isEqualTo(entity.getPaymentTransactionId());
        assertThat(dto.getPaymentTransaction().getStatus()).isEqualTo(PaymentTransactionStatus.SUCCESS);
        assertThat(dto.getPaymentTransaction().getStatusDetails()).isEqualTo(new StatusDetails("SUCCESS", "Captured"));
    }

    private static CheckoutSessionEntity checkoutSession() {
        final PaymentProviderEntity provider = PaymentProviderEntity.builder()
                .id(UUID.randomUUID())
                .provider("paypal")
                .build();
        final PaymentEntity payment = PaymentEntity.builder()
                .id(UUID.randomUUID())
                .status(PaymentStatus.CLOSED)
                .description("Order #10001")
                .purchaseOrder(Map.of(
                        "currency", "USD",
                        "items", List.of(Map.of("name", "Widget", "price", 3000L, "quantity", 1)),
                        "grossAmount", 3000L))
                .createdAt(OffsetDateTime.now())
                .build();
        payment.setPaymentProvider(provider);

        final PaymentTransactionEntity transaction = PaymentTransactionEntity.builder()
                .id(UUID.randomUUID())
                .status(PaymentTransactionStatus.SUCCESS)
                .statusDetails(new StatusDetails("SUCCESS", "Captured"))
                .pspData(Map.of("orderId", "paypal-order"))
                .createdAt(OffsetDateTime.now())
                .build();
        transaction.setPayment(payment);

        final CheckoutSessionEntity session = CheckoutSessionEntity.builder()
                .id(UUID.randomUUID())
                .build();
        session.setPayment(payment);
        session.setPaymentTransaction(transaction);
        return session;
    }
}
